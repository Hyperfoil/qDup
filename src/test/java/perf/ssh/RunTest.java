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

import static org.junit.Assert.assertTrue;

public class RunTest {

    @Rule
    public final TestServer testServer = new TestServer();


    @Test
    public void testEnvCapture(){

        final StringBuilder runEnvBuffer = new StringBuilder();

        RunConfigBuilder builder = new RunConfigBuilder(CmdBuilder.getBuilder());

        Script setupScript = new Script("setup-env");
        setupScript.then(Cmd.sh("export FOO=\"FOO\""));
        setupScript.then(Cmd.sh("unset PROMPT_COMMAND"));
        setupScript.then(Cmd.sh("export VERTX_HOME=\"/tmp\"")
        //        .then(Cmd.echo())
        );
        //setupScript.then(Cmd.sh("env"));


        Script runScript = new Script("run-env");
        runScript.then(Cmd.sh("env",false).then(Cmd.code((input,state)->{
            runEnvBuffer.append(input);
            return Result.next(input);
        })));
        //runScript.then(Cmd.echo());

        builder.addScript(setupScript);
        builder.addScript(runScript);

        builder.addHostAlias("local","wreicher@localhost:22");//+testServer.getPort());
        //builder.addHostAlias("local","wreicher@localhost:"+testServer.getPort());
        builder.addHostToRole("role","local");
        builder.addRoleRun("role","run-env",new HashMap<>());
        builder.addRoleSetup("role","setup-env",new HashMap<>());

        RunConfig config = builder.buildConfig();

        CommandDispatcher dispatcher = new CommandDispatcher();
        Run run = new Run("/tmp",config,dispatcher);

        run.run();

        String runEnv = runEnvBuffer.toString();
        assertTrue("run-env output should contain FOO=FOO",runEnv.contains("FOO=FOO"));
        assertTrue("run-env output should contain VERTX_HOME=/tmp",runEnv.contains("VERTX_HOME=/tmp"));


    }
}
