package perf.ssh;

import com.sun.org.apache.regexp.internal.RE;
import org.junit.Rule;
import org.junit.Test;
import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.CommandDispatcher;
import perf.ssh.cmd.Result;
import perf.ssh.cmd.Script;
import perf.ssh.config.CmdBuilder;
import perf.ssh.config.RunConfig;
import perf.ssh.config.RunConfigBuilder;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RunTest extends SshTestBase{

//    @Rule
//    public final TestServer testServer = new TestServer();


    @Test
    public void testTimer(){
        final StringBuilder first = new StringBuilder();
        final StringBuilder second = new StringBuilder();
        RunConfigBuilder builder = new RunConfigBuilder(CmdBuilder.getBuilder());
        Script script = new Script("run-timer");
        script.then(
                Cmd.sleep(4_000).addTimer(2_000,Cmd.code(((input, state) -> {
                    first.append(input);
                    return Result.next(input);
                }))).addTimer(10_000,Cmd.code(((input, state) -> {
                    second.append(input);
                    return Result.next(input);
                })))
        );

        builder.addScript(script);
        builder.addHostAlias("local",getHost().toString());//+testServer.getPort());
        builder.addHostToRole("role","local");
        builder.addRoleRun("role","run-timer",new HashMap<>());

        RunConfig config = builder.buildConfig();

        CommandDispatcher dispatcher = new CommandDispatcher();
        Run run = new Run("/tmp",config,dispatcher);

        run.run();

        String firstString = first.toString();
        String secondString = second.toString();
        assertEquals("first should container the 10000 timeout value","2000",firstString);
        assertEquals("second should not run because the parent command finished","",secondString);
    }

    @Test
    public void testEnvCapture(){

        final StringBuilder runEnvBuffer = new StringBuilder();

        RunConfigBuilder builder = new RunConfigBuilder(CmdBuilder.getBuilder());

        Script setupScript = new Script("setup-env");
        setupScript.then(Cmd.sh("env",true));
        setupScript.then(Cmd.sh("export FOO=\"FOO\""));
        setupScript.then(Cmd.sh("unset PROMPT_COMMAND"));
        setupScript.then(Cmd.sh("export VERTX_HOME=\"/tmp\"")
        );
        Script runScript = new Script("run-env").then(Cmd.log("post-run-env-script"));
        runScript.then(Cmd.sh("env",true).then(Cmd.code((input,state)->{
            runEnvBuffer.append(input);
            return Result.next(input);
        })));
        builder.addScript(setupScript);
        builder.addScript(runScript);

        builder.addHostAlias("local",getHost().toString());//+testServer.getPort());
        builder.addHostToRole("role","local");
        builder.addRoleRun("role","run-env",new HashMap<>());
        builder.addRoleSetup("role","setup-env",new HashMap<>());

        RunConfig config = builder.buildConfig();

        CommandDispatcher dispatcher = new CommandDispatcher();
        Run run = new Run("/tmp",config,dispatcher);

        run.run();

        String runEnv = runEnvBuffer.toString();
        assertTrue("run-env output should contain FOO=FOO but was\n"+runEnv,runEnv.contains("FOO=FOO"));
        assertTrue("run-env output should contain VERTX_HOME=/tmp",runEnv.contains("VERTX_HOME=/tmp"));
    }
}
