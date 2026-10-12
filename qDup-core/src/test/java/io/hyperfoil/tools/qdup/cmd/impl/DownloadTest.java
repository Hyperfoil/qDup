package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.*;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.cmd.ScriptContext;
import io.hyperfoil.tools.qdup.cmd.SpyContext;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import io.hyperfoil.tools.qdup.shell.AbstractShell;
import io.hyperfoil.tools.qdup.shell.LocalShell;
import io.hyperfoil.tools.yaup.time.SystemTimer;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class DownloadTest extends SshTestBase{

    @Test
    public void download_folder_local_remote_container_consistency() throws IOException {
        AbstractShell localShell = AbstractShell.getShell(
                "download_consistency",
                Host.parse(Host.LOCAL),//getHost(),//
                new ScheduledThreadPoolExecutor(2),
                new SecretFilter(),
                null
        );
        AbstractShell remoteShell = AbstractShell.getShell(
                "download_consistency",
                getHost(),
                new ScheduledThreadPoolExecutor(2),
                new SecretFilter(),
                null
        );
        Host host = Host.parse("quay.io/fedora/fedora", getContainerPlatform());
        host.setAlias("container");
        host.setIdentity(getIdentity());
        AbstractShell containerShell = AbstractShell.getShell(
                "download_consistency",
                host,
                "",
                new ScheduledThreadPoolExecutor(2),
                new SecretFilter(),
                null
        );
        assertTrue(containerShell.getHost().isContainer());
        assertTrue(containerShell.isOpen());
        assertTrue(containerShell.isReady());

        Path runPath = Files.createTempDirectory("run");
        Run run = new Run(runPath.toString(),getBuilder().buildConfig(),new Dispatcher());

        ScriptContext localContext = new ScriptContext(localShell,run.getConfig().getState(),run,new SystemTimer("upload"), Cmd.NO_OP(),true);
        SpyContext localSpyContext = new SpyContext(localContext,run.getConfig().getState(), run.getCoordinator());

        ScriptContext remoteContext = new ScriptContext(remoteShell,run.getConfig().getState(),run,new SystemTimer("upload"),Cmd.NO_OP(),true);
        SpyContext remoteSpyContext = new SpyContext(remoteContext,run.getConfig().getState(), run.getCoordinator());

        ScriptContext containerContext = new ScriptContext(containerShell,run.getConfig().getState(),run,new SystemTimer("upoad"),Cmd.NO_OP(),true);
        SpyContext containerSpyContext = new SpyContext(containerContext,run.getConfig().getState(), run.getCoordinator());

        //create the files to download
        for(AbstractShell shell : Arrays.asList(localShell,remoteShell,containerShell)){
            shell.execSync("mkdir -p /tmp/download_consistency");
            shell.execSync("/bin/sh -c 'echo \"one\" > /tmp/download_consistency/one.txt'");
            shell.execSync("/bin/sh -c 'echo \"two\" > /tmp/download_consistency/two.txt'");
        }

        for(Download download : List.of(
/*
                new Download("/tmp/download_consistency", "one"),
                new Download("/tmp/download_consistency", "one/"),
*/
                /*new Download("/tmp/download_consistency/", "one/"),*/
                new Download("/tmp/download_consistency/", "one")
        )){
            download.run("",remoteSpyContext);
            assertTrue(remoteSpyContext.hasNext());
            String remotePath =remoteSpyContext.getNext();
            List<String> remoteContent = Files.walk(Path.of(remoteSpyContext.getNext())).map(Path::toString).toList();

            download.run("",localSpyContext);
            assertTrue(localSpyContext.hasNext());
            String localPath =localSpyContext.getNext();
            List<String> localContent = Files.walk(Path.of(localSpyContext.getNext())).map(Path::toString).toList();

            download.run("",containerSpyContext);
            assertTrue(containerSpyContext.hasNext());
            String containerPath =containerSpyContext.getNext();
            List<String> containerContent = Files.walk(Paths.get(containerSpyContext.getNext())).map(Path::toString).toList();

            assertEquals("expect 3 remote entries: "+remoteContent,3,remoteContent.size());
            assertEquals("expect 3 local entries: "+localContent,3,localContent.size());
            assertEquals("expect 3 container entries: "+containerContent,3,containerContent.size());
            for(int i=0; i<3; i++){
                String local = localContent.get(i).substring(localContent.get(i).indexOf(download.getDestination()));
                String remote = remoteContent.get(i).substring(remoteContent.get(i).indexOf(download.getDestination()));
                String  container = containerContent.get(i).substring(containerContent.get(i).indexOf(download.getDestination()));
                assertTrue("paths["+i+"] should match: "+List.of(remoteContent.get(i),localContent.get(i),containerContent.get(i)),
                        local.equals(remote) && local.equals(container));
            }
            //cleanup the local download for the next loop
            rm(remotePath);
            rm(localPath);
            rm(containerPath);

        }

        //remove the files to download
        for(AbstractShell shell : Arrays.asList(localShell,remoteShell,containerShell)){
            shell.execSync("rm -rf /tmp/download_consistency");
        }
    }

    private boolean rm(String dir){
        try (Stream<Path> walk = Files.walk(Path.of(dir))) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            System.err.printf("Failed to delete %s%n", path);
                        }
                    });
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    @Test
    public void return_local_file_path_when_defined_without_destination() throws IOException {
        String wrote = "bizbuz";
        File source = Files.createTempFile("qdup","txt").toFile();
        Files.write(source.toPath(),wrote.getBytes());
        File destinationFolder = Files.createTempDirectory("qdup").toFile();
        Download d = new Download(source.getPath());

        AbstractShell localShell = AbstractShell.getShell(
                "return_local_file_path_when_defined_without_destination",
                Host.parse(Host.LOCAL),
                new ScheduledThreadPoolExecutor(2),
                new SecretFilter(),
                null
        );
        Run run = new Run(destinationFolder.getPath(),new RunConfigBuilder().buildConfig(),new Dispatcher());
        ScriptContext scriptContext = new ScriptContext(localShell,run.getConfig().getState(),run,new SystemTimer("download"),d,true);
        SpyContext spyContext = new SpyContext(scriptContext,run.getConfig().getState(), run.getCoordinator());
        d.run("",spyContext);

        assertNotNull("download should call next when download succeeds",spyContext.getNext());

        String result = spyContext.getNext();
        File resultFile = new File(result);

        assertTrue("result file should exist",resultFile.exists());
        assertTrue("download should return path starting with destinationFolder",spyContext.getNext().startsWith(destinationFolder.getPath()));
        assertTrue("download return should end with source name\nreturned "+spyContext.getNext()+"\nsource "+source.getName(),spyContext.getNext().endsWith(source.getName()));

        String read = Files.readString(resultFile.toPath());
        assertEquals(read,wrote);

    }

    @Test
    public void observing_relative_home_alias_and_environment_reference_exit_code() throws IOException {

        Parser parser = Parser.getInstance();
        parser.setAbortOnExitCode(true);
        RunConfigBuilder builder = getBuilder();

        builder.loadYaml(parser.loadFile("pwd",
                """
                scripts:
                  foo:
                   - sh: export NAME=tres
                   - sh: export FOLDER=two
                   - sh: mkdir -p ~/foo/one
                   - sh: mkdir -p /tmp/foo/two
                   - sh: echo 'uno' >> ~/foo/one/uno.txt
                   - sh: echo 'dos' >> /tmp/foo/two/dos.txt
                   - sh: echo 'tres' >> /tmp/foo/two/tres.txt
                   - sh: cd /tmp/foo
                   - sh:
                       command: sleep 4s; (exit 42);
                       ignore-exit-code: true
                     timer:
                       1s:
                         - download: ~/foo/one/uno.txt
                         - download: ./two/dos.txt
                         - download: /tmp/foo/$FOLDER/${NAME}.txt
                   - download: ~/foo/one/uno.txt uno2.txt
                   - download: ./two/dos.txt dos2.txt
                   - download: /tmp/foo/$FOLDER/${NAME}.txt name2.txt
                   - sh: echo $?
                     then:
                     - set-state: RUN.ec
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
        doit.ensureConsoleLogging();
        doit.run();

        Host host = config.getAllHostsInRoles().iterator().next();

        State state = doit.getConfig().getState();

        File uno = new File(tmpDir.toString() + "/"+host.getShortHostName()+"/uno.txt");
        File dos = new File(tmpDir.toString() + "/"+host.getShortHostName()+"/dos.txt");
        File tres = new File(tmpDir.toString() + "/"+host.getShortHostName()+"/tres.txt");

        assertTrue("uno should exist @ "+uno.getPath(),uno.exists());
        assertTrue("dos should exist @ "+dos.getPath(),dos.exists());
        assertTrue("tres should exist @ "+tres.getPath(),tres.exists());

        assertTrue("ec should be set",state.has("ec"));
        assertEquals(42L,state.get("ec"));


        String content = Files.readString(Paths.get(doit.getOutputPath(),"run.json"));
    }
}
