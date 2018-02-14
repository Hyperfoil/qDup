package perf.ssh.cmd.impl;

import org.junit.Rule;
import org.junit.Test;
import perf.ssh.TestServer;
import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.Script;
import perf.ssh.config.CmdBuilder;
import perf.ssh.config.RunConfigBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class UploadTest {

    @Rule
    public final TestServer testServer = new TestServer();

    @Test
    public void uploadFile(){
        try {
            File tmpFile = File.createTempFile("UploadTest","txt",new File("/tmp"));

            RunConfigBuilder builder = new RunConfigBuilder(CmdBuilder.getBuilder());


            String timestamp = ""+System.currentTimeMillis();

            Files.write(tmpFile.toPath(),timestamp.getBytes());


            Script runScript = new Script("run-upload");
            runScript.then(Cmd.upload(tmpFile.getPath(),"/tmp/destination"))

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
