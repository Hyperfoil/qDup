package io.hyperfoil.tools.qdup.config.rule;

import io.hyperfoil.tools.qdup.SshTestBase;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.RunSummary;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import org.junit.Test;

import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;

public class NonObservingCommandsTest extends SshTestBase {

    @Test
    public void sh_in_script_in_watch() {
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("test", stream("" +
            "scripts:",
            "  doit:",
            "    - sh: echo bad",
            "  test:",
            "    - sh: echo foo",
            "      watch:",
            "      - script: ${{FOO}}",
            "hosts:",
            "  local: me@localhost",
            "roles:",
            "  role:",
            "    hosts: [local]",
            "    run-scripts:",
            "    - test",
            "states:",
            "  FOO: doit"
        )));
        RunConfig config = builder.buildConfig(parser);

        RunSummary summary = new RunSummary();
        NonObservingCommands rule = new NonObservingCommands();
        summary.addRule("observer",rule);
        summary.scan(config.getRoles(), builder);
        assertTrue("expect errors:\n" + summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")), summary.hasErrors());
    }


        @Test
        public void sh_in_onsignal () {
            Parser parser = Parser.getInstance();
            RunConfigBuilder builder = new RunConfigBuilder();
            builder.loadYaml(parser.loadFile("test", stream("" +
                            "scripts:",
                    "  test:",
                    "    - sh: echo foo",
                    "      on-signal:",
                    "        foo:",
                    "        - sh: echo bad",
                    "hosts:",
                    "  local: me@localhost",
                    "roles:",
                    "  role:",
                    "    hosts: [local]",
                    "    run-scripts:",
                    "    - test"
            )));
            RunConfig config = builder.buildConfig(parser);

            RunSummary summary = new RunSummary();
            NonObservingCommands rule = new NonObservingCommands();
            summary.addRule("observer",rule);
            summary.scan(config.getRoles(), builder);
            assertTrue("expect errors:\n" + summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")), summary.hasErrors());
        }

        @Test
        public void sh_in_timer () {
            Parser parser = Parser.getInstance();
            RunConfigBuilder builder = new RunConfigBuilder();
            builder.loadYaml(parser.loadFile("test", stream("" +
                            "scripts:",
                    "  test:",
                    "    - sh: echo foo",
                    "      timer:",
                    "        10s:",
                    "        - sh: echo bad",
                    "hosts:",
                    "  local: me@localhost",
                    "roles:",
                    "  role:",
                    "    hosts: [local]",
                    "    run-scripts:",
                    "    - test"
            )));
            RunConfig config = builder.buildConfig(parser);

            RunSummary summary = new RunSummary();
            NonObservingCommands rule = new NonObservingCommands();
            summary.addRule("observer",rule);
            summary.scan(config.getRoles(), builder);
            assertTrue("expect errors:\n" + summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")), summary.hasErrors());
        }
        @Test
        public void sh_in_watch () {
            Parser parser = Parser.getInstance();
            RunConfigBuilder builder = new RunConfigBuilder();
            builder.loadYaml(parser.loadFile("test", stream("" +
                            "scripts:",
                    "  test:",
                    "    - sh: echo foo",
                    "      watch:",
                    "      - sh: echo bad",
                    "hosts:",
                    "  local: me@localhost",
                    "roles:",
                    "  role:",
                    "    hosts: [local]",
                    "    run-scripts:",
                    "    - test"
            )));
            RunConfig config = builder.buildConfig(parser);

            RunSummary summary = new RunSummary();
            NonObservingCommands rule = new NonObservingCommands();
            summary.addRule("observer",rule);
            summary.scan(config.getRoles(), builder);
            assertTrue("expect errors:\n" + summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")), summary.hasErrors());
        }
    }
