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
import java.util.stream.Collectors;

import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class UploadTest {

//    @Rule
//    public final TestServer testServer = new TestServer();

    @Test
    public void uploadFile(){
        try {
            File tmpFile = File.createTempFile("UploadTest",".txt",new File("/tmp"));

            RunConfigBuilder builder = new RunConfigBuilder(CmdBuilder.getBuilder());

            File destination = new File("/tmp/destination");
            if(destination.exists()){
                destination.delete();
            }

            String timestamp = ""+System.currentTimeMillis();

            Files.write(tmpFile.toPath(),timestamp.getBytes());

            Script runScript = new Script("run-upload");
            runScript.then(Cmd.upload(tmpFile.getPath(),"/tmp/destination"));

            builder.addScript(runScript);
            builder.addHostAlias("local","wreicher@localhost:22");
            //builder.addHostAlias("local","wreicher@localhost:"+testServer.getPort());
            builder.addHostToRole("role","local");
            builder.addRoleRun("role","run-upload",new HashMap<>());

            RunConfig config = builder.buildConfig();

            assertFalse("unexpected errors:\n"+config.getErrors().stream().collect(Collectors.joining("\n")),config.hasErrors());


            CommandDispatcher dispatcher = new CommandDispatcher();
            Run run = new Run("/tmp",config,dispatcher);
            run.run();

            File uploadedFile = new File("/tmp/destination/"+tmpFile.getName());
            assertTrue("file should have been uploaded after test run",uploadedFile.exists());

            boolean uploadDeleted = uploadedFile.delete();
            boolean destinationDeleted = uploadedFile.getParentFile().delete();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
