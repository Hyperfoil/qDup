package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.SshTestBase;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.cmd.SpyContext;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class SetSignalTest extends SshTestBase {


    @Test
    public void run_set_new_signal(){
        SetSignal setSignal = new SetSignal("FOO","2");
        SpyContext context = new SpyContext();
        setSignal.run("",context);

        assertTrue("FOO signal should be created",context.getCoordinator().hasSignal("FOO"));
        assertEquals("FOO should be set to 2",2,context.getCoordinator().getSignalCount("FOO"));
    }

    @Test
    public void set_existing_signal_at_zero(){
        SetSignal setSignal = new SetSignal("FOO","2");
        SpyContext context = new SpyContext();
        context.getCoordinator().setSignal("FOO",0);
        setSignal.run("",context);

        assertTrue("FOO signal should be created",context.getCoordinator().hasSignal("FOO"));
        assertEquals("FOO should be set to 2",2,context.getCoordinator().getSignalCount("FOO"));
    }

    @Test
    public void set_existing_signal_not_zero(){
        SetSignal setSignal = new SetSignal("FOO","2");
        SpyContext context = new SpyContext();
        context.getCoordinator().setSignal("FOO",4);
        setSignal.run("",context);

        assertTrue("FOO signal should be created",context.getCoordinator().hasSignal("FOO"));
        assertEquals("FOO should be set to 2",2,context.getCoordinator().getSignalCount("FOO"));
    }

    @Test
    public void set_existing_signal_not_reset_not_zero(){
        SetSignal setSignal = new SetSignal("FOO","2",false);
        SpyContext context = new SpyContext();
        context.getCoordinator().setSignal("FOO",4);
        setSignal.run("",context);

        assertTrue("FOO signal should be created",context.getCoordinator().hasSignal("FOO"));
        assertEquals("FOO should remain 4",4,context.getCoordinator().getSignalCount("FOO"));
    }

    @Test(timeout = 90_000)
    public void run_set_signal_zero_in_waitfor_timer(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("",
                """
                scripts:
                  foo:
                  - set-signal: FOO 1
                  - wait-for: FOO
                    timer:
                      10s:
                      - set-signal: FOO 0 #should trigger the wait-for to end
                  - set-state: RUN.worked true
                hosts:
                  local: TARGET_HOST
                roles:
                  doit:
                    hosts: [local]
                    run-scripts: [foo]
                """.replaceAll("TARGET_HOST",getHost().toString())
        ));

        RunConfig config = builder.buildConfig(parser);
        assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();
        dispatcher.shutdown();
        State state = config.getState();

        assertTrue("state should have worked",state.has("worked"));
        assertEquals("timer should be ant","true",state.getString("worked"));
    }
}
