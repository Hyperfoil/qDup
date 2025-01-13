package io.hyperfoil.tools.qdup;

import org.junit.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class QDupTest extends SshContainerTestBase {

    private class Output {
        private String exit;
        private String output;

        public Output(String output, String exit){
            this.output = output;
            this.exit = exit;
        }
        public String getOutput(){return output;}
        public String getExit(){return exit;}
    }

    public static String makeFile(String...lines){
        String rtrn = "";
        try {
            File f = File.createTempFile("qdup-test","yaml");
            rtrn = f.getPath();
            f.deleteOnExit();
            if(lines!=null){
                BufferedWriter writer = new BufferedWriter(new FileWriter(f));
                Arrays.stream(lines).forEach(line->{
                    try {
                        writer.write(line);
                        writer.newLine();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                writer.flush();
                writer.close();
            }
        } catch (IOException e) {
            //e.printStackTrace();
            rtrn = "";
        }
        return rtrn;
    }
    private Output runMain(String...args){
        String result = "0";
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final String utf8 = StandardCharsets.UTF_8.name();
        boolean ok = true;
        try (PrintStream tempPrintStream = new PrintStream(baos, true, utf8)) {
//            System.setOut(tempPrintStream);
//            System.setErr(tempPrintStream);
            System.setProperty("disableRestApi", "true");
            QDup qdup = new QDup(args);
            ok = qdup.run();
            //QDup.main(args);
        } catch (SecurityException e) {
            result = e.getMessage();
        } catch (UnsupportedEncodingException e) {
            result = e.getMessage();
        } finally {
            System.setErr(originalErr);
            System.setOut(originalOut);
        }
        return new Output(baos.toString(),ok ? "0" : "1");
    }

    @Test
    public void main_exit_sh() {
        Output output = runMain(
                "-i",
                getIdentity(),
                makeFile(
                    "hosts:",
                    "  local: "+getHost(),
                    "scripts:",
                    "  doit:",
                    "  - sh: whoami; (exit 42);",
                    "  - set-state: RUN.foo true",
                    "",
                    "roles:",
                    "  run:",
                    "    hosts: [local]",
                    "    run-scripts:",
                    "    - doit"
                )
        );
        assertNotNull(output);
        assertEquals("Qdup.main should exit with 1","1",output.getExit());
    }

    @Test
    public void yaml_with_else(){
        Output output = runMain(
                "-Y",
                makeFile(
                        "hosts:",
                        "  local: "+getHost(),
                        "scripts:",
                        "  doit:",
                        "  - sh: cat log",
                        "  - regex: foo",
                        "    then:",
                        "    - sh: echo 'found'",
                        "    else:",
                        "    - sh: echo 'lost'",
                        "",
                        "roles:",
                        "  run:",
                        "    hosts: [local]",
                        "    run-scripts:",
                        "    - doit"
                )
        );
        assertNotNull(output);
    }

    @Test
    public void main_exit_invalid_args(){
        Output output = runMain("");
        assertNotNull(output);
        assertEquals("incorrect exit code for invalid args to QDup.main"+output.getOutput(),"1",output.getExit());
    }
    @Test
    public void main_exit_bad_yaml(){
        Output output = runMain(
                "-i",
                getIdentity(),
                makeFile(
                    "hosts:",
                    "  local: "+getHost(),
                    "scripts:",
                    "  doit:",
                    "  - sh: whoami",
                    "    - set-state: RUN.foo true",
                    "",
                    "roles:",
                    "  run:",
                    "    hosts: [local]",
                    "    run-scripts:",
                    "    - doit"
                )
        );
        assertNotNull(output);
        assertEquals("incorrect exit code for invalid args to QDup.main"+output.getOutput(),"1",output.getExit());
    }
    @Test
    public void main_exit_sh_ignore() {
        Output output = runMain(
                "-ix",
                "-i",
                getIdentity(),
                makeFile(
                    "hosts:",
                    "  local: "+getHost(),
                    "scripts:",
                    "  doit:",
                    "  - sh: whoami; (exit 42);",
                    "  - set-state: RUN.foo true",
                    "",
                    "roles:",
                    "  run:",
                    "    hosts: [local]",
                    "    run-scripts:",
                    "    - doit"
                )
        );
        assertNotNull(output);
        assertEquals("Qdup.main should exit with 0","0",output.getExit());
    }
    @Test
    public void exit_code_checking_by_default(){
        QDup qdup = new QDup("-T","fake.yaml");
        assertTrue("exit checking by default",qdup.checkExitCode());
    }
    @Test
    public void disable_exit_checking(){
        QDup qdup = new QDup("-ix","-T","fake.yaml");
        assertFalse("exit checking by default",qdup.checkExitCode());
    }
    @Test
    public void skip_stages_valid_lowercase(){
        QDup qdup = new QDup("--skip-stages","setup,cleanup","-T","fake.yaml");
        assertTrue("expect targetStages",qdup.hasSkipStages());
        assertEquals("expect 2 targetStages: "+qdup.getSkipStages(),2,qdup.getSkipStages().size());
        List<Stage> targetStages = qdup.getSkipStages();
        assertTrue("should contain setup "+targetStages,targetStages.contains(Stage.Setup));
        assertTrue("should contain cleanup "+targetStages,targetStages.contains(Stage.Cleanup));
    }
    @Test
    public void skip_stages_valid_mixed_case(){
        QDup qdup = new QDup("--skip-stages","Setup,Cleanup","-T","fake.yaml");
        assertTrue("expect targetStages",qdup.hasSkipStages());
        assertEquals("expect 2 targetStages: "+qdup.getSkipStages(),2,qdup.getSkipStages().size());
        List<Stage> targetStages = qdup.getSkipStages();
        assertTrue("should contain setup "+targetStages,targetStages.contains(Stage.Setup));
        assertTrue("should contain cleanup "+targetStages,targetStages.contains(Stage.Cleanup));
    }
}
