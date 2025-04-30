package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.SecretFilter;
import io.hyperfoil.tools.qdup.cmd.*;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import io.hyperfoil.tools.qdup.shell.AbstractShell;
import io.hyperfoil.tools.yaup.time.SystemTimer;
import org.junit.Test;
import io.hyperfoil.tools.qdup.SshTestBase;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class UploadTest extends SshTestBase {

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

            RunConfig config = builder.buildConfig(Parser.getInstance());

            assertFalse("unexpected errors:\n"+config.getErrorStrings().stream().collect(Collectors.joining("\n")),config.hasErrors());

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

    @Test
    public void return_remote_path_new_name() throws IOException {
        String wrote = "bizbuz";
        File source = Files.createTempFile("qdup","upload.txt").toFile();
        Files.write(source.toPath(),wrote.getBytes());
        File runFolder = Files.createTempDirectory("qdup").toFile();

        Upload upload = new Upload(source.getPath(),"/tmp/found.txt");

        AbstractShell shell = AbstractShell.getShell(
                Host.parse(Host.LOCAL),
                new ScheduledThreadPoolExecutor(2),
                new SecretFilter(),
                false
        );
        Run run = new Run(runFolder.getPath(),new RunConfigBuilder().buildConfig(),new Dispatcher());
        ScriptContext scriptContext = new ScriptContext(shell,run.getConfig().getState(),run,new SystemTimer("download"),upload,true);
        SpyContext spyContext = new SpyContext(scriptContext,run.getConfig().getState(), run.getCoordinator());
        upload.run("",spyContext);

        assertNotNull("upload should call next",spyContext.getNext());
        String response = spyContext.getNext();
        assertEquals("response should match the destination","/tmp/found.txt",response);
        String readContent = Files.readString(Paths.get(response));
        assertEquals(wrote,readContent);
    }

    @Test
    public void return_remote_path_target_folder() throws IOException {
        String wrote = "bizbuz";
        File source = Files.createTempFile("qdup.",".upload.txt").toFile();
        Files.write(source.toPath(),wrote.getBytes());
        File runFolder = Files.createTempDirectory("qdup_").toFile();

        File tempFolder = Files.createTempDirectory("qdup_").toFile();
        //tempFolder.mkdirs();
        Upload upload = new Upload(source.getPath(),tempFolder.getAbsolutePath()+File.separator);

        AbstractShell shell = AbstractShell.getShell(
            Host.parse(Host.LOCAL),
            new ScheduledThreadPoolExecutor(2),
            new SecretFilter(),
            false
        );
        Run run = new Run(runFolder.getPath(),new RunConfigBuilder().buildConfig(),new Dispatcher());
        ScriptContext scriptContext = new ScriptContext(shell,run.getConfig().getState(),run,new SystemTimer("download"),upload,true);
        SpyContext spyContext = new SpyContext(scriptContext,run.getConfig().getState(), run.getCoordinator());
        upload.run("",spyContext);

        assertNotNull("upload should call next",spyContext.getNext());
        String response = spyContext.getNext();
        assertTrue("response should end with source name",response.endsWith(source.getName()));
        String readContent = Files.readString(Paths.get(response));
        assertEquals(wrote,readContent);
    }

    @Test
    public void return_remote_path_target_file() throws IOException {
        String wrote = "bizbuz";
        File source = Files.createTempFile("qdup.",".upload.txt").toFile();
        Files.write(source.toPath(),wrote.getBytes());
        File runFolder = Files.createTempDirectory("qdup_").toFile();

        Upload upload = new Upload(source.getPath(),"/tmp/renamed.txt");
        AbstractShell shell = AbstractShell.getShell(
            Host.parse(Host.LOCAL),
            new ScheduledThreadPoolExecutor(2),
            new SecretFilter(),
            false
        );
        Run run = new Run(runFolder.getPath(),new RunConfigBuilder().buildConfig(),new Dispatcher());
        ScriptContext scriptContext = new ScriptContext(shell,run.getConfig().getState(),run,new SystemTimer("download"),upload,true);
        SpyContext spyContext = new SpyContext(scriptContext,run.getConfig().getState(), run.getCoordinator());
        upload.run("",spyContext);

        assertNotNull("upload should call next",spyContext.getNext());
        String response = spyContext.getNext();
        assertEquals("response should be the specified path","/tmp/renamed.txt",response);
        String readContent = Files.readString(Paths.get(response));
        assertEquals(wrote,readContent);
    }

}
