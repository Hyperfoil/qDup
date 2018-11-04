package perf.qdup.cmd;

import org.junit.Test;
import perf.qdup.Run;
import perf.qdup.SshTestBase;
import perf.qdup.config.CmdBuilder;
import perf.qdup.config.RunConfig;
import perf.qdup.config.RunConfigBuilder;

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

        RunConfigBuilder builder = new RunConfigBuilder(CmdBuilder.getBuilder());

        builder.addScript(runScript);
        builder.addHostAlias("local",getHost().toString());
        builder.addHostToRole("role","local");
        builder.addRoleRun("role","run-exit",new HashMap<>());

        RunConfig config = builder.buildConfig();
        assertFalse("unexpected errors:\n"+config.getErrors().stream().collect(Collectors.joining("\n")),config.hasErrors());

        Dispatcher dispatcher = new Dispatcher();
        Run run = new Run("/tmp",config,dispatcher);
        run.run();

        assertTrue("imlicit called",implicitCalled.get());
        assertTrue("explicit called",explicitCalled.get());
    }
}
