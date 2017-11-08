package perf.ssh;

import org.junit.Assert;
import org.junit.Test;
import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.Script;

public class RunValidationTest {


    @Test
    public void signalOneScriptOneHost(){
        RunConfig config = new RunConfig();
        Script signal = config.getScript("signal");
        signal.then(Cmd.signal("FOO"));

        config.addHost("local",new Host("guest","localhost"));
        config.getRole("role").add("local").addRunScript("signal");

        RunValidation validation = config.validate();

        Assert.assertEquals("expect 1 signal for FOO",1,validation.getRunValidation().getSignalCount("FOO"));

    }
    @Test
    public void signalOneScriptTwoHosts(){
        RunConfig config = new RunConfig();
        Script signal = config.getScript("signal");
        signal.then(Cmd.signal("FOO"));

        config.addHost("alpha",new Host("guest","alpha"));
        config.addHost("bravo",new Host("guest","bravo"));
        config.getRole("role").add("alpha","bravo").addRunScript("signal");

        RunValidation validation = config.validate();

        Assert.assertEquals("expect 2 signals for FOO",2,validation.getRunValidation().getSignalCount("FOO"));

    }
    @Test
    public void signalTwoScriptsOneHost(){
        RunConfig config = new RunConfig();
        Script signal = config.getScript("signal");
        signal.then(Cmd.signal("FOO"));
        Script second = config.getScript("second");
        second.then(Cmd.signal("FOO"));

        config.addHost("alpha",new Host("guest","alpha"));

        config.getRole("role").add("alpha").addRunScript("signal");
        config.getRole("role").addRunScript("second");

        RunValidation validation = config.validate();

        Assert.assertEquals("expect 2 signals for FOO",2,validation.getRunValidation().getSignalCount("FOO"));

    }
    @Test
    public void signalTwoScriptsTwoHosts(){
        RunConfig config = new RunConfig();
        Script signal = config.getScript("signal");
        signal.then(Cmd.signal("FOO"));
        Script second = config.getScript("second");
        second.then(Cmd.signal("FOO"));

        config.addHost("alpha",new Host("guest","alpha"));
        config.addHost("bravo",new Host("guest","bravo"));

        config.getRole("role").add("alpha","bravo").addRunScript("signal");
        config.getRole("role").addRunScript("second");

        RunValidation validation = config.validate();

        Assert.assertEquals("expect 4 signals for FOO",4,validation.getRunValidation().getSignalCount("FOO"));

    }
    @Test
    public void signalInSubScript(){
        RunConfig config = new RunConfig();
        Script signal = config.getScript("signal");
        signal.then(Cmd.signal("FOO"));
        Script second = config.getScript("second");
        second.then(Cmd.script("signal"));

        config.addHost("alpha",new Host("guest","alpha"));
        config.addHost("bravo",new Host("guest","bravo"));

        config.getRole("role").add("alpha","bravo").addRunScript("signal");
        config.getRole("role").addRunScript("second");

        RunValidation validation = config.validate();

        Assert.assertEquals("expect 4 signals for FOO",4,validation.getRunValidation().getSignalCount("FOO"));

    }

    @Test
    public void variableSignal(){
        RunConfig config = new RunConfig();
        Script signal = config.getScript("signal");
        signal.then(Cmd.signal("${{FOO}}"));

        config.addHost("local",new Host("guest","localhost"));
        config.getRole("role").add("local").addRunScript("signal");

        RunValidation validation = config.validate();

        System.out.println(validation.getRunValidation().getSignals());

        //Assert.assertEquals("expect 1 signal for FOO",1,validation.getRunValidation().getSignalCount("FOO"));

    }

}
