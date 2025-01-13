package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.SshContainerTestBase;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.cmd.Script;
import io.hyperfoil.tools.qdup.cmd.SpyContext;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import org.junit.Test;

import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class JsonCmdTest extends SshContainerTestBase {


    @Test
    public void skip_on_miss(){
        JsonCmd cmd = new JsonCmd("$.foo");
        SpyContext context = new SpyContext();

        cmd.run("miss",context);

        assertTrue(context.hasSkip());
        assertFalse(context.hasNext());
    }

    @Test
    public void match_map_key(){
        JsonCmd cmd = new JsonCmd("$.foo");
        SpyContext context = new SpyContext();

        cmd.run("{\"foo\":\"bar\"}",context);

        assertTrue(context.hasNext());
        assertEquals("bar",context.getNext());
    }
    @Test
    public void match_entry_in_array(){
        JsonCmd cmd = new JsonCmd("$[1]");
        SpyContext context = new SpyContext();
        cmd.run("[\"uno\",{\"foo\":\"bar\"}]",context);
        assertTrue(context.hasNext());
        assertEquals("{\"foo\":\"bar\"}",context.getNext());
    }
    @Test
    public void find_entry_in_array(){
        JsonCmd cmd = new JsonCmd("$[?(@.foo)]");
        SpyContext context = new SpyContext();
        cmd.run("[\"uno\",{\"foo\":\"bar\"}]",context);
        assertTrue(context.hasNext());
        assertEquals("{\"foo\":\"bar\"}",context.getNext());
    }
    @Test
    public void find_entry_in_array_with_regex(){
        JsonCmd cmd = new JsonCmd("$[?(@.foo =~ /^B.*/i)]");
        SpyContext context = new SpyContext();
        cmd.run("[\"uno\",{\"foo\":\"bar\"}]",context);
        assertTrue(context.hasNext());
        assertEquals("{\"foo\":\"bar\"}",context.getNext());
    }
    @Test
    public void find_entry_return_key(){
        JsonCmd cmd = new JsonCmd("$[?(@.foo =~ /^B.*/i)].foo");
        SpyContext context = new SpyContext();
        cmd.run("[\"uno\",{\"foo\":\"bar\"}]",context);
        assertTrue(context.hasNext());
        assertEquals("bar",context.getNext());
    }

    @Test
    public void yaml_string(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("signal", stream("" +
            "scripts:",
            "  foo:",
            "    - json: doSomething",
            "hosts:",
            "  local: " + getHost(),
            "roles:",
            "  doit:",
            "    hosts: [local]",
            "    run-scripts: [foo]"
        )));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
        Script foo = config.getScript("foo");
        assertNotNull(foo);
        Cmd first = foo.getNext();
        assertTrue("expect foo's first command to be json "+first,first instanceof JsonCmd);
        JsonCmd json = (JsonCmd)first;
        assertEquals("correctly set pattern","doSomething",json.getPath());
    }
    @Test
    public void yaml_json(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("signal", stream("" +
                "scripts:",
                "  foo:",
                "    - json:",
                "        path: doSomething",
                "hosts:",
                "  local: " + getHost(),
                "roles:",
                "  doit:",
                "    hosts: [local]",
                "    run-scripts: [foo]"
        )));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
        Script foo = config.getScript("foo");
        assertNotNull(foo);
        Cmd first = foo.getNext();
        assertTrue("expect foo's first command to be json "+first,first instanceof JsonCmd);
        JsonCmd json = (JsonCmd)first;
        assertEquals("correctly set pattern","doSomething",json.getPath());
    }

    @Test
    public void yaml_run_then(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("signal", stream("" +
                "scripts:",
                "  foo:",
                "    - sh: echo '{\"foo\":\"bar\"}'",
                "    - json: \"$.foo\"",
                "      then:",
                "      - set-state: RUN.foo",
                "hosts:",
                "  local: " + getHost(),
                "roles:",
                "  doit:",
                "    hosts: [local]",
                "    run-scripts: [foo]"
        )));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();
        State state = config.getState();
        Object found = state.get("foo");
        assertNotNull("state should contain foo\n"+state.tree(),found);
        assertEquals("bar",found.toString());
    }
    @Test
    public void yaml_run_else(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("signal", stream("" +
                        "scripts:",
                "  foo:",
                "    - sh: echo '{\"foo\":\"bar\"}'",
                "    - json: \"$.NOTfoo\"",
                "      then:",
                "      - set-state: RUN.foo",
                "      else:",
                "      - set-state: RUN.foo missed",
                "hosts:",
                "  local: " + getHost(),
                "roles:",
                "  doit:",
                "    hosts: [local]",
                "    run-scripts: [foo]"
        )));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();
        State state = config.getState();
        Object found = state.get("foo");
        assertNotNull("state should contain foo\n"+state.tree(),found);
        assertEquals("missed",found.toString());
    }
}
