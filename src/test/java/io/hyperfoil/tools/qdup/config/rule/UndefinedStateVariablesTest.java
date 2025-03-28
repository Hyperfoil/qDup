package io.hyperfoil.tools.qdup.config.rule;

import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.SshTestBase;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.cmd.PatternValuesMap;
import io.hyperfoil.tools.qdup.cmd.Script;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.RunSummary;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class UndefinedStateVariablesTest {

    public static InputStream stream(String...input){
        return new ByteArrayInputStream(
                String.join("\n", Arrays.asList(input)).getBytes()
        );
    }
    public RunConfigBuilder getBuilder(){
        return new RunConfigBuilder();
    }
    @Test
    public void script_with_at_runtime(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                scripts:
                  set:
                    - set-state: RUN.bar buz
                    - set-state: RUN.buz.biz biz
                  sig:
                    - set-state: RUN.sig ${{foo}}
                hosts:
                  test: fakeUser@fakeHost
                roles:
                  role:
                    hosts: [test]
                    setup-scripts:
                    - set
                    run-scripts:
                    - sig:
                        with:
                          foo: ${{${{bar}}.biz}}
                """
        ));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("config should not have errors: "+config.getErrorStrings().stream().collect(Collectors.joining("\n")),config.hasErrors());
        RunSummary summary = new RunSummary();
        UndefinedStateVariables rule = new UndefinedStateVariables(parser);
        summary.addRule("state",rule);
        summary.scan(config.getRolesValues(),builder);
        assertFalse("unexpected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
    }
    @Test
    public void script_missing_with_at_runtime(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                scripts:
                  set:
                    - set-state: RUN.biz buz
                    - set-state: RUN.buz.biz biz
                  sig:
                    - set-state: RUN.sig ${{foo}}
                hosts:
                  test: fakeUser@fakeHost
                roles:
                  role:
                    hosts: [test]
                    setup-scripts:
                    - set
                    run-scripts:
                    - sig:
                        with:
                          foo: ${{${{bar}}.biz}}
                """
        ));
        RunConfig config = builder.buildConfig(parser);
        assertTrue("config should have errors: "+config.getErrorStrings().stream().collect(Collectors.joining("\n")),config.hasErrors());
        RunSummary summary = new RunSummary();
        UndefinedStateVariables rule = new UndefinedStateVariables(parser);
        summary.addRule("state",rule);
        summary.scan(config.getRolesValues(),builder);
        assertTrue("expected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        List<String> missing = rule.getMissingVariables();
        assertEquals("missing should have 2 entries",2,missing.size());
        assertTrue("missing should include 'bar'",missing.contains("bar"));
        assertTrue("missing should include '${{bar}}.biz'",missing.contains("${{bar}}.biz"));
    }
    @Test
    public void from_separate_file_state_jsonpath_reference_constant_with_minus(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                scripts:
                  test:
                  - sh: echo ${{hosts}}
                hosts:
                  local: me@localhost
                roles:
                  role:
                    hosts: [local]
                    run-scripts:
                    - test
                states:
                  from_state: in_state
                """
        ));
        builder.loadYaml(parser.loadFile("state",
                """
                states:
                  hosts:
                  - name: foo-bar
                    ip: "127.0.0.1"
                  - name: bar
                    ip: "0.0.0.0"
                """
        ));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("config should not have errors: "+config.getErrorStrings().stream().collect(Collectors.joining("\n")),config.hasErrors());
        RunSummary summary = new RunSummary();
        UndefinedStateVariables rule = new UndefinedStateVariables(parser);
        summary.addRule("state",rule);
        summary.scan(config.getRolesValues(),builder);
        assertFalse("unexpected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
    }

    @Test
    public void value_set_as_path_used_as_parent(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                scripts:
                  test:
                    - for-each:
                        name: FOO
                        input:
                          - { name: "one", pattern: "uno"}
                          - { name: "two", pattern: "dos"}
                      then:
                      - sh: echo string
                        then:
                        - set-state: RUN.results.${{FOO.name}} ${{FOO.pattern}}
                    - sh: echo ${{RUN.results}}
                hosts:
                  local: me@localhost
                roles:
                  role:
                    hosts: [local]
                    run-scripts:
                    - test
                states:
                  from_state: in_state
                """
        ));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("config should not have errors: "+config.getErrorStrings().stream().collect(Collectors.joining("\n")),config.hasErrors());
        RunSummary summary = new RunSummary();
        UndefinedStateVariables rule = new UndefinedStateVariables(parser);
        summary.addRule("state",rule);
        summary.scan(config.getRolesValues(),builder);
        assertFalse("unexpected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
    }

    @Test
    public void value_from_foreach_in_regex_disable_statescan(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """                
                scripts:
                  test:
                    - for-each:
                        name: FOO
                        input:
                          - { name: "one", pattern: "uno"}
                          - { name: "two", pattern: "dos"}
                      then:
                      - sh: echo string
                        then:
                        - regex: ^(?<${{FOO.name}}.dir>.*)
                    - sh: echo ${{one.dir}}
                      state-scan: false
                    - sh: echo ${{two.dir}}
                      state-scan: false
                hosts:
                  local: me@localhost
                roles:
                  role:
                    hosts: [local]
                    run-scripts:
                    - test:
                        with:
                          from_with: in_with
                states:
                  from_state: in_state
                """
        ));
        RunConfig config = builder.buildConfig(parser);
        RunSummary summary = new RunSummary();
        UndefinedStateVariables rule = new UndefinedStateVariables(parser);
        summary.addRule("state",rule);
        summary.scan(config.getRolesValues(),builder);
        assertFalse("unexpected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
    }

    @Test
    public void value_from_foreach(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                scripts:
                  test:
                    - for-each:
                        name: FOO
                        input: [{ name: "hibernate", pattern: "hibernate-core*jar" }, { name: "logging", pattern: "jboss-logging*jar" }]
                     then:
                      - set-state: RUN.BAR ${{RUN.BAR:}}-${{FOO.name}}
                hosts:
                  local: me@localhost
                roles:
                  role:
                    hosts: [local]
                    run-scripts:
                    - test:
                        with:
                          from_with: in_with
                states:
                  from_state: in_state
                """
        ));
        RunConfig config = builder.buildConfig(parser);
        RunSummary summary = new RunSummary();
        UndefinedStateVariables rule = new UndefinedStateVariables(parser);
        summary.addRule("state",rule);
        summary.scan(config.getRolesValues(),builder);
        assertFalse("unexpected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
    }

    @Test
    public void qdup_timestamps_not_cause_error(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                "scripts:
                  use:
                    - sh: echo ${{QDUP_GLOBAL.state.foo.bar}}
                hosts:
                  local: me@localhost
                roles:
                  role:
                    hosts: [local]
                    run-scripts:
                    - use
                """.replaceAll("QDUP_GLOBAL",PatternValuesMap.QDUP_GLOBAL)
        ));
        RunConfig config = builder.buildConfig(parser);
        RunSummary summary = new RunSummary();
        UndefinedStateVariables rule = new UndefinedStateVariables(parser);
        summary.addRule("state",rule);
        summary.scan(config.getRolesValues(),builder);
        assertFalse("expected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
    }

    @Test
    public void read_state_not_cause_error(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                scripts:
                  use:
                    - read-state: ${{never-set}}
                hosts:
                  local: me@localhost
                roles:
                  role:
                    hosts: [local]
                    run-scripts:
                    - use
                """
        ));
        RunConfig config = builder.buildConfig(parser);
        RunSummary summary = new RunSummary();
        UndefinedStateVariables rule = new UndefinedStateVariables(parser);
        summary.addRule("state",rule);
        summary.scan(config.getRolesValues(),builder);
        assertFalse("expected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
    }

    @Test
    public void error_set_after_used_separate_phase(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                scripts:
                  set:
                    - set-state: later_phase wrong_phase
                  use:
                    - sh: ${{later_phase}}
                hosts:
                  local: me@localhost
                roles:
                  role:
                    hosts: [local]
                    run-scripts:
                    - use
                    cleanup-scripts:
                    - set
                """
        ));
        RunConfig config = builder.buildConfig(parser);
        RunSummary summary = new RunSummary();
        UndefinedStateVariables rule = new UndefinedStateVariables(parser);
        summary.addRule("state",rule);
        summary.scan(config.getRolesValues(),builder);
        assertTrue("expected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
    }

    @Test
    public void error_set_after_used_sequential_phase(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                scripts:
                  set:
                    - set-state: later_phase wrong_phase
                  use:
                    - sh: ${{later_phase}}
                hosts:
                  local: me@localhost
                roles:
                  role:
                    hosts: [local]
                    setup-scripts:
                    - use
                    - set
                """
        ));
        RunConfig config = builder.buildConfig(parser);
        RunSummary summary = new RunSummary();
        UndefinedStateVariables rule = new UndefinedStateVariables(parser);
        summary.addRule("state",rule);
        summary.scan(config.getRolesValues(),builder);
        assertTrue("expected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        assertEquals("unexpected number of variables: "+rule.getUsedVariables(),1,rule.getUsedVariables().size());

    }
    @Test
    public void error_missing_referenced_state_value() {
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("ctrlC",
                """
                scripts:
                  foo:
                    - sh: echo HI
                      then:
                      - sh: echo ${{BAR}}
                hosts:
                  local: fakeUser@fakeHost
                roles:
                  doit:
                    hosts: [local]
                    setup-scripts: [foo]
                states:
                  BAR: ${{BIZ}}
                  BIZx:
                """
        ));
        RunConfig config = builder.buildConfig(parser);

        RunSummary summary = new RunSummary();
        UndefinedStateVariables rule = new UndefinedStateVariables(parser);
        summary.addRule("state",rule);
        summary.scan(config.getRolesValues(),builder);

        assertEquals("unexpected number of variables: "+rule.getUsedVariables(),2,rule.getUsedVariables().size());

        assertTrue("expected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());

        assertTrue("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
        assertEquals("expect 1 errors:\n"+ config.getErrorStrings().stream().collect(Collectors.joining("\n")),1,config.getErrors().size());
    }

    @Test
    public void error_set_after_used_same_script(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
            """
            scripts:
              test:
                - sh: ${{explicit}}
                - set-state: explicit in_script
            hosts:
              local: me@localhost
            roles:
              role:
                hosts: [local]
                run-scripts:
                - test
            """
        ));
        RunConfig config = builder.buildConfig(parser);
        RunSummary summary = new RunSummary();
        UndefinedStateVariables rule = new UndefinedStateVariables(parser);
        summary.addRule("state",rule);
        summary.scan(config.getRolesValues(),builder);
        assertTrue("expected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        assertEquals("unexpected number of variables: "+rule.getUsedVariables(),1,rule.getUsedVariables().size());
    }

    @Test
    public void setstate_with_default(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                scripts:
                  invoke:
                  - script: test
                    with:
                      name: foo
                  test:
                    - read-state: ${{name}}
                      then:
                      - regex: .*
                        then:
                        - set-state: RUN.RUNTIME_NAME ${{name}}_${{suffix:2010}}
                hosts:
                  local: me@localhost
                roles:
                  role:
                    hosts: [local]
                    setup-scripts:
                    - invoke:
                """
        ));
        RunConfig config = builder.buildConfig(parser);
        RunSummary summary = new RunSummary();
        UndefinedStateVariables rule = new UndefinedStateVariables(parser);
        summary.addRule("state",rule);
        summary.scan(config.getRolesValues(),builder);
        assertFalse("unexpected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
    }

    @Test
    public void use_default_twice_for_same_key(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("test",
                """
                scripts:
                  test:
                  - sh: ./profiler.sh -d 10 -b 2097152 -f /tmp/flamegraph.${{PID:jps}}.svg --title ${{TITLE}} --width 1900 ${{PID:jps}}
                """
        ));
        builder.loadYaml(parser.loadFile("signal",
                """
                hosts:
                  local: me@localhost
                roles:
                  role:
                    hosts: [local]
                    setup-scripts:
                    - test:
                        with:
                          TITLE: foo
                          PID: $(jps -v | grep jboss-modules | cut -d \" \" -f1)
                """
        ));
        RunConfig config = builder.buildConfig(parser);
        RunSummary summary = new RunSummary();
        UndefinedStateVariables rule = new UndefinedStateVariables(parser);
        summary.addRule("state",rule);
        summary.scan(config.getRolesValues(),builder);
        Script script = config.getScript("test");

        assertFalse("unexpected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());

    }

    @Test
    public void value_from_setstate(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                scripts:
                  test:
                    - set-state: explicit in_script
                    - set-state: from_pattern ${{explicit}}
                    - set-state: from_pattern_default ${{missing:in_default}}
                    - set-state: from_pattern_with ${{from_with}}
                    - set-state: from_pattern_with ${{from_with}}_${{missing:in_default}}
                    - set-state: from_pattern_state ${{from_state}}
                    - sh: ${{explicit}}
                    - sh: ${{from_pattern}}
                    - sh: ${{from_pattern_default}}
                    - sh: ${{from_pattern_with}}
                    - sh: ${{from_pattern_state}}
                hosts:
                  local: me@localhost
                roles:
                  role:
                    hosts: [local]
                    run-scripts:
                    - test:
                        with:
                          from_with: in_with
                states:
                  from_state: in_state
                """
        ));
        RunConfig config = builder.buildConfig(parser);
        RunSummary summary = new RunSummary();
        UndefinedStateVariables rule = new UndefinedStateVariables(parser);
        summary.addRule("state",rule);
        summary.scan(config.getRolesValues(),builder);
        assertFalse("unexpected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        assertEquals("unexpected number of variables: "+rule.getUsedVariables(),8,rule.getUsedVariables().size());
    }

    @Test
    public void value_from_regex(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                scripts:
                  test:
                    - regex: (?<RUN.with_run_prefix>\\d+) (?<HOST.with_host_prefix>\\d+) (?<without_prefix>\\d+)
                    - sh: ${{with_run_prefix}}
                    - sh: ${{with_host_prefix}}
                    - sh: ${{without_prefix}}
                hosts:
                  local: me@localhost
                roles:
                  role:
                    hosts: [local]
                    run-scripts:
                    - test:
                """
        ));
        RunConfig config = builder.buildConfig(parser);
        RunSummary summary = new RunSummary();
        UndefinedStateVariables rule = new UndefinedStateVariables(parser);
        summary.addRule("state",rule);
        summary.scan(config.getRolesValues(),builder);
        assertFalse("unexpected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        assertEquals("unexpected number of variables: "+rule.getUsedVariables(),3,rule.getUsedVariables().size());
    }

    @Test
    public void valid_patterns(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                scripts:
                  test:
                    - log: ${{from_with}}
                    - sh: ${{from_state}}
                    - sh: ${{undefined_with_default:defaultValue}}
                    - sh: ${{from_with}}_${{undefined_with_default:defaultValue}}
                    - sh: ${{undefined_with_empty_default:}}
                    - sh: ${{RUN.run_undefined_with_empty_default:}}
                    - sh: ${{stateJson.key}}
                    - sh: ${{withJson.key}}
                hosts:
                  local: me@localhost
                roles:
                  role:
                    hosts: [local]
                    run-scripts:
                    - test:
                        with: { from_with: alpha, withJson: { key: value } }
                states:
                  from_state: bravo
                  stateJson:
                    key: value
                """
        ));
        RunConfig config = builder.buildConfig(parser);
        RunSummary summary = new RunSummary();
        UndefinedStateVariables rule = new UndefinedStateVariables(parser);
        summary.addRule("state",rule);
        summary.scan(config.getRolesValues(),builder);

        assertFalse("unexpected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        assertEquals("unexpected number of variables: "+rule.getUsedVariables(),7,rule.getUsedVariables().size());
    }

    @Test
    public void valid_default_value_in_subscript(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                scripts:
                  create-runtime-name: #PATH > RUN.RUNTIME_NAME, RUN.ARCHIVE_NAME
                    - read-state: ${{PATH}}
                      then:
                      - regex: .*?\\/(?<name>[^/]+?)\\.(?<type>zip|tar.gz|tgz)
                        then:
                        - set-state: RUN.RUNTIME_NAME ${{name}}_${{suffix:2010}}
                        - set-state: RUN.ARCHIVE_NAME ${{name}}.${{type}}
                  test:
                    - script: create-runtime-name
                      with:
                        PATH: foo/bar.tar.gz
                hosts:
                  local: me@localhost
                roles:
                  role:
                    hosts: [local]
                    run-scripts:
                    - test:
                states:
                  defined: foo
                  from_state: bar
                """
        ));
        RunConfig config = builder.buildConfig(parser);
        RunSummary summary = new RunSummary();
        UndefinedStateVariables rule = new UndefinedStateVariables(parser);
        summary.addRule("state",rule);
        summary.scan(config.getRolesValues(),builder);

        assertFalse("unexpected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
    }
}
