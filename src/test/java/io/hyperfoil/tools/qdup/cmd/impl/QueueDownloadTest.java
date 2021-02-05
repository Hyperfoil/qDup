package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.Local;
import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.cmd.Script;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.converter.FileSizeConverter;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import org.junit.Test;
import io.hyperfoil.tools.qdup.SshTestBase;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class QueueDownloadTest extends SshTestBase {

    @Test
    public void stateToLocalEnv(){
        RunConfigBuilder builder = getBuilder();

        String timestamp = ""+System.currentTimeMillis();

        Script runScript = new Script("run-queue");
        runScript.then(Cmd.sh("rm -rf /tmp/"+getHost().getHostName()+"/"));
        runScript.then(Cmd.sh("export FOO=\"/tmp\""));
        runScript.then(Cmd.sh("echo "+timestamp+" > /tmp/date.txt"));

        runScript.then(Cmd.queueDownload("${{FOO}}.txt"));


        builder.addScript(runScript);

        builder.addHostAlias("local",getHost().toString());
        builder.addHostToRole("role","local");
        builder.addRoleRun("role","run-queue",new HashMap<>());
        builder.getState().set("FOO","$(echo /tmp/date)");
        RunConfig config = builder.buildConfig(Parser.getInstance());


        assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());


        Dispatcher dispatcher = new Dispatcher();
        Run run = new Run(tmpDir.toString(),config,dispatcher);
        run.run();

        File downloadFile = new File(tmpDir.toString() + "/"+getHost().getHostName()+"/date.txt");

        assertTrue(tmpDir.toString() + "/download.txt should exist",downloadFile.exists());

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

    @Test
    public void populate_local_env_dollar(){
        RunConfigBuilder builder = getBuilder();
        String timestamp = ""+System.currentTimeMillis();
        Script runScript = new Script("run-queue");
        runScript.then(Cmd.sh("rm -rf /tmp/"+getHost().getHostName()+"/"));
        runScript.then(Cmd.sh("export FOO=\"/tmp\""));
        runScript.then(Cmd.sh("cd /tmp"));
        runScript.then(Cmd.sh("echo "+timestamp+" > /tmp/date.txt"));
        runScript.then(Cmd.queueDownload("$FOO/date.txt"));
        builder.addScript(runScript);
        builder.addHostAlias("local",getHost().toString());
        builder.addHostToRole("role","local");
        builder.addRoleRun("role","run-queue",new HashMap<>());

        RunConfig config = builder.buildConfig(Parser.getInstance());
        Dispatcher dispatcher = new Dispatcher();
        Run run = new Run(tmpDir.toString(),config,dispatcher);
        run.run();

        File downloadFile = new File(tmpDir.toString() + "/"+getHost().getHostName()+"/date.txt");
        assertTrue(tmpDir.toString() + "/download.txt should exist",downloadFile.exists());
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
    @Test
    public void populate_local_env_tick(){
        RunConfigBuilder builder = getBuilder();
        String timestamp = ""+System.currentTimeMillis();
        Script runScript = new Script("run-queue");
        runScript.then(Cmd.sh("rm -rf /tmp/"+getHost().getHostName()+"/"));
        runScript.then(Cmd.sh("export FOO=\"/tmp\""));
        runScript.then(Cmd.sh("cd /tmp"));
        runScript.then(Cmd.sh("echo "+timestamp+" > /tmp/date.txt"));
        runScript.then(Cmd.queueDownload("`pwd`/date.txt"));
        builder.addScript(runScript);
        builder.addHostAlias("local",getHost().toString());
        builder.addHostToRole("role","local");
        builder.addRoleRun("role","run-queue",new HashMap<>());

        RunConfig config = builder.buildConfig(Parser.getInstance());
        Dispatcher dispatcher = new Dispatcher();
        Run run = new Run(tmpDir.toString(),config,dispatcher);
        run.run();

        File downloadFile = new File(tmpDir.toString() + "/"+getHost().getHostName()+"/date.txt");
        assertTrue(tmpDir.toString() + "/download.txt should exist",downloadFile.exists());
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
    @Test
    public void populate_local_env_dollar_parenthesis(){
        RunConfigBuilder builder = getBuilder();
        String timestamp = ""+System.currentTimeMillis();
        Script runScript = new Script("run-queue");
        runScript.then(Cmd.sh("rm -rf /tmp/"+getHost().getHostName()+"/"));
        runScript.then(Cmd.sh("export FOO=\"/tmp\""));
        runScript.then(Cmd.sh("cd /tmp"));
        runScript.then(Cmd.sh("echo "+timestamp+" > /tmp/date.txt"));
        runScript.then(Cmd.queueDownload("$(pwd)/date.txt"));
        builder.addScript(runScript);
        builder.addHostAlias("local",getHost().toString());
        builder.addHostToRole("role","local");
        builder.addRoleRun("role","run-queue",new HashMap<>());

        RunConfig config = builder.buildConfig(Parser.getInstance());
        Dispatcher dispatcher = new Dispatcher();
        Run run = new Run(tmpDir.toString(),config,dispatcher);
        run.run();

        File downloadFile = new File(tmpDir.toString() + "/"+getHost().getHostName()+"/date.txt");
        assertTrue(tmpDir.toString() + "/download.txt should exist",downloadFile.exists());
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

    @Test
    public void maxFileSize(){

        String maxSize = "5MB";

        String largeFilePath = "/tmp/large.file";
        String smallFilePath = "/tmp/small.file";

        RunConfigBuilder builder = getBuilder();

        Script runScript = new Script("run-queue");
        runScript.then(Cmd.sh("echo 'Allocating files'"));
        runScript.then(Cmd.sh("fallocate -l 10MB ".concat(largeFilePath)));
        runScript.then(Cmd.sh("fallocate -l 1MB ".concat(smallFilePath)));

        runScript.then(Cmd.queueDownload(largeFilePath, FileSizeConverter.toBytes(maxSize)));
        runScript.then(Cmd.queueDownload(smallFilePath, FileSizeConverter.toBytes(maxSize)));

        builder.addScript(runScript);

        builder.addHostAlias("local",getHost().toString());
        builder.addHostToRole("role","local");
        builder.addRoleRun("role","run-queue",new HashMap<>());

        RunConfig config = builder.buildConfig(Parser.getInstance());

        assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());

        Dispatcher dispatcher = new Dispatcher();
        Run run = new Run(tmpDir.toString(),config,dispatcher);
        run.run();

        long remoteLargeFileSize = run.getLocal().remoteFileSize(largeFilePath, getHost());

        long remoteSmallFileSize = run.getLocal().remoteFileSize(smallFilePath, getHost());

        assertEquals(10000000l, remoteLargeFileSize);
        assertEquals(1000000l, remoteSmallFileSize);

        assertTrue(new File(tmpDir.toString().concat("/").concat(getHost().getHostName()).concat("/").concat("small.file")).exists());
        assertFalse(new File(tmpDir.toString().concat("/").concat(getHost().getHostName()).concat("/").concat("large.file")).exists());

        new File(tmpDir.toString()).delete();
    }

}
