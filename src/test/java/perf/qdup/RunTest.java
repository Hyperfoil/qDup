package perf.qdup;

import org.junit.Assert;
import org.junit.Test;
import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.CommandDispatcher;
import perf.qdup.cmd.Result;
import perf.qdup.cmd.Script;
import perf.qdup.config.CmdBuilder;
import perf.qdup.config.RunConfig;
import perf.qdup.config.RunConfigBuilder;

import java.io.File;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RunTest extends SshTestBase{

//    @Rule
//    public final TestServer testServer = new TestServer();


    @Test
    public void testTwoSetupNotSkipSecond(){
        final StringBuilder first = new StringBuilder();
        final StringBuilder second = new StringBuilder();

        RunConfigBuilder builder = new RunConfigBuilder(CmdBuilder.getBuilder());

        Script firstScript = new Script("first");
        firstScript.then(Cmd.code((input,state)->{
            first.append(System.currentTimeMillis());
            return Result.skip(input);
        }));


        Script secondScript = new Script("second");
        secondScript.then(Cmd.sleep("500"));//to ensure second is > first if called
        secondScript.then(Cmd.code((input, state) -> {
            second.append(System.currentTimeMillis());
            return Result.next(input);
        }));

        builder.addScript(firstScript);
        builder.addScript(secondScript);

        builder.addHostAlias("local",getHost().toString());//+testServer.getPort());
        builder.addHostToRole("role","local");
        builder.addRoleSetup("role","first",new HashMap<>());
        builder.addRoleSetup("role","second",new HashMap<>());


        RunConfig config = builder.buildConfig();
        CommandDispatcher dispatcher = new CommandDispatcher();
        Run run = new Run("/tmp",config,dispatcher);

        run.run();

        assertFalse("first should be called",first.toString().isEmpty());
        assertFalse("second should be called",second.toString().isEmpty());

        assertTrue("first should be called before second: first="+first.toString()+" second="+second.toString(),first.toString().compareTo(second.toString()) < 0);
    }

    @Test(timeout=45_000)
    public void testDone(){
        final StringBuilder first = new StringBuilder();
        final AtomicLong cleanupTimer = new AtomicLong();
        final AtomicBoolean staysFalse = new AtomicBoolean(false);
        RunConfigBuilder builder = new RunConfigBuilder(CmdBuilder.getBuilder());
        Script doneScript = new Script("run-done");

        doneScript
            .then(Cmd.sh("echo foo > /tmp/foo.txt"))
            .then(Cmd.queueDownload("/tmp/foo.txt"))
            .then(Cmd.sleep("2_000"))
            .then(Cmd.log("done waiting"))
            .then(Cmd.done())
            .then(Cmd.code((input, state) -> {
                staysFalse.set(true);
                return Result.next(input);
            }));
        Script waitScript = new Script("run-wait");
        waitScript.then(Cmd.waitFor("NEVER"));
        Script signalScript = new Script("run-signal");
        signalScript.then(Cmd.sleep("30s")).then(Cmd.signal("NEVER"));
        Script cleanupScript = new Script("cleanup");
        cleanupScript.then(Cmd.code((input,state)->{
            first.append(System.currentTimeMillis());
            cleanupTimer.set(System.currentTimeMillis());
            return Result.next(input);
        }));

        builder.addScript(doneScript);
        builder.addScript(waitScript);
        builder.addScript(signalScript);
        builder.addScript(cleanupScript);
        builder.addHostAlias("local",getHost().toString());//+testServer.getPort());
        builder.addHostToRole("role","local");
        builder.addRoleRun("role","run-done",new HashMap<>());
        builder.addRoleRun("role","run-wait",new HashMap<>());
        builder.addRoleRun("role","run-signal",new HashMap<>());
        builder.addRoleCleanup("role","cleanup",new HashMap<>());

        RunConfig config = builder.buildConfig();
        CommandDispatcher dispatcher = new CommandDispatcher();
        Run run = new Run("/tmp",config,dispatcher);
        long start = System.currentTimeMillis();
        run.run();
        assertFalse("script should not invoke beyond a done",staysFalse.get());
        assertTrue("cleanupTimer should be > 0",cleanupTimer.get() > 0);
        assertTrue("done should stop before NEVER is signalled",cleanupTimer.get() - start < 30_000);


        File foo = new File("/tmp/foo.txt");
        File outputPath = new File(run.getOutputPath());
        File downloaded = new File(outputPath.getAbsolutePath(),"laptop/foo.txt");

        assertTrue("queue-download should execute despite done",downloaded.exists());


        foo.delete();
        downloaded.delete();
        downloaded.getParentFile().delete();
    }

    @Test
    public void testTimer(){
        final StringBuilder first = new StringBuilder();
        final StringBuilder second = new StringBuilder();
        RunConfigBuilder builder = new RunConfigBuilder(CmdBuilder.getBuilder());
        Script script = new Script("run-timer");
        script.then(
            Cmd.sleep("4_000").addTimer(2_000,Cmd.code(((input, state) -> {
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
