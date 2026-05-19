package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.*;
import io.hyperfoil.tools.qdup.cmd.*;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import io.hyperfoil.tools.qdup.shell.AbstractShell;
import io.hyperfoil.tools.yaup.time.SystemTimer;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
    public void upload_folder() throws IOException {
        Path dir = Files.createTempDirectory("qdup.upload_without_desitnation");
        File source = Files.createTempFile(dir,"qdup.",".upload.txt").toFile();
        Files.write(source.toPath(),"bizbuz".getBytes());
        File second = Files.createTempFile(dir,"qdup.",".upload.txt").toFile();
        Files.write(second.toPath(),"bizbuz".getBytes());

        Parser parser = Parser.getInstance();
        parser.setAbortOnExitCode(true);
        RunConfigBuilder builder = getBuilder();

        builder.loadYaml(parser.loadFile("pwd",
                """
                scripts:
                  foo:
                   - sh: export FOLDER=foo
                   - sh: cd /tmp
                   - upload: SRC ./foo
                   - sh: find ./foo/ -name "*"
                hosts:
                  local: TARGET_HOST
                roles:
                  doit:
                    hosts: [local]
                    run-scripts: [foo]
                """.replaceAll("TARGET_HOST",getHost().toString())
                        .replaceAll("SRC",dir.toAbsolutePath().toString()+"/")
        ));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.ensureConsoleLogging();
        doit.run();

        List.of("/tmp/foo/"+source.getName(),"/tmp/foo/"+second.getName()).forEach(path->{
            assertTrue(path+" should exist",exists(path));
            String read = readFile(path);
            assertEquals("bizbuz",read);
        });
    }


    @Test
    public void return_remote_path_new_name() throws IOException {
        String wrote = "bizbuz";
        File source = Files.createTempFile("qdup","upload.txt").toFile();
        Files.write(source.toPath(),wrote.getBytes());
        File runFolder = Files.createTempDirectory("qdup").toFile();

        Upload upload = new Upload(source.getPath(),"/tmp/found.txt");

        AbstractShell shell = AbstractShell.getShell(
                "return_remote_path_new_name",
                Host.parse(Host.LOCAL),
                new ScheduledThreadPoolExecutor(2),
                new SecretFilter(),
                null
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
            "return_remote_path_target_folder",
            Host.parse(Host.LOCAL),
            new ScheduledThreadPoolExecutor(2),
            new SecretFilter(),
            null
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
                "return_remote_path_target_file",
            Host.parse(Host.LOCAL),
            new ScheduledThreadPoolExecutor(2),
            new SecretFilter(),
            null
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

    @Test
    public void upload_resolve_path() throws IOException {
        String wrote = "bizbuz";
        File source = Files.createTempFile("qdup.",".upload.txt").toFile();
        Files.write(source.toPath(),wrote.getBytes());

        Parser parser = Parser.getInstance();
        parser.setAbortOnExitCode(true);
        RunConfigBuilder builder = getBuilder();

        builder.loadYaml(parser.loadFile("pwd",
                """
                scripts:
                  foo:
                   - sh: export FOLDER=foo
                   - sh: cd /tmp
                   - upload: SRC ./foo/
                   - upload: SRC ~/bar
                   - upload: SRC ./${FOLDER}/$FOLDER/
                hosts:
                  local: TARGET_HOST
                roles:
                  doit:
                    hosts: [local]
                    run-scripts: [foo]
                """.replaceAll("TARGET_HOST",getHost().toString())
                        .replaceAll("SRC",source.getPath())
        ));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.ensureConsoleLogging();
        doit.run();

        List.of("~/bar","/tmp/foo/"+source.getName(),"/tmp/foo/foo/"+source.getName()).forEach(path->{
            assertTrue(path+" should exist",exists(path));
            String read = readFile(path);
            assertEquals(wrote,read);
        });
    }
    @Test
    public void upload_defer_resolve_path() throws IOException {
        String wrote = "bizbuz";
        File source = Files.createTempFile("qdup.",".upload.txt").toFile();
        Files.write(source.toPath(),wrote.getBytes());

        Parser parser = Parser.getInstance();
        parser.setAbortOnExitCode(true);
        RunConfigBuilder builder = getBuilder();

        builder.loadYaml(parser.loadFile("pwd",
                """
                scripts:
                  foo:
                   - sh: export FOLDER=foo
                   - sh: cd /tmp
                   - sh: sleep 4s
                     timer:
                       1s:
                       - upload: SRC ./foo/
                       - upload: SRC ~/bar
                       - upload: SRC ./${FOLDER}/$FOLDER/
                hosts:
                  local: TARGET_HOST
                roles:
                  doit:
                    hosts: [local]
                    run-scripts: [foo]
                """.replaceAll("TARGET_HOST",getHost().toString())
                        .replaceAll("SRC",source.getPath())
        ));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.ensureConsoleLogging();
        doit.run();

        List.of("~/bar","/tmp/foo/"+source.getName(),"/tmp/foo/foo/"+source.getName()).forEach(path->{
            assertTrue(path+" should exist",exists(path));
            String read = readFile(path);
            assertEquals(wrote,read);
        });
    }

    @Test
    public void observing_relative_home_alias_and_environment_reference_exit_code() throws IOException {

        Parser parser = Parser.getInstance();
        parser.setAbortOnExitCode(true);
        RunConfigBuilder builder = getBuilder();

        File localTmp = File.createTempFile("qdup.",".upload.txt");
        Files.write(localTmp.toPath(),"local".getBytes());
        localTmp.deleteOnExit();

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
                         - upload: LOCAL_PATH ~/foo/one/uno.txt
                         - upload: LOCAL_PATH ./two/dos.txt
                         - upload: LOCAL_PATH /tmp/foo/$FOLDER/${NAME}.txt
                   - upload: LOCAL_PATH ~/foo/one/uno2.txt
                   - upload: LOCAL_PATH ./two/dos2.txt
                   - upload: LOCAL_PATH /tmp/foo/$FOLDER/${NAME}2.txt
                   - sh: echo $?
                     then:
                     - set-state: RUN.ec
                   - sh: test -f ~/foo/one/uno.txt
                   - sh: test -f ./two/dos.txt
                   - sh: test -f /tmp/foo/$FOLDER/${NAME}.txt
                   - sh: test -f ~/foo/one/uno2.txt
                   - sh: test -f ./two/dos2.txt
                   - sh: test -f /tmp/foo/$FOLDER/${NAME}2.txt
                hosts:
                  local: TARGET_HOST
                roles:
                  doit:
                    hosts: [local]
                    run-scripts: [foo]
                """.replaceAll("TARGET_HOST",getHost().toString())
                   .replaceAll("LOCAL_PATH",localTmp.getAbsolutePath().toString()
               )
        ));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.ensureConsoleLogging();
        doit.run();

        Host host = config.getAllHostsInRoles().iterator().next();

        State state = doit.getConfig().getState();

        assertTrue("ec should be set",state.has("ec"));
        assertEquals(42L,state.get("ec"));
    }


}
