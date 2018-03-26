package perf.qdup;

import org.junit.Assert;
import org.junit.Test;
import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Script;
import perf.qdup.config.CmdBuilder;
import perf.qdup.config.RunConfig;
import perf.qdup.config.RunConfigBuilder;
import perf.qdup.config.YamlParser;

import java.util.Collections;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StageTest extends SshTestBase{

    private static CmdBuilder cmdBuilder = CmdBuilder.getBuilder();




    @Test
    public void variableSignalName_boundInRole(){
        YamlParser parser = new YamlParser();
        parser.load("signal",stream(""+
            "scripts:",
            "  foo:",
            "    - signal: ${{FOO}}",
            "  bar:",
            "    - wait-for: ${{BAR}}",
            "hosts:",
            "  local: me@localhost",
            "roles:",
            "  role:",
            "    hosts: [local]",
            "    run-scripts:",
            "    - foo:",
            "        with: { FOO: foo }",
            "    - bar:",
            "        with: { BAR: foo }"
        ));

        RunConfigBuilder builder = new RunConfigBuilder(CmdBuilder.getBuilder());

        builder.loadYaml(parser);
        RunConfig config = builder.buildConfig();

        assertFalse("unexpected errors:\n"+config.getErrors().stream().collect(Collectors.joining("\n")),config.hasErrors());
        assertEquals("one signal for foo",1,config.getRunStage().getSignalCount("foo"));
        assertTrue("one waiter for foo",config.getRunStage().getWaiters().contains("foo"));

    }

    @Test
    public void signalOneScriptOneHost(){
        RunConfigBuilder builder = new RunConfigBuilder(cmdBuilder);

        Script signal = new Script("signal");
        signal.then(Cmd.signal("FOO"));

        builder.addScript(signal);

        builder.addHostAlias("local","guest@localhost");
        builder.addHostToRole("role","local");
        builder.addRoleRun("role","signal", Collections.emptyMap());

        RunConfig runConfig = builder.buildConfig();

        Assert.assertEquals("expect 1 signal for FOO",1,runConfig.getRunStage().getSignalCount("FOO"));

    }
    @Test
    public void signalOneScriptTwoHosts(){

        RunConfigBuilder builder = new RunConfigBuilder(cmdBuilder);

        Script signal = new Script("signal");
        signal.then(Cmd.signal("FOO"));

        builder.addScript(signal);

        builder.addHostAlias("alpha","guest@alpha");
        builder.addHostAlias("bravo","guest@bravo");
        builder.addHostToRole("role","alpha");
        builder.addHostToRole("role","bravo");
        builder.addRoleRun("role","signal", Collections.emptyMap());


        RunConfig runConfig = builder.buildConfig();

        Assert.assertEquals("expect 2 signals for FOO",2,runConfig.getRunStage().getSignalCount("FOO"));

    }
    @Test
    public void signalTwoScriptsOneHost(){
        RunConfigBuilder builder = new RunConfigBuilder(cmdBuilder);


        Script signal = new Script("signal");
        signal.then(Cmd.signal("FOO"));
        Script second = new Script("second");
        second.then(Cmd.signal("FOO"));

        builder.addScript(signal);
        builder.addScript(second);

        builder.addHostAlias("alpha","guest@alpha");
        builder.addHostToRole("role","alpha");

        builder.addRoleRun("role","signal", Collections.emptyMap());
        builder.addRoleRun("role","second", Collections.emptyMap());


        RunConfig runConfig = builder.buildConfig();


        Assert.assertEquals("expect 2 signals for FOO",2,runConfig.getRunStage().getSignalCount("FOO"));

    }
    @Test
    public void signalTwoScriptsTwoHosts(){

        RunConfigBuilder builder = new RunConfigBuilder(cmdBuilder);

        Script signal = new Script("signal");
        signal.then(Cmd.signal("FOO"));
        Script second = new Script("second");
        second.then(Cmd.signal("FOO"));

        builder.addScript(signal);
        builder.addScript(second);

        builder.addHostAlias("alpha","guest@alpha");
        builder.addHostAlias("bravo","guest@bravo");

        builder.addHostToRole("role","alpha");
        builder.addHostToRole("role","bravo");

        builder.addRoleRun("role","signal", Collections.emptyMap());
        builder.addRoleRun("role","second", Collections.emptyMap());


        RunConfig runConfig = builder.buildConfig();

        Assert.assertEquals("expect 4 signals for FOO",4,runConfig.getRunStage().getSignalCount("FOO"));

    }
    @Test
    public void signalInSubScript(){
        RunConfigBuilder builder = new RunConfigBuilder(cmdBuilder);

        Script signal = new Script("signal");
        signal.then(Cmd.signal("FOO"));
        Script second = new Script("second");
        second.then(Cmd.script("signal"));

        builder.addScript(signal);
        builder.addScript(second);

        builder.addHostAlias("alpha","guest@alpha");
        builder.addHostAlias("bravo","guest@bravo");

        builder.addHostToRole("role","alpha");
        builder.addHostToRole("role","bravo");

        builder.addRoleRun("role","signal", Collections.emptyMap());
        builder.addRoleRun("role","second", Collections.emptyMap());


        RunConfig runConfig = builder.buildConfig();
        Assert.assertEquals("expect 4 signals for FOO",4,runConfig.getRunStage().getSignalCount("FOO"));

    }

    @Test
    public void variableSignal(){
        RunConfigBuilder builder = new RunConfigBuilder(cmdBuilder);

        Script signal = new Script("signal");
        signal.then(Cmd.signal("${{FOO}}"));

        builder.addScript(signal);

        builder.addHostAlias("alpha","guest@alpha");

        builder.addHostToRole("role","alpha");

        builder.addRoleRun("role","signal", Collections.emptyMap());

        RunConfig runConfig = builder.buildConfig();
        assertTrue("should not be valid to signal an undefined state variable",runConfig.hasErrors());


        builder.setRunState("FOO","foo");
        runConfig = builder.buildConfig();
        assertFalse("adding state FOO = foo should make the config valid",runConfig.hasErrors());
        assertFalse("run should signal foo (state value of FOO)",runConfig.getRunStage().getSignals().contains("foo"));
    }

}
