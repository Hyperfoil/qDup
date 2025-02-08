package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Script;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class StageTest extends SshTestBase{

    @Test
    public void isBefore_sameStage(){
        assertFalse("Cleanup should not be before Cleanup",Stage.Cleanup.isBefore(Stage.Cleanup));
    }

    @Test
    public void variableSignalName_boundInState(){
        RunConfigBuilder builder = new RunConfigBuilder();
        Script signal = new Script("signal");
        signal.then(Cmd.signal("${{FOO}}"));
        builder.addScript(signal);
        builder.addHostAlias("alpha","guest@alpha");
        builder.addHostToRole("role","alpha");
        builder.addRoleRun("role","signal", Collections.emptyMap());
        RunConfig runConfig;
//        assertTrue("should not be valid to signal an undefined state variable",runConfig.hasErrors());

        builder.setRunState("FOO","foo");
        runConfig = builder.buildConfig(Parser.getInstance());

        assertFalse("adding state FOO = foo should make the config valid:\n"+runConfig.getErrorStrings().stream().collect(Collectors.joining("\n")),runConfig.hasErrors());
        assertTrue("run should signal foo (state value of FOO)", runConfig.getSignalCounts().contains("foo"));
    }

    @Test
    public void variableSignalName_boundInRole(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
           """
           scripts:
             foo:
               - signal: ${{FOO:}}
             bar:
               - wait-for: ${{BAR}}
           hosts:
             local: me@localhost
           roles:
             role:
               hosts: [local]
               run-scripts:
               - foo:
                   with: { FOO: foo }
               - bar:
                   with: { BAR: foo }
           """
        ));
        RunConfig config = builder.buildConfig(parser);

        assertFalse("unexpected errors:\n"+config.getErrorStrings().stream().collect(Collectors.joining("\n")),config.hasErrors());
        assertTrue("should signal foo",config.getSignalCounts().contains("foo"));
        assertEquals("one signal for foo",1,config.getSignalCounts().count("foo"));
    }

    @Test
    public void signalOneScriptOneHost(){
        RunConfigBuilder builder = new RunConfigBuilder();
        Script signal = new Script("signal");
        signal.then(Cmd.signal("FOO"));
        builder.addScript(signal);
        builder.addHostAlias("local","guest@localhost");
        builder.addHostToRole("role","local");
        builder.addRoleRun("role","signal", Collections.emptyMap());

        RunConfig runConfig = builder.buildConfig(Parser.getInstance());

        Assert.assertEquals("expect 1 signal for FOO",1,runConfig.getSignalCounts().count("FOO"));

    }
    @Test
    public void signalOneScriptTwoHosts(){

        RunConfigBuilder builder = new RunConfigBuilder();

        Script signal = new Script("signal");
        signal.then(Cmd.signal("FOO"));

        builder.addScript(signal);

        builder.addHostAlias("alpha","guest@alpha");
        builder.addHostAlias("bravo","guest@bravo");
        builder.addHostToRole("role","alpha");
        builder.addHostToRole("role","bravo");
        builder.addRoleRun("role","signal", Collections.emptyMap());


        RunConfig runConfig = builder.buildConfig(Parser.getInstance());

        Assert.assertEquals("expect 2 signals for FOO",2,runConfig.getSignalCounts().count("FOO"));

    }
    @Test
    public void signalTwoScriptsOneHost(){
        RunConfigBuilder builder = new RunConfigBuilder();


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


        RunConfig runConfig = builder.buildConfig(Parser.getInstance());


        Assert.assertEquals("expect 2 signals for FOO",2,runConfig.getSignalCounts().count("FOO"));

    }
    @Test
    public void signalTwoScriptsTwoHosts(){

        RunConfigBuilder builder = new RunConfigBuilder();

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


        RunConfig runConfig = builder.buildConfig(Parser.getInstance());

        Assert.assertEquals("expect 4 signals for FOO",4,runConfig.getSignalCounts().count("FOO"));

    }
    @Test
    public void signalMultipleTimesSameScript(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
           """
           scripts:
             sig:
               - signal: FOO
               - signal: FOO
               - signal: FOO
               - signal: FOO
           hosts:
             local: me@localhost
           roles:
             role:
               hosts: [local]
               run-scripts:
               - sig:
           """
        ));
        RunConfig config = builder.buildConfig(parser);
        Assert.assertEquals("expect 4 signals for FOO",4,config.getSignalCounts().count("FOO"));
    }
    @Test
    public void signalInRepeatedSubScript(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
           """
           scripts:
             sig:
               - signal: FOO
             inv:
               - script: sig
                 with: {BAR: alpha}
               - script: sig
                 with: {BAR: bravo}
               - script: sig
                 with: {BAR: charlie}
               - script: sig
                 with: {BAR: delta}
             wat:
               - wait-for: FOO
               - done:
           hosts:
             local: me@localhost
           roles:
             role:
               hosts: [local]
               run-scripts:
               - inv:
               - wat
           """
        ));
        RunConfig config = builder.buildConfig(parser);
        Assert.assertEquals("expect 4 signals for FOO",4,config.getSignalCounts().count("FOO"));
    }


    @Test
    public void signalInSubScript(){
        RunConfigBuilder builder = new RunConfigBuilder();

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


        RunConfig runConfig = builder.buildConfig(Parser.getInstance());
        Assert.assertEquals("expect 4 signals for FOO",4,runConfig.getSignalCounts().count("FOO"));

    }


}
