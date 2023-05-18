package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.SshTestBase;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import org.junit.Test;

import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class AbortTest extends SshTestBase {


    @Test
    public void abort_finishes_cleanup(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("",stream(""+
                "scripts:",
                "  foo:",
                "  - log: running foo",
                "  - abort: foo",
                "  - set-state: RUN.foo true",
                "  bar:",
                "  - log: running bar",
                "  - sleep: 10s", //to make sure foo calls abort before we progress
                "  - log: ran bar",
                "  - set-state: RUN.bar true",
                "hosts:",
                "  local: " + getHost(),
                "roles:",
                "  doit:",
                "    hosts: [local]",
                "    run-scripts:",
                "      - foo",
                "    cleanup-scripts:",
                "      - bar",
                "states:",
                "  bar: false",
                "  foo: false"
        )));
        RunConfig config = builder.buildConfig(parser);

        assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());

        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();

        String logContents = readFile(tmpDir.getPath().resolve("run.log"));

        State state = config.getState();

        assertEquals("abort should still call cleanup script","true",config.getState().get("bar"));
        assertEquals("abort should stop current script","false",config.getState().get("foo"));
    }

    @Test
    public void abort_run_and_cleanup(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("",stream(""+
                "scripts:",
                "  foo:",
                "  - log: running foo",
                "  - abort: foo",
                "  - set-state: RUN.foo true",
                "  bar:",
                "  - log: running bar",
                "  - sleep: 10s", //to make sure foo calls abort before we progress
                "  - log: ran bar",
                "  - set-state: RUN.bar true",
                "  - abort: bar",
                "  - set-state: RUN.barbar true",
                "hosts:",
                "  local: " + getHost(),
                "roles:",
                "  doit:",
                "    hosts: [local]",
                "    run-scripts:",
                "      - foo",
                "    cleanup-scripts:",
                "      - bar",
                "states:",
                "  bar: false",
                "  barbar: false",
                "  foo: false"


        )));
        RunConfig config = builder.buildConfig(parser);

        assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());

        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();

        String logContents = readFile(tmpDir.getPath().resolve("run.log"));

        State state = config.getState();

        assertEquals("abort should still call cleanup script","true",config.getState().get("bar"));
        assertEquals("abort should stop current cleanup script","false",config.getState().get("barbar"));
        assertEquals("abort should stop current run script","false",config.getState().get("foo"));
    }
}
