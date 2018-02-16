package perf.ssh.config;

import org.junit.Ignore;
import org.junit.Test;
import perf.ssh.Host;
import perf.ssh.RunValidation;
import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.Script;
import perf.ssh.cmd.impl.ScriptCmd;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RunConfigBuilderTest {

    private static CmdBuilder cmdBuilder = CmdBuilder.getBuilder();
    private static InputStream stream(String input){
        return new ByteArrayInputStream(input.getBytes());
    }


    @Test
    public void testSameScriptName(){
        YamlParser parser = new YamlParser();
        parser.load("firstDef",stream(""+
            "scripts:\n"+
            "  first:\n"+
            "    - sh: echo FOO\n"+
            "hosts:\n" +
            "  local : fakeUser@localhost\n"+
            "roles:\n"+
            "  hosts: [local]\n"+
            "  run-scripts: [first]\n"+
            ""
        ));
        parser.load("secondDef",stream(""+
            "scripts:\n"+
            "  first:\n"+
            "    - sh: echo BAR\n"+
            ""
        ));
        RunConfigBuilder builder = new RunConfigBuilder(cmdBuilder);

        builder.loadYaml(parser);
        RunConfig runConfig = builder.buildConfig();

        assertFalse("runConfig errors:\n"+runConfig.getErrors(),runConfig.hasErrors());

        Script script = runConfig.getScript("first");

        assertTrue("the first script definition should be used",script.tree().contains("echo FOO"));




    }


    @Test
    public void testMergeRoleAcrossYaml(){
        YamlParser parser = new YamlParser();
        parser.load("scriptDef",stream(""+
            "scripts:\n"+
            "  first:\n"+
            "    - sh: ls\n"+
            "    - sh: pwd\n"+
            "  second:\n"+
            "    - sh: whoami\n"+
            "    - sh: echo ${PWD}\n"+
            "roles:\n" +
            "  role:\n"+
            "    - setup-scripts: [first]\n"+
            "    - run-scripts: [second]\n"
        ));
        parser.load("hostDef",stream(""+
            "hosts:\n"+
            "  local: fakeUser@localhost\n"+
            "roles:\n"+
            "  role:\n"+
            "    - hosts: [local]\n"
        ));
        RunConfigBuilder builder = new RunConfigBuilder(cmdBuilder);

        builder.loadYaml(parser);

        RunConfig runConfig = builder.buildConfig();

        Set<Host> runHosts = runConfig.getRunHosts();
        Set<Host> setupHosts = runConfig.getSetupHosts();

        assertEquals("role should contain one run host",1,runHosts.size());
        assertEquals("role should contain one setup host",1,setupHosts.size());

        Host host = runHosts.iterator().next();

        Cmd setupCmd = runConfig.getSetupCmd(host);
        List<ScriptCmd> runCmds = runConfig.getRunCmds(host);

        assertTrue("setup should contain first script",setupCmd.getNext().toString().contains("first"));
        assertEquals("role should contain one role script",1,runCmds.size());

        assertTrue("role should have second as a run script",runCmds.get(0).toString().contains("second"));
    }

    @Test @Ignore
    public void testRoleExpession(){
        YamlParser parser = new YamlParser();
        parser.load("roleExpression",stream(""+
                "hosts:\n"+
                "  foo : user@foo\n"+
                "  bar : user@bar\n"+
                "  biz : user@biz\n"+
                "roles:\n"+
                "  foo:\n"+
                "    hosts: [foo]\n"+
                "  all\n"+
                "    hosts: [foo, bar, biz]\n"+
                "  NotFoo\n"+
                "    hosts: all !foo\n"+
                ""
        ));
        RunConfigBuilder builder = new RunConfigBuilder(cmdBuilder);

        builder.loadYaml(parser);

        builder.buildConfig();

    }

    @Test @Ignore
    public void testSyntax(){
        YamlParser parser = new YamlParser();
        parser.load("supportedSyntax",stream(""+
                        "name: syntax\n"+
                        "scripts:\n"+
                        "  firstScript:#this is my first script\n"+
                        "    - sh: inline shell arguments\n"+
                        "    - queue-download:\n"+
                        "      path: ./\n"+
                        "      destination: ./\n"+
                        "    - sh: top\n"+
                        "      - sh: second\n"+
                        "      - sh: third\n"+
                        "    - sh: first second third\n"+
                        "      - watch:\n"+
                        "        - regex: \".*?\"\n"+
                        "          - abort: fail\n"+
                        "      - with:\n"+
                        "          FOO : buz\n"+
                        "      - sh: childCommand\n"+
                        "    - invoke: ${{scriptName}}\n"+
                        "  secondScript:#this is the otherScript\n"+
                        "    - sh: do this please\n"+
                        "    - abort: ha!\n"+
                        "hosts:\n"+
                        "  laptop: wreicher@laptop\n"+
                        "  server:\n"+
                        "     username: root\n"+
                        "     hostname: serverName\n"+
                        "     port: 22\n"+
                        "---\n"+
                        "roles:\n"+
                        "  foo:\n"+
                        "    hosts:\n"+
                        "     - laptop\n"+
                        "    setup-scripts:\n"+
                        "     - firstScript\n"+
                        "        - WITH: {foo:bar,biz:buz}\n"+
                        "     - firstScript\n"+
                        "        - WITH: {foo:yaba,biz:daba}\n"+
                        "    run-scripts\n"+
                        "     - secondScript\n"+
                        "    cleanup-scripts:\n"+
                        "     - ${{cleanupScript}}\n"+
                        "---\n"+
                        "states:\n"+
                        "  RUN:\n"+
                        "    FOO: bar\n"+
                        "  laptop:\n"+
                        "    FOO: biz\n"+
                        ""
                )
        );

        RunConfigBuilder builder = new RunConfigBuilder(cmdBuilder);
        builder.loadYaml(parser);
    }

}
