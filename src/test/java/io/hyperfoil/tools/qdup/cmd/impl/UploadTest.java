package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.cmd.Script;
import io.hyperfoil.tools.qdup.config.CmdBuilder;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import org.junit.Test;
import io.hyperfoil.tools.qdup.SshTestBase;

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

            RunConfigBuilder builder = getBuilder();

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
            Run run = new Run(tmpDir.toString(),config,dispatcher);
            run.run();

            //File uploadedFile = new File("/tmp/destination/"+tmpFile.getName());
            assertTrue("file should have been uploaded after test run",exists("/tmp/destination/"+tmpFile.getName()));
            tmpFile.delete();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
