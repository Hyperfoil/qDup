package io.hyperfoil.tools.qdup.config.rule;

import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.SshTestBase;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.cmd.Result;
import io.hyperfoil.tools.qdup.cmd.Script;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.RunSummary;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class SignalCountsTest extends SshTestBase {

    @Test
    public void issue_from_runtest(){
        StringBuilder fooOutput = new StringBuilder();
        StringBuilder barOutput = new StringBuilder();

        Script tail = new Script("tail");
        tail.then(Cmd.sh("echo '' > /tmp/foo.txt"));
        //tail.then(Cmd.signal("FOO_READY")); //BUG signalling before tail -f is a race!
        tail.then(
                Cmd.sh("tail -f /tmp/foo.txt")
                        .addTimer(10_000,
                                Cmd.code((input,state)->{
                                    return Result.next(input);
                                }).then(Cmd.signal("FOO_READY")))
                        .watch(Cmd.code((input, state)->{
                            return Result.next(input);
                        }))
                        .watch(Cmd.code((input, state) -> {
                            try {
                                Thread.sleep(5_000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                                Thread.interrupted();
                            }
                            fooOutput.append(input);
                            return Result.next(input);
                        }))
                        .onSignal("FOO_DONE", Cmd.code((input,state)->{
                            return Result.next(input);
                        }).then(Cmd.ctrlC()))

        ).then(Cmd.sh("cat /tmp/foo.txt"));

        tail.then(Cmd.sh("echo '' > /tmp/bar.txt"));
        tail.then(Cmd.signal("BAR_READY"));
        tail.then(
                Cmd.sh("tail -f /tmp/bar.txt")

                        .watch(Cmd.code((input, state) -> {
                            barOutput.append(input);
                            return Result.next(input);
                        }))
                        .onSignal("BAR_DONE", Cmd.ctrlC())
        );

        Script echo = new Script("echo");
        echo.then(Cmd.waitFor("FOO_READY"));
        echo.then(Cmd.sleep("5s"));
        echo.then(Cmd.sh("echo 'foo1' >> /tmp/foo.txt"));
        echo.then(Cmd.sh("echo 'foo2' >> /tmp/foo.txt"));
        echo.then(Cmd.sh("echo 'foo3' >> /tmp/foo.txt"));
        echo.then(Cmd.sleep("5s")); // added because docker doesnt update tail before DONE sends CtrlC
        echo.then(Cmd.signal("FOO_DONE"));
        echo.then(Cmd.waitFor("BAR_READY"));
        echo.then(Cmd.sh("echo 'bar1' >> /tmp/bar.txt"));
        echo.then(Cmd.sh("echo 'bar2' >> /tmp/bar.txt"));
        echo.then(Cmd.sh("echo 'bar3' >> /tmp/bar.txt"));
        echo.then(Cmd.sleep("5s")); // added because docker doesnt update tail before DONE sends CtrlC
        echo.then(Cmd.signal("BAR_DONE"));

        RunConfigBuilder builder = getBuilder();
        builder.addHostAlias("local", getHost().toString());
        builder.addScript(tail);
        builder.addScript(echo);

        builder.addHostToRole("role", "local");
        builder.addRoleRun("role", "tail", new HashMap<>());
        builder.addRoleRun("role", "echo", new HashMap<>());

        RunConfig config = builder.buildConfig(Parser.getInstance());
        RunSummary summary = new RunSummary();
        SignalCounts signalCounts = new SignalCounts();
        summary.addRule("signals",signalCounts);
        summary.scan(config.getRolesValues(),builder);
        assertFalse("unexpected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());

        assertEquals("signal count for FOO_READY: "+signalCounts.getCounts(),1,signalCounts.getSignalCount("FOO_READY"));
        assertEquals("signal count for FOO_DONE: "+signalCounts.getCounts(),1,signalCounts.getSignalCount("FOO_DONE"));

        assertEquals("signal count for BAR_READY: "+signalCounts.getCounts(),1,signalCounts.getSignalCount("BAR_READY"));
        assertEquals("signal count for BAR_DONE: "+signalCounts.getCounts(),1,signalCounts.getSignalCount("BAR_DONE"));
    }

    @Test
    public void signal_in_timer(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
            """                        
            scripts:
              sig:
                - sh: tail -f /dev/null
                  timer:
                    5m:
                    - set-state: fizz fuzz
                      then:
                      - signal: sig
              wat:
                - wait-for: sig
            hosts:
              local: me@localhost
            roles:
              role:
                hosts: [local]
                run-scripts:
                - sig:
                - wat:
            """
        ));
        RunConfig config = builder.buildConfig(parser);
        RunSummary summary = new RunSummary();
        SignalCounts signalCounts = new SignalCounts();
        summary.addRule("signals",signalCounts);
        summary.scan(config.getRolesValues(),builder);
        assertFalse("unexpected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        assertEquals("signal count for sig: "+signalCounts.getCounts(),1,signalCounts.getSignalCount("sig"));
    }

    @Test
    public void signal_in_timer_on_waitfor(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
            """
            scripts:
              sig:
                - wait-for: sig
                  timer:
                    5m:
                    - set-state: fizz fuzz
                      then:
                      - signal: sig
            hosts:
              local: me@localhost
            roles:
              role:
                hosts: [local]
                run-scripts:
                - sig
            """
        ));
        RunConfig config = builder.buildConfig(parser);
        RunSummary summary = new RunSummary();
        SignalCounts signalCounts = new SignalCounts();
        summary.addRule("signals",signalCounts);
        summary.scan(config.getRolesValues(),builder);
        assertFalse("unexpected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        assertEquals("signal count for sig: "+signalCounts.getCounts(),1,signalCounts.getSignalCount("sig"));
    }

    @Test
    public void error_waitfor_without_signal(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                scripts:
                  sig:
                    - wait-for: FOO
                    - sh: tail -f /dev/null
                      timer:
                        5m:
                        - set-state: fizz fuzz
                          then:
                          - signal: sig
                  wat:
                    - wait-for: sig
                hosts:
                  local: me@localhost
                roles:
                  role:
                    hosts: [local]
                    run-scripts:
                    - sig:
                    - wat:
                """
        ));
        RunConfig config = builder.buildConfig(parser);
        RunSummary summary = new RunSummary();
        SignalCounts signalCounts = new SignalCounts();
        summary.addRule("signals",signalCounts);
        summary.scan(config.getRolesValues(),builder);
        assertTrue("expected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        assertEquals("expect 1 error",1,summary.getErrors().size());
        assertEquals("signal count for sig: "+signalCounts.getCounts(),1,signalCounts.getSignalCount("sig"));
    }

    @Test
    public void waitfor_empty_string(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                scripts:
                  sig:
                    - wait-for: ${{missing:}}
                    - sh: tail -f /dev/null
                      timer:
                        5m:
                        - set-state: fizz fuzz
                          then:
                          - signal: sig
                  wat:
                    - wait-for: sig
                hosts:
                  local: me@localhost
                roles:
                  role:
                    hosts: [local]
                    run-scripts:
                    - sig:
                    - wat:
                """
        ));
        RunConfig config = builder.buildConfig(parser);
        RunSummary summary = new RunSummary();
        SignalCounts signalCounts = new SignalCounts();
        summary.addRule("signals",signalCounts);
        summary.scan(config.getRolesValues(),builder);
        assertFalse("expected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        assertEquals("signal count for sig: "+signalCounts.getCounts(),1,signalCounts.getSignalCount("sig"));
    }



    @Test
    public void signal_in_watcher(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                scripts:
                  sig:
                    - sh: tail -f /dev/null
                      watch:
                      - signal: sig
                  wat:
                    - wait-for: sig
                hosts:
                  local: me@localhost
                roles:
                  role:
                    hosts: [local]
                    run-scripts:
                    - sig:
                    - wat:
                """
        ));
        RunConfig config = builder.buildConfig(parser);
        RunSummary summary = new RunSummary();
        SignalCounts signalCounts = new SignalCounts();
        summary.addRule("signals",signalCounts);
        summary.scan(config.getRolesValues(),builder);
        assertFalse("unexpected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        assertEquals("signal count for sig: "+signalCounts.getCounts(),1,signalCounts.getSignalCount("sig"));
    }

    @Test
    public void signal_in_onsignal(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                scripts:
                  sig:
                    - sh: tail -f /dev/null
                      on-signal:
                        foo:
                        - signal: sig
                  wat:
                    - wait-for: sig
                hosts:
                  local: me@localhost
                roles:
                  role:
                    hosts: [local]
                    run-scripts:
                    - sig:
                    - wat:
                """
        ));
        RunConfig config = builder.buildConfig(parser);
        RunSummary summary = new RunSummary();
        SignalCounts signalCounts = new SignalCounts();
        summary.addRule("signals",signalCounts);
        summary.scan(config.getRolesValues(),builder);
        assertFalse("unexpected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        assertEquals("signal count for sig: "+signalCounts.getCounts(),1,signalCounts.getSignalCount("sig"));
    }

    @Test
    public void error_missing_signal_variable_renamed(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                scripts:
                  sig:
                    - signal: ${{FOO}}
                  inv:
                    - wait-for: ${{FOO}}
                hosts:
                  local: me@localhost
                roles:
                  role:
                    hosts: [local]
                    run-scripts:
                    - sig:
                        with: { FOO: BIZ }
                    - inv:
                        with: { FOO: BUZ }
                """
        ));
        RunConfig config = builder.buildConfig(parser);

        RunSummary summary = new RunSummary();
        SignalCounts signalCounts = new SignalCounts();
        summary.addRule("signals",signalCounts);
        summary.scan(config.getRolesValues(),builder);
        assertTrue("expect errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        assertEquals("expected number of errors",1,summary.getErrors().size());
        assertEquals("signal count for BIZ",1,signalCounts.getSignalCount("BIZ"));
        assertEquals("signal count for BUZ",0,signalCounts.getSignalCount("BUZ"));
    }
    @Test
    public void error_waitfor_in_previous_stage(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                scripts:
                  sig:
                    - signal: FOO
                  inv:
                    - wait-for: FOO
                hosts:
                  local: me@localhost
                roles:
                  role:
                    hosts: [local]
                    setup-scripts:
                    - inv
                    run-scripts:
                    - sig
                """
        ));
        RunConfig config = builder.buildConfig(parser);

        RunSummary summary = new RunSummary();
        SignalCounts signalCounts = new SignalCounts();
        summary.addRule("signals",signalCounts);
        summary.scan(config.getRolesValues(),builder);

        assertTrue("expect errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        assertEquals("expected number of errors",1,summary.getErrors().size());
        assertEquals("signal count for FOO",1,signalCounts.getSignalCount("FOO"));
    }
    @Test
    public void error_signal_after_waitfor(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                scripts:
                  test:
                    - wait-for: FOO
                    - signal: FOO
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
        SignalCounts signalCounts = new SignalCounts();
        summary.addRule("signals",signalCounts);
        summary.scan(config.getRolesValues(),builder);
        assertTrue("expect errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        assertEquals("expected number of errors",1,summary.getErrors().size());
        assertEquals("signal count for FOO",1,signalCounts.getSignalCount("FOO"));
    }
    @Test
    public void error_signal_after_waitfor_different_role_different_phase(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                scripts:
                  wait:
                    - wait-for: FOO
                  signal:
                    - signal: FOO
                hosts:
                  local: me@localhost
                roles:
                  waiter:
                    hosts: [local]
                    setup-scripts:
                    - wait
                  signaler:
                    hosts: [local]
                    run-scripts:
                    - signal
                """
        ));
        RunConfig config = builder.buildConfig(parser);

        RunSummary summary = new RunSummary();
        SignalCounts signalCounts = new SignalCounts();
        summary.addRule("signals",signalCounts);
        summary.scan(config.getRolesValues(),builder);
        assertTrue("expect errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        assertEquals("expected number of errors",1,summary.getErrors().size());
        assertEquals("signal count for FOO",1,signalCounts.getSignalCount("FOO"));
    }

    @Test
    public void error_missing_signal(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                scripts:
                  test:
                    - wait-for: FOO
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
        SignalCounts signalCounts = new SignalCounts();
        summary.addRule("signals",signalCounts);
        summary.scan(config.getRolesValues(),builder);
        assertTrue("expect errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        assertEquals("expected number of errors",1,summary.getErrors().size());
        assertEquals("signal count for FOO",0,signalCounts.getSignalCount("FOO"));
    }

    @Test
    public void setsignal_variable_name_then_waitfor(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                scripts:
                  test:
                    - set-signal: ${{BAR}} 1
                    - wait-for: FOO
                hosts:
                  local: me@localhost
                states:
                  BAR: FOO
                roles:
                  role:
                    hosts: [local]
                    run-scripts:
                    - test:
                """
        ));
        RunConfig config = builder.buildConfig(parser);

        RunSummary summary = new RunSummary();
        SignalCounts signalCounts = new SignalCounts();
        summary.addRule("signals",signalCounts);
        summary.scan(config.getRolesValues(),builder);
        assertFalse("unexpected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        assertEquals("signal count for FOO",0,signalCounts.getSignalCount("FOO"));
    }

    @Test
    public void signal_in_previous_stage(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                scripts:
                  sig:
                    - signal: FOO
                  inv:
                    - wait-for: FOO
                hosts:
                  local: me@localhost
                roles:
                  role:
                    hosts: [local]
                    setup-scripts:
                    - sig
                    run-scripts:
                    - inv
                """
        ));
        RunConfig config = builder.buildConfig(parser);

        RunSummary summary = new RunSummary();
        SignalCounts signalCounts = new SignalCounts();
        summary.addRule("signals",signalCounts);
        summary.scan(config.getRolesValues(),builder);
        assertFalse("unexpected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        assertEquals("signal count for FOO",1,signalCounts.getSignalCount("FOO"));
    }

    @Test
    public void setsignal_then_waitfor(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                scripts:
                  test:
                    - set-signal: FOO 1
                    - wait-for: FOO
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
        SignalCounts signalCounts = new SignalCounts();
        summary.addRule("signals",signalCounts);
        summary.scan(config.getRolesValues(),builder);
        assertFalse("unexpected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        assertEquals("signal count for FOO",0,signalCounts.getSignalCount("FOO"));
    }

    @Test
    public void signal_name_bound_in_state(){
        RunConfigBuilder builder = new RunConfigBuilder();
        Script signal = new Script("signal");
        signal.then(Cmd.signal("${{FOO}}"));
        builder.addScript(signal);
        builder.addHostAlias("alpha","guest@alpha");
        builder.addHostToRole("role","alpha");
        builder.addRoleRun("role","signal", Collections.emptyMap());
        builder.setRunState("FOO","foo");

        RunConfig config = builder.buildConfig();
        RunSummary summary = new RunSummary();
        SignalCounts signalCounts = new SignalCounts();
        summary.addRule("signals",signalCounts);
        summary.scan(config.getRolesValues(),builder);
        assertFalse("unexpected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        assertEquals("signal count for foo",1,signalCounts.getSignalCount("foo"));

    }

    @Test
    public void signal_name_bound_in_role(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                scripts:
                  foo:
                    - signal: ${{FOO:}}
                  bar:
                    - wait-for: ${{BAR}}
                hosts:
                  local: me@localhost
                roles:
                  role:
                    hosts: [local]
                    run-scripts:
                    - foo:
                        with: { FOO: foo }
                    - bar:
                        with: { BAR: foo }
                """
        ));
        RunConfig config = builder.buildConfig(parser);
        RunSummary summary = new RunSummary();
        SignalCounts signalCounts = new SignalCounts();
        summary.addRule("signals",signalCounts);
        summary.scan(config.getRolesValues(),builder);
        assertFalse("unexpected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        assertEquals("signal count for foo",1,signalCounts.getSignalCount("foo"));

    }



    @Test
    public void signal_one_script_one_host(){
        RunConfigBuilder builder = new RunConfigBuilder();
        Script signal = new Script("signal");
        signal.then(Cmd.signal("FOO"));
        builder.addScript(signal);
        builder.addHostAlias("local","guest@localhost");
        builder.addHostToRole("role","local");
        builder.addRoleRun("role","signal", Collections.emptyMap());

        RunConfig config = builder.buildConfig();

        RunSummary summary = new RunSummary();
        SignalCounts signalCounts = new SignalCounts();
        summary.addRule("signals",signalCounts);
        summary.scan(config.getRolesValues(),builder);
        assertFalse("unexpected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        assertEquals("signal count for FOO",1,signalCounts.getSignalCount("FOO"));
    }

    @Test
    public void signal_one_script_two_hosts(){
        RunConfigBuilder builder = new RunConfigBuilder();

        Script signal = new Script("signal");
        signal.then(Cmd.signal("FOO"));

        builder.addScript(signal);

        builder.addHostAlias("alpha","guest@alpha");
        builder.addHostAlias("bravo","guest@bravo");
        builder.addHostToRole("role","alpha");
        builder.addHostToRole("role","bravo");
        builder.addRoleRun("role","signal", Collections.emptyMap());

        RunConfig config = builder.buildConfig();

        RunSummary summary = new RunSummary();
        SignalCounts signalCounts = new SignalCounts();
        summary.addRule("signals",signalCounts);
        summary.scan(config.getRolesValues(),builder);
        assertFalse("unexpected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        assertEquals("signal count for FOO",2,signalCounts.getSignalCount("FOO"));

    }

    @Test
    public void signal_two_scripts_one_host(){
        RunConfigBuilder builder = new RunConfigBuilder();


        Script signal = new Script("signal");
        signal.then(Cmd.signal("FOO"));
        Script second = new Script("second");
        second.then(Cmd.signal("FOO"));

        builder.addScript(signal);
        builder.addScript(second);

        builder.addHostAlias("alpha","guest@alpha");
        builder.addHostToRole("role","alpha");

        builder.addRoleRun("role","signal", Collections.emptyMap());
        builder.addRoleRun("role","second", Collections.emptyMap());

        RunConfig config = builder.buildConfig();

        RunSummary summary = new RunSummary();
        SignalCounts signalCounts = new SignalCounts();
        summary.addRule("signals",signalCounts);
        summary.scan(config.getRolesValues(),builder);
        assertFalse("unexpected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        assertEquals("signal count for FOO",2,signalCounts.getSignalCount("FOO"));
    }

    @Test
    public void signal_two_scripts_two_hosts(){
        RunConfigBuilder builder = new RunConfigBuilder();

        Script signal = new Script("signal");
        signal.then(Cmd.signal("FOO"));
        Script second = new Script("second");
        second.then(Cmd.signal("FOO"));

        builder.addScript(signal);
        builder.addScript(second);

        builder.addHostAlias("alpha","guest@alpha");
        builder.addHostAlias("bravo","guest@bravo");

        builder.addHostToRole("role","alpha");
        builder.addHostToRole("role","bravo");

        builder.addRoleRun("role","signal", Collections.emptyMap());
        builder.addRoleRun("role","second", Collections.emptyMap());

        RunConfig config = builder.buildConfig();

        RunSummary summary = new RunSummary();
        SignalCounts signalCounts = new SignalCounts();
        summary.addRule("signals",signalCounts);
        summary.scan(config.getRolesValues(),builder);
        assertFalse("unexpected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        assertEquals("signal count for FOO",4,signalCounts.getSignalCount("FOO"));
    }

    @Test
    public void signal_repeated_in_one_script(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                scripts:
                  sig:
                    - signal: FOO
                    - signal: FOO
                    - signal: FOO
                    - signal: FOO
                hosts:
                  local: me@localhost
                roles:
                  role:
                    hosts: [local]
                    run-scripts:
                    - sig:
                """
        ));
        RunConfig config = builder.buildConfig(parser);

        RunSummary summary = new RunSummary();
        SignalCounts signalCounts = new SignalCounts();
        summary.addRule("signals",signalCounts);
        summary.scan(config.getRolesValues(),builder);
        assertFalse("unexpected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        assertEquals("signal count for FOO",4,signalCounts.getSignalCount("FOO"));
    }

    @Test
    public void signal_in_watch(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal_in_watch",
                """
                scripts:
                  sig:
                    - sh: tail -f /var/log/messages
                      watch:
                      - regex: READY
                        then:
                        - ctrlC:
                        - log: going to signal ${{name}}
                        - signal: ${{name}}
                  wat:
                    - wait-for: ${{to_wait}}
                hosts:
                  local: me@localhost
                roles:
                  water:
                    hosts: [local]
                    run-scripts:
                    - wat:
                        with:
                          to_wait: FOO
                  siger:
                    hosts: [local]
                    run-scripts:
                    - sig:
                        with:
                          name: FOO
                """
        ));
        RunConfig config = builder.buildConfig(parser);

        RunSummary summary = new RunSummary();
        SignalCounts signalCounts = new SignalCounts();
        summary.addRule("signals",signalCounts);
        summary.scan(config.getRolesValues(),builder);
        summary.close(builder,summary);
        assertFalse("unexpected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        assertEquals("signal count for FOO:\n"+signalCounts.getSignalNames(),1,signalCounts.getSignalCount("FOO"));
    }

    @Test
    public void signal_repeated_sub_script(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                scripts:
                  sig:
                    - signal: FOO
                    - signal: ${{BAR}}
                  inv:
                    - script: sig
                      with: {BAR: alpha}
                    - script: sig
                      with: {BAR: bravo}
                    - script: sig
                      with: {BAR: charlie}
                    - script: sig
                      with: {BAR: delta}
                  wat:
                    - wait-for: FOO
                    - done:
                hosts:
                  local: me@localhost
                roles:
                  role:
                    hosts: [local]
                    run-scripts:
                    - inv:
                    - wat:
                """
        ));
        RunConfig config = builder.buildConfig(parser);
        RunSummary summary = new RunSummary();
        SignalCounts signalCounts = new SignalCounts();
        summary.addRule("signals",signalCounts);
        summary.scan(config.getRolesValues(),builder);
        assertFalse("unexpected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        assertEquals("signal count for FOO: "+signalCounts.getCounts(),4,signalCounts.getSignalCount("FOO"));
        assertEquals("signal count for alpha: "+signalCounts.getCounts(),1,signalCounts.getSignalCount("alpha"));
        assertEquals("signal count for bravo: "+signalCounts.getCounts(),1,signalCounts.getSignalCount("bravo"));
        assertEquals("signal count for charlie: "+signalCounts.getCounts(),1,signalCounts.getSignalCount("charlie"));
        assertEquals("signal count for delta: "+signalCounts.getCounts(),1,signalCounts.getSignalCount("delta"));
    }

    @Test
    public void signal_in_subscript(){
        RunConfigBuilder builder = new RunConfigBuilder();

        Script signal = new Script("signal");
        signal.then(Cmd.signal("FOO"));
        Script second = new Script("second");
        second.then(Cmd.script("signal"));

        builder.addScript(signal);
        builder.addScript(second);

        builder.addHostAlias("alpha","guest@alpha");
        builder.addHostAlias("bravo","guest@bravo");

        builder.addHostToRole("role","alpha");
        builder.addHostToRole("role","bravo");

        builder.addRoleRun("role","signal", Collections.emptyMap());
        builder.addRoleRun("role","second", Collections.emptyMap());

        RunConfig config = builder.buildConfig();

        RunSummary summary = new RunSummary();
        SignalCounts signalCounts = new SignalCounts();
        summary.addRule("signals",signalCounts);
        summary.scan(config.getRolesValues(),builder);
        assertFalse("unexpected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        assertEquals("signal count for FOO",4,signalCounts.getSignalCount("FOO"));
    }

    @Test @Ignore /*TODO expected signal count needs to take else-then branching into account*/
    public void signal_in_then_and_else(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                scripts:
                  single:
                    - log: single
                    - signal: sig
                  replicated:
                    - log: replicated
                    - signal: sig
                  caller:
                    - log: caller
                    - read-state: ${{TOPOLOGY}}
                      then:
                        - regex: single
                          then:
                            - script: single
                          else:
                            - script: replicated
                  waiter:
                    - log: waiting for signal
                    - wait-for: sig
                hosts:
                  local: me@localhost
                roles:
                  role:
                    hosts: [local]
                    run-scripts:
                    - caller
                    - waiter
                states:
                  TOPOLOGY: single
                """
        ));
        RunConfig config = builder.buildConfig(parser);
        RunSummary summary = new RunSummary();
        SignalCounts signalCounts = new SignalCounts();
        summary.addRule("signals",signalCounts);
        summary.scan(config.getRolesValues(),builder);
        assertFalse("unexpected errors:\n"+summary.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),summary.hasErrors());
        assertEquals("signal count for sig: "+signalCounts.getCounts(),1,signalCounts.getSignalCount("sig"));
    }


    @Test
    public void setSignalCountTest(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("",
                """
                scripts:
                  repeat-until:
                  - repeat-until: TEST_DONE
                    then:
                    - wait-for: READY
                    - set-state: RUN.counter ${{= ${{RUN.counter}} + 1}}
                    - set-signal:
                        name: READY
                        count: 1
                        reset: true
                  run-test:
                  - for-each:
                      name: it
                      input: ${{tests}}
                    then:
                    - log: running test ${{it}}
                    - signal: READY
                    - sleep: 500
                  - signal: TEST_DONE
                hosts:
                  local: TARGET_HOST
                roles:
                  doit:
                    hosts: [local]
                    run-scripts:
                      - repeat-until
                      - run-test
                states:
                  counter: 0
                  tests: ['test1', 'test2', 'test3']
                """.replaceAll("TARGET_HOST",getHost().toString())
        ));

        RunConfig config = builder.buildConfig(parser);
        assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());

        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);

        doit.run();
        dispatcher.shutdown();
        State state = config.getState();
        assertTrue("state should have key",state.has("counter"));
        assertEquals(3l,state.get("counter"));
    }
}
