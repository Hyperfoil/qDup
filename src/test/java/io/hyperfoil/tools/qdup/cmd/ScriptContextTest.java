package io.hyperfoil.tools.qdup.cmd;

import io.hyperfoil.tools.qdup.Profiles;
import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.impl.Echo;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import io.hyperfoil.tools.yaup.json.Json;
import org.junit.Test;
import io.hyperfoil.tools.qdup.SshTestBase;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ScriptContextTest extends SshTestBase {

    private class UpdateCmd extends Cmd {
        private final int count;
        UpdateCmd(int count){
            this.count = count;
        }

        @Override
        public void run(String input, Context context) {
            for(int i=0; i<count; i++){
                context.update(""+i);
            }
            context.next(""+count);
        }

        @Override
        public Cmd copy() {
            return new UpdateCmd(this.count);
        }
    }

    private ScriptContext getContext(Cmd command){

        RunConfigBuilder builder = getBuilder();
        RunConfig runConfig = builder.buildConfig(Parser.getInstance());
        Run run = new Run(
                "/tmp/",
            runConfig,
            new Dispatcher()

        );

        ScriptContext context = new ScriptContext(
            getSession(),
            new State(""),
            run,
            new Profiles().get("ScriptContextTest"),
            command,
            false
        );
        return context;
    }

    @Test
    public void invoke_single_watcher(){
        Cmd toRun = new UpdateCmd(5);
        List<String> lines = new LinkedList<>();
        toRun.watch(Cmd.code((input, state) -> {
            lines.add(input);
            return Result.next(input);
        }));
        ScriptContext context = getContext(toRun);
        context.run();
        try {
            //Wait for the executor to finish
            Thread.sleep(5_000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assertEquals("expect 5 lines of output:"+lines,5,lines.size());
    }
    @Test
    public void invoke_watcher_chain(){
        Cmd toRun = new UpdateCmd(5);
        AtomicBoolean sawTwo = new AtomicBoolean(false);
        AtomicBoolean calledChild = new AtomicBoolean(false);
        toRun.watch(Cmd.code((input,state)->{
            if("2".equals(input)){
                sawTwo.set(true);
                return Result.next(input);
            }else{
                return Result.skip(input);
            }
        }).then(Cmd.code((input,state)->{
            calledChild.set(true);
            return Result.next(input);
        })));

        ScriptContext context = getContext(toRun);

        context.run();

        assertTrue("expect to see 2",sawTwo.get());
        assertTrue("expect to invoke child of sawTwo",calledChild.get());
    }

    @Test
    public void invoke_multiple_timeout(){
        int timeout = 1_000;
        int mult = 5;
        Cmd toRun = Cmd.code((line,state)->{
            try {
                //Sleep in command to ensure timeout is not blocked by blocking tasks
                Thread.sleep(mult*timeout);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return Result.next(line);
        });
        AtomicLong timeA = new AtomicLong(0);
        AtomicBoolean calledA = new AtomicBoolean(false);
        toRun.addTimer(timeout, Cmd.code((line,state)->{
            calledA.set(true);
            timeA.set(System.currentTimeMillis());
            return Result.next(line);
        }));
        AtomicLong timeB = new AtomicLong(0);
        AtomicBoolean calledB = new AtomicBoolean(false);
        toRun.addTimer(2*timeout, Cmd.code((line,state)->{
            calledB.set(true);
            timeB.set(System.currentTimeMillis());
            return Result.next(line);
        }));

        ScriptContext context = getContext(toRun);

        context.run();
        assertTrue("A timer should be called",calledA.get());
        assertTrue("B timer should be called",calledB.get());
        assertTrue("A should be called before B but A="+timeA.get()+" and B="+timeB.get(),timeA.get() < timeB.get());

    }
    @Test
    public void invoke_multiple_watcher(){
        int count=5;
        Cmd toRun = new UpdateCmd(count);
        List<String> lines = new LinkedList<>();
        toRun.watch(Cmd.code((input, state) -> {
            lines.add("first="+input);
            return Result.skip(input);
            //skip should not prevent other watchers
        }));
        toRun.watch(Cmd.code((input, state) -> {
            lines.add("second="+input);
            return Result.skip(input);
        }));
        toRun.watch(Cmd.code((input, state) -> {
            lines.add("third="+input);
            return Result.next(input);
        }));
        ScriptContext context = getContext(toRun);
        context.run();
        assertEquals("expect 3 watchers to see all "+count+" lines",3*count,lines.size());
        List<String> prefixes = Arrays.asList("first","second","third");
        for(int i=0; i<3*count; i++){
            String prefix = prefixes.get(i%3);
            assertTrue("expect "+i+" output to come from "+prefix,lines.get(i).startsWith(prefix));
        }
    }

    @Test
    public void observer_update(){
        int count=5;
        Cmd toRun = new UpdateCmd(count);
        ScriptContext context = getContext(toRun);
        List<String> updates = new LinkedList<>();
        context.setObserver(new ContextObserver() {
            @Override
            public void onUpdate(Context context, Cmd command, String output) {
                updates.add(output);
            }
        });
        context.run();
        assertEquals("expect "+count+" updates",count,updates.size());
    }

    @Test
    public void validate_context_scratch_path(){
        Cmd toRun = new Echo();
        ScriptContext context = getContext(toRun);
        context.run();
        File scratchDir = context.getScratchDir("test");
        assertNotNull("Scratch path should not be null", scratchDir);
        assertTrue("Scratch path should exist",scratchDir.exists());
    }

    @Test
    public void validate_script_scratch_file_manipulation(){
        //create the scratchDir
        File scratchDir = new File(tmpDir.getPath().toAbsolutePath().toString()+"-scratch");
        scratchDir.mkdirs();

        Parser parser = Parser.getInstance();
        ScratchDirManipCmd.extendParser(parser);
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("signal", stream("" +
                "scripts:",
                "  scratch-file-test:",
                "    - scratch-file-write:",
                "        fileName: test.scratch.out",
                "        contents: This is a test",
                "hosts:",
                "  local: " + getHost(),
                "roles:",
                "  doit:",
                "    hosts: [local]",
                "    run-scripts: [scratch-file-test]"
        )));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();

        File scratchFile = new File(scratchDir.getAbsolutePath() + File.separator + "test.scratch.out");
        assertTrue("Scratch File should exist", scratchFile.exists());

        try {
            String fileContents = new String(Files.readAllBytes(scratchFile.toPath()), StandardCharsets.UTF_8);
            assertEquals("This is a test", fileContents);

        } catch (IOException e) {
            fail("Error reading scratch file contents");
        }

    }

    public static class ScratchDirManipCmd extends Cmd {

        private String fileName;
        private String contents;

        public ScratchDirManipCmd(String fileName, String contents) {
            this.fileName = fileName;
            this.contents = contents;
        }

        @Override
        public void run(String input, Context context) {
            File scratchFile = context.getScratchFile(this.fileName);
            if ( scratchFile != null ) {
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(scratchFile))) {
                    writer.write(this.contents);
                    context.next(input);
                } catch (IOException e) {
                    context.error("Could not create new scratch file: " + scratchFile.getAbsolutePath());
                    context.abort(false);
                }
            } else {
                context.error("Could not create new scratch file: " + scratchFile.getAbsolutePath());
                context.abort(false);
            }
        }

        @Override
        public Cmd copy() {
            return new ScratchDirManipCmd(this.fileName, this.contents);
        }

        @Override
        public String toString() {
            return "scratch-file-write";
        }

        @Override
        public String getLogOutput(String output, Context context) {
            return "scratch-file-write: " + this.fileName;
        }

        public String getFileName() {
            return fileName;
        }

        public String getContents() {
            return contents;
        }

        public static void extendParser(Parser parser){
            parser.addCmd(ScratchDirManipCmd.class,
                    "scratch-file-write",
                    (cmd) -> {
                        LinkedHashMap<Object, Object> map = new LinkedHashMap<>();
                        LinkedHashMap<Object, Object> opts = new LinkedHashMap<>();
                        map.put("scratch-file-write", opts);
                        opts.put("fileName", cmd.getFileName());
                        opts.put("contents", cmd.getContents());
                        return map;
                    },
                    (str, prefix, suffix) -> {
                        if (str == null || str.isEmpty()) {
                            throw new YAMLException("scratch-file-write command cannot be empty");
                        }
                        String[] split = str.split(" ");
                        if (split.length != 2) {
                            throw new YAMLException("scratch-file-write command expecting 2 params");
                        }
                        return new ScratchDirManipCmd(split[0], split[1]);
                    },
                    (json) -> {
                        validateNonEmptyValue(json, "fileName");
                        validateNonEmptyValue(json, "contents");
                return new ScratchDirManipCmd(json.getString("fileName"), json.getString("contents"));
                },
                    "fileName", "contents");
        }
    }

    public static void validateNonEmptyValue(Json json, String key) throws YAMLException {
        if (!json.has(key) || json.getString(key, "").isEmpty()) {
            throw new YAMLException("git-bisect-init requires a non-empty " + key + " ");
        }
    }
}