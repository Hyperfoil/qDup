package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.*;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.cmd.Script;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.converter.FileSizeConverter;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import io.hyperfoil.tools.yaup.AsciiArt;
import io.hyperfoil.tools.yaup.file.FileUtility;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Objects;
import java.util.stream.Collectors;

import static io.hyperfoil.tools.qdup.cmd.PatternValuesMap.QDUP_GLOBAL;
import static io.hyperfoil.tools.qdup.cmd.PatternValuesMap.QDUP_GLOBAL_ABORTED;
import static org.junit.Assert.*;

public class QueueDownloadTest extends SshTestBase {


    @Test
    public void custom_download_scp(){
        Parser parser = Parser.getInstance();
        parser.setAbortOnExitCode(true);
        RunConfigBuilder builder = getBuilder();

        builder.loadYaml(parser.loadFile("pwd",
                """
                scripts:
                  foo:
                   - sh: ls -al /root/.ssh
                   - sh: mkdir -p /tmp/foo/folder-one
                   - sh: mkdir -p /tmp/foo/folder-two
                   - sh: echo 'uno' >> /tmp/foo/folder-one/uno.txt
                   - sh: echo 'dos' >> /tmp/foo/folder-two/dos.txt
                   - queue-download: /tmp/foo/folder-one/uno.txt
                hosts:
                  local:
                    username: HOST_USERNAME
                    hostname: HOST_HOSTNAME
                    port: HOST_PORT
                    identity: HOST_IDENTITY
                    download:
                    - scp
                    - "-q"
                    - "-i"
                    - KEY_PATH
                    - "-o"
                    - StrictHostKeyChecking=no
                    - "-o"
                    - UserKnownHostsFile=/dev/null
                    - "-P"
                    - ${{host.port}}
                    - "-r"
                    - ${{host.username}}@${{host.hostname}}:${{source}}
                    - ${{destination}}
                roles:
                  doit:
                    hosts: [local]
                    run-scripts: [foo]
                """.replaceAll("KEY_PATH",getPath("keys/qdup").toFile().getPath())
                        .replaceAll("HOST_USERNAME", getHost().getUserName())
                        .replaceAll("HOST_HOSTNAME",getHost().getHostName())
                        .replaceAll("HOST_PORT",""+getHost().getPort())
                        .replaceAll("HOST_IDENTITY",getHost().getIdentity())
        ));
        RunConfig config = builder.buildConfig(parser);

        Host h = config.getAllHostsInRoles().iterator().next();
        assertNotNull("host should exit",h);
        assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);

        doit.run();

        State state = doit.getConfig().getState();
        Host host = config.getAllHostsInRoles().iterator().next();

        File uno = new File(tmpDir.toString() + "/"+host.getShortHostName()+"/uno.txt");

        File tmpFile = new File(tmpDir.toString());
        String tree = AsciiArt.printTree(new File(tmpDir.toString()),(f)-> f.listFiles() == null ? Collections.EMPTY_LIST : Arrays.asList(f.listFiles()),v->(v.isDirectory()? "/":"")+v.getName());
        assertTrue("uno should exist @ "+uno.getPath()+"\n"+tree,uno.exists());
    }
    @Test(timeout = 50_000)
    public void path_star_slash_star(){
        Parser parser = Parser.getInstance();
        parser.setAbortOnExitCode(true);
        RunConfigBuilder builder = getBuilder();

        builder.loadYaml(parser.loadFile("pwd",
                """
                scripts:
                  foo:
                   - sh: mkdir -p /tmp/foo/folder-one
                   - sh: mkdir -p /tmp/foo/folder-two
                   - sh: echo 'uno' >> /tmp/foo/folder-one/uno.txt
                   - sh: echo 'dos' >> /tmp/foo/folder-two/dos.txt
                   - queue-download: /tmp/foo/folder-*/*.txt
                hosts:
                  local: TARGET_HOST
                roles:
                  doit:
                    hosts: [local]
                    run-scripts: [foo]
                """.replaceAll("TARGET_HOST",getHost().toString())
        ));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();

        State state = doit.getConfig().getState();
        Host host = config.getAllHostsInRoles().iterator().next();

        File uno = new File(tmpDir.toString() + "/"+host.getShortHostName()+"/uno.txt");
        File dos = new File(tmpDir.toString() + "/"+host.getShortHostName()+"/dos.txt");

        assertTrue("uno should exist @ "+uno.getPath(),uno.exists());
        assertTrue("dos should exist @ "+dos.getPath(),dos.exists());
    }

    @Test(timeout = 50_000)
    public void path_slash_star(){
        Parser parser = Parser.getInstance();
        parser.setAbortOnExitCode(true);
        RunConfigBuilder builder = getBuilder();

        builder.loadYaml(parser.loadFile("pwd",
                """
                scripts:
                  foo:
                   - sh: mkdir -p /tmp/foo/one
                   - sh: mkdir -p /tmp/foo/two
                   - sh: echo 'uno' >> /tmp/foo/one/uno.txt
                   - sh: echo 'dos' >> /tmp/foo/two/dos.txt
                   - queue-download: /tmp/foo/*
                hosts:
                  local: TARGET_HOST
                roles:
                  doit:
                    hosts: [local]
                    run-scripts: [foo]
                """.replaceAll("TARGET_HOST",getHost().toString())
        ));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();

        State state = doit.getConfig().getState();
        Host host = config.getAllHostsInRoles().iterator().next();

        File uno = new File(tmpDir.toString() + "/"+host.getShortHostName()+"/one/uno.txt");
        File dos = new File(tmpDir.toString() + "/"+host.getShortHostName()+"/two/dos.txt");

        assertTrue("uno should exist @ "+uno.getPath(),uno.exists());
        assertTrue("dos should exist @ "+dos.getPath(),dos.exists());
    }


    @Test
    public void stateToLocalEnv(){
        RunConfigBuilder builder = getBuilder();

        String timestamp = ""+System.currentTimeMillis();

        Script runScript = new Script("run-queue");
        runScript.then(Cmd.sh("rm -rf /tmp/"+getHost().getShortHostName()+"/"));
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
        Host host = config.getAllHostsInRoles().iterator().next();

        File downloadFile = new File(tmpDir.toString() + "/"+host.getShortHostName()+"/date.txt");

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
        runScript.then(Cmd.sh("rm -rf /tmp/"+getHost().getShortHostName()+"/"));
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
        Host host = config.getAllHostsInRoles().iterator().next();

        File downloadFile = new File(tmpDir.toString() + "/"+host.getShortHostName()+"/date.txt");
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
        runScript.then(Cmd.sh("rm -rf /tmp/"+getHost().getShortHostName()+"/"));
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
        Host host = config.getAllHostsInRoles().iterator().next();

        File downloadFile = new File(tmpDir.toString() + "/"+host.getShortHostName()+"/date.txt");
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
    public void populate_relativepath(){
        Parser parser = Parser.getInstance();
        parser.setAbortOnExitCode(true);
        RunConfigBuilder builder = getBuilder();

        builder.loadYaml(parser.loadFile("pwd",
                """
                scripts:
                  foo:
                   - sh: mkdir -p /tmp/foo/one
                   - sh: mkdir -p /tmp/foo/two
                   - sh: echo 'uno' >> /tmp/foo/one/uno.txt
                   - sh: echo 'dos' >> /tmp/foo/two/dos.txt
                   - sh: cd /tmp/foo
                   - queue-download: ./one/uno.txt
                   - sh: cd /tmp/foo/two
                   - queue-download: ./dos.txt
                hosts:
                  local: TARGET_HOST
                roles:
                  doit:
                    hosts: [local]
                    run-scripts: [foo]
                """.replaceAll("TARGET_HOST",getHost().toString())
        ));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();

        Host host = config.getAllHostsInRoles().iterator().next();

        State state = doit.getConfig().getState();

        File uno = new File(tmpDir.toString() + "/"+host.getShortHostName()+"/uno.txt");
        File dos = new File(tmpDir.toString() + "/"+host.getShortHostName()+"/dos.txt");



        assertTrue("uno should exist @ "+uno.getPath(),uno.exists());
        assertTrue("dos should exist @ "+dos.getPath(),dos.exists());
    }

    @Test
    public void populate_relativepath_watcher(){
        Parser parser = Parser.getInstance();
        parser.setAbortOnExitCode(true);
        RunConfigBuilder builder = getBuilder();

        builder.loadYaml(parser.loadFile("pwd",
                """
                scripts:
                  bar:
                   - sleep: 2s
                   - sh: cd /tmp/foo
                   - sh: tail -f list.txt
                     watch:
                     - regex: done
                       then:
                       - ctrlC
                     - regex: (?<path>.*txt)
                       then:
                       - queue-download: ${{path}}
                  foo:
                   - sh: mkdir -p /tmp/foo/one
                   - sh: mkdir -p /tmp/foo/two
                   - sh: echo '' > /tmp/foo/list.txt
                   - sh: echo 'uno' >> /tmp/foo/one/uno.txt
                   - sh: echo 'dos' >> /tmp/foo/two/dos.txt
                   - sleep: 2s
                   - sh: echo 'one/uno.txt' >> /tmp/foo/list.txt
                   - sh: echo './two/dos.txt' >> /tmp/foo/list.txt
                   - sh: echo 'done' >> /tmp/foo/list.txt
                hosts:
                  local: TARGET_HOST
                roles:
                  doit:
                    hosts: [local]
                    run-scripts: [foo, bar]
                """.replaceAll("TARGET_HOST",getHost().toString())
        ));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();


        State state = doit.getConfig().getState();
        Host host = config.getAllHostsInRoles().iterator().next();

        File dos = new File(tmpDir.toString() + "/"+host.getShortHostName()+"/dos.txt");
        File uno = new File(tmpDir.toString() + "/"+host.getShortHostName()+"/uno.txt");



        assertTrue("uno should exist @ "+uno.getPath(),uno.exists());
        assertTrue("dos should exist @ "+dos.getPath(),dos.exists());
    }
    @Test
    public void populate_local_env_dollar_parenthesis(){
        RunConfigBuilder builder = getBuilder();
        String timestamp = ""+System.currentTimeMillis();
        Script runScript = new Script("run-queue");
        runScript.then(Cmd.sh("rm -rf /tmp/"+getHost().getShortHostName()+"/"));
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
        Host host = config.getAllHostsInRoles().iterator().next();
        File downloadFile = new File(tmpDir.toString() + "/"+host.getShortHostName()+"/date.txt");
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
        Host host = config.getAllHostsInRoles().iterator().next();

        assertTrue(new File(tmpDir.toString().concat("/").concat(host.getShortHostName()).concat("/").concat("small.file")).exists());
        assertFalse(new File(tmpDir.toString().concat("/").concat(host.getShortHostName()).concat("/").concat("large.file")).exists());

        new File(tmpDir.toString()).delete();
    }

}
