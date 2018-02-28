package perf.qdup;

import org.junit.Assert;
import org.junit.Test;
import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Script;
import perf.qdup.config.CmdBuilder;
import perf.qdup.config.RunConfigBuilder;

import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RunValidationTest {

    private static CmdBuilder cmdBuilder = CmdBuilder.getBuilder();

    @Test
    public void signalOneScriptOneHost(){
        RunConfigBuilder builder = new RunConfigBuilder(cmdBuilder);

        Script signal = new Script("signal");
        signal.then(Cmd.signal("FOO"));

        builder.addScript(signal);

        builder.addHostAlias("local","guest@localhost");
        builder.addHostToRole("role","local");
        builder.addRoleRun("role","signal", Collections.emptyMap());

        RunValidation validation = builder.runValidation();

        Assert.assertEquals("expect 1 signal for FOO",1,validation.getRunStage().getSignalCount("FOO"));

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


        RunValidation validation = builder.runValidation();

        Assert.assertEquals("expect 2 signals for FOO",2,validation.getRunStage().getSignalCount("FOO"));

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


        RunValidation validation = builder.runValidation();


        Assert.assertEquals("expect 2 signals for FOO",2,validation.getRunStage().getSignalCount("FOO"));

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


        RunValidation validation = builder.runValidation();

        Assert.assertEquals("expect 4 signals for FOO",4,validation.getRunStage().getSignalCount("FOO"));

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


        RunValidation validation = builder.runValidation();

        Assert.assertEquals("expect 4 signals for FOO",4,validation.getRunStage().getSignalCount("FOO"));

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

        RunValidation validation = builder.runValidation();
        assertFalse("should not be valid to signal an undefined state variable",validation.isValid());


        builder.setRunState("FOO","foo");
        validation = builder.runValidation();
        assertTrue("adding state FOO = foo should make the config valid",validation.isValid());
        assertTrue("run should signal foo (state value of FOO)",validation.getRunStage().getSignals().contains("foo"));
    }

}
