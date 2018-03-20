package perf.qdup.cmd.impl;

import org.junit.Rule;
import org.junit.Test;
import perf.qdup.Run;
import perf.qdup.TestServer;
import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.CommandDispatcher;
import perf.qdup.cmd.Script;
import perf.qdup.config.CmdBuilder;
import perf.qdup.config.RunConfig;
import perf.qdup.config.RunConfigBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class QueueDownloadTest {

    @Rule
    public final TestServer testServer = new TestServer();


    @Test
    public void populateLocalEnv(){
        RunConfigBuilder builder = new RunConfigBuilder(CmdBuilder.getBuilder());

        String timestamp = ""+System.currentTimeMillis();

        Script runScript = new Script("run-queue");
        runScript.then(Cmd.sh("rm -rf /tmp/localhost/"));
        runScript.then(Cmd.sh("export FOO=\"/tmp\""));
        runScript.then(Cmd.sh("echo "+timestamp+" > /tmp/date.txt"));

        runScript.then(Cmd.queueDownload("$FOO/date.txt"));


        builder.addScript(runScript);

        //builder.addHostAlias("local","wreicher@localhost:"+testServer.getPort());
        builder.addHostAlias("local","wreicher@localhost:22");
        builder.addHostToRole("role","local");
        builder.addRoleRun("role","run-queue",new HashMap<>());

        RunConfig config = builder.buildConfig();
        CommandDispatcher dispatcher = new CommandDispatcher();
        Run run = new Run("/tmp",config,dispatcher);
        run.run();

        File downloadFile = new File("/tmp/localhost/date.txt");

        assertTrue("/tmp/download.txt should exist",downloadFile.exists());

        try {
            String content = new String(Files.readAllBytes(downloadFile.toPath())).trim();

            assertTrue("downloaded file should match expected [["+timestamp+"]] found [["+content+"]]",timestamp.equals(content));
        } catch (IOException e) {
            fail("IOException trying to read "+downloadFile.getPath()+" "+e.getMessage());
            e.printStackTrace();
        }

        downloadFile.delete();
        downloadFile.getParentFile().delete();
        new File("/tmp/date.txt").delete();
    }
}
