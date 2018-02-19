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
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class RunConfigBuilderTest {

    private static CmdBuilder cmdBuilder = CmdBuilder.getBuilder();
    private static InputStream stream(String input){
        return new ByteArrayInputStream(input.getBytes());
    }
    public static InputStream stream(String...input){
        return new ByteArrayInputStream(
                Arrays.asList(input).stream()
                        .collect(Collectors.joining("\n")).getBytes()
        );
    }

    /**
     * The first yaml to load a script wins. Scripts do not merge
     */
    @Test
    public void testSameScriptName(){
        YamlParser parser = new YamlParser();
        parser.load("firstDef",stream(""+
            "scripts:",
            "  first:",
            "    - sh: echo FOO",
            "hosts:",
            "  local : fakeUser@localhost",
            "roles:",
            "  hosts: [local]",
            "  run-scripts: [first]",
            ""
        ));
        parser.load("secondDef",stream(""+
            "scripts:",
            "  first:",
            "    - sh: echo BAR",
            ""
        ));
        RunConfigBuilder builder = new RunConfigBuilder(cmdBuilder);

        builder.loadYaml(parser);
        RunConfig runConfig = builder.buildConfig();

        assertFalse("runConfig errors:\n"+runConfig.getErrors().stream().collect(Collectors.joining("\n")),runConfig.hasErrors());

        Script script = runConfig.getScript("first");

        assertTrue("the first script definition should be used",script.tree().contains("echo FOO"));
        assertFalse("the second should script not be merged",script.tree().contains("echo BAR"));
    }

    /**
     * A role defined in 2 yaml should merge
     */
    @Test
    public void testMergeRoleAcrossYaml(){
        YamlParser parser = new YamlParser();
        parser.load("scriptDef",stream(""+
            "scripts:",
            "  first:",
            "    - sh: ls",
            "    - sh: pwd",
            "  second:",
            "    - sh: whoami",
            "    - sh: echo ${PWD}",
            "roles:",
            "  role:",
            "    - setup-scripts: [first]",
            "    - run-scripts: [second]"
        ));
        parser.load("hostDef",stream(""+
            "hosts:",
            "  local: fakeUser@localhost",
            "roles:",
            "  role:",
            "    - hosts: [local]"
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
                "hosts:",
                "  foo : user@foo",
                "  bar : user@bar",
                "  biz : user@biz",
                "roles:",
                "  foo:",
                "    hosts: [foo]",
                "    run-scripts: [FooRunScript]",
                "  bar",
                "    hosts: [foo, bar, biz]",
                "    run-scripts: [AllRunScript]",
                "  NotFoo",
                "    hosts: bar !foo",
                "    run-scripts: [NotFooRunScript]",
                ""
        ));
        RunConfigBuilder builder = new RunConfigBuilder(cmdBuilder);

        builder.loadYaml(parser);

        RunConfig runConfig = builder.buildConfig();

        assertFalse("RunConfig should be valid but saw errors:\n"+runConfig.getErrors(),runConfig.hasErrors());

        Host foo = new Host("user","foo");
        Host bar = new Host("user","bar");
        Host biz = new Host("user","biz");

        List<ScriptCmd> fooScripts = runConfig.getRunCmds(foo);
        List<ScriptCmd> barScripts = runConfig.getRunCmds(bar);
        List<ScriptCmd> bizScripts = runConfig.getRunCmds(biz);

        assertTrue("foo host should run FooRunScript and AllRunScript",fooScripts.toString().contains("FooRunScript") && fooScripts.toString().contains("AllRunScript"));
        assertFalse("foo host should not have NotFooRunScript",fooScripts.toString().contains("NotFooRunScript"));

        assertTrue("bar host should run AllRunScript and NotFooRunScript",barScripts.toString().contains("AllRunScript") && barScripts.toString().contains("NotFooRunScript"));
        assertFalse("bar host should not contain FooRunScript",barScripts.toString().contains(" FooRunScript"));//need space otherwise matches NotFooRunScript

    }

    @Test
    public void testOldNestSyntax(){
        YamlParser parser = new YamlParser();
        parser.load("oldSyntax",stream("",
                "name: oldSyntax",
                "scripts:",
                "  firstScript:#comment",
                "   - sh: do smomething",
                "   - - watch:",
                "       - regex: .*",
                "       - - abort: message"
                )
        );
        RunConfigBuilder builder = new RunConfigBuilder(cmdBuilder);
        builder.loadYaml(parser);

        RunConfig runConfig = builder.buildConfig();

        assertFalse("RunConfig should not contain errors but saw\n:"+runConfig.getErrors().stream().collect(Collectors.joining("\n")),runConfig.hasErrors());

        Script firstScript = runConfig.getScript("firstScript");

        assertNotNull("firstScript should exist but was null",firstScript);

        Cmd next = firstScript.getNext();

        assertTrue("sh should have a watcher",next.hasWatchers());

        Cmd watcher = next.getLastWatcher();

        assertNotNull("sh watcher should not be null",watcher);
        assertTrue("watcher should have abort as child",watcher.getNext().toString().contains("abort"));
    }

    @Test
    public void testSyntax(){
        YamlParser parser = new YamlParser();
        parser.load("supportedSyntax",stream(""+
                        "name: syntax",
                        "scripts:",
                        "  firstScript:#this is my first script",
                        "    - sh: inline shell arguments",
                        "    - queue-download:",
                        "      path: ./",
                        "      destination: ./",
                        "    - sh: top",
                        "      - sh: second",
                        "      - sh: third",
                        "    - sh: first second third",
                        "      - watch:",
                        "        - regex: \".*?\"",
                        "          - abort: fail",
                        "      - with:",
                        "          FOO : buz",
                        "          BAR : buz",
                        "      - sh: childCommand",
                        "    - invoke: ${{scriptName}}",
                        "  secondScript:#this is the otherScript",
                        "    - sh: do this please",
                        "    - abort: ha!",
                        "  thirdScript:",
                        "    - sh: rm -rf /tmp/bar",
                        "hosts:",
                        "  laptop: wreicher@laptop",
                        "  server:",
                        "     username: root",
                        "     hostname: serverName",
                        "     port: 22",
                        "---",
                        "roles:",
                        "  foo:",
                        "    hosts:",
                        "     - laptop",
                        "     - server",
                        "    setup-scripts:",
                        "     - firstScript",
                        "        - WITH: {foo:bar,biz:buz}",
                        "     - firstScript",
                        "        - WITH: {foo:yaba,biz:daba}",
                        "    run-scripts",
                        "     - secondScript",
                        "    cleanup-scripts:",
                        "     - ${{cleanupScript}}",
                        "---",
                        "states:",
                        "  RUN:",
                        "    FOO: bar",
                        "    cleanupScript: thirdScript",
                        "  laptop:",
                        "    FOO: biz",
                        ""
                )
        );
        RunConfigBuilder builder = new RunConfigBuilder(cmdBuilder);
        builder.loadYaml(parser);

        RunConfig runConfig = builder.buildConfig();

        Set<Host> cleanupHosts = runConfig.getCleanupHosts();
    }

}
