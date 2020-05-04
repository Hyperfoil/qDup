package io.hyperfoil.tools.qdup.cmd;

import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.config.CmdBuilder;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import org.junit.Test;
import io.hyperfoil.tools.qdup.SshTestBase;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExitCodeTest extends SshTestBase {


    @Test
    public void two_same_code(){
        AtomicBoolean implicitCalled = new AtomicBoolean(false);
        AtomicBoolean explicitCalled = new AtomicBoolean(false);
        Script runScript = new Script("run-exit");
        runScript.then(Cmd.sh("pwd")
        .then(
            Cmd.exitCode()
            .then(Cmd.code((input,state)->{
                implicitCalled.set(true);
                return Result.next(input);
            }))
        )
        .then(
            Cmd.exitCode("0")
            .then(Cmd.code((input,state)->{
                explicitCalled.set(true);
                return Result.next(input);
            }))
        ));

        RunConfigBuilder builder = getBuilder();

        builder.addScript(runScript);
        builder.addHostAlias("local",getHost().toString());
        builder.addHostToRole("role","local");
        builder.addRoleRun("role","run-exit",new HashMap<>());

        RunConfig config = builder.buildConfig();
        assertFalse("unexpected errors:\n"+config.getErrors().stream().collect(Collectors.joining("\n")),config.hasErrors());

        Dispatcher dispatcher = new Dispatcher();
        Run run = new Run(tmpDir.toString(),config,dispatcher);
        run.run();

        assertTrue("imlicit called",implicitCalled.get());
        assertTrue("explicit called",explicitCalled.get());
    }
}
