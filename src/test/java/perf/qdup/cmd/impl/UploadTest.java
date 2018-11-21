package perf.qdup.cmd.impl;

import org.junit.Test;
import perf.qdup.Run;
import perf.qdup.SshTestBase;
import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Dispatcher;
import perf.qdup.cmd.Script;
import perf.qdup.config.CmdBuilder;
import perf.qdup.config.RunConfig;
import perf.qdup.config.RunConfigBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UploadTest extends SshTestBase {

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
            runScript.then(Cmd.upload(tmpFile.getPath(),"/tmp/destination/"));

            builder.addScript(runScript);
            builder.addHostAlias("local",getHost().toString());
            builder.addHostToRole("role","local");
            builder.addRoleRun("role","run-upload",new HashMap<>());

            RunConfig config = builder.buildConfig();

            assertFalse("unexpected errors:\n"+config.getErrors().stream().collect(Collectors.joining("\n")),config.hasErrors());


            Dispatcher dispatcher = new Dispatcher();
            Run run = new Run("/tmp",config,dispatcher);
            run.run();

            File uploadedFile = new File("/tmp/destination/"+tmpFile.getName());
            assertTrue("file should have been uploaded after test run",uploadedFile.exists());

            tmpFile.delete();
            boolean uploadDeleted = uploadedFile.delete();
            boolean destinationDeleted = uploadedFile.getParentFile().delete();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
