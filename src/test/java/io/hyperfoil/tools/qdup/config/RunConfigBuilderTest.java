package io.hyperfoil.tools.qdup.config;

import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Script;
import io.hyperfoil.tools.qdup.cmd.impl.CtrlC;
import io.hyperfoil.tools.qdup.cmd.impl.ScriptCmd;
import io.hyperfoil.tools.qdup.cmd.impl.Sh;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import io.hyperfoil.tools.qdup.SshTestBase;

import org.junit.Ignore;
import org.junit.Test;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RunConfigBuilderTest extends SshTestBase {

    @Test
    public void role_hosts_pattern_as_expression_existing_host(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("first",stream(""+
            "scripts:",
            "  foo:",
            "    - sh: pwd",
            "hosts:",
            "  foo: me@localhost",
            "roles:",
            "  doit:",
            "    hosts: ${{hostname}}",
            "    setup-scripts:",
            "    - foo",
            "states:",
            "  hostname: foo"
        )));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());
        assertEquals("expect 2 roles because of ALL",2,config.getRoles().size());
        Role role = config.getRole("doit");
        assertNotNull("doit it should be a role: "+config.getRoleNames(),role);
        List<Host> declaredHosts = role.getDeclaredHosts();
        assertEquals("expect 1 host for role",1,declaredHosts.size());
        Host first = declaredHosts.get(0);
        assertNotNull("host should not be null",first);
        assertEquals("me",first.getUserName());
        assertEquals("localhost",first.getHostName());
    }
    @Test
    public void role_hosts_pattern_as_expression_new_host(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("first",stream(""+
                "scripts:",
                "  foo:",
                "    - sh: pwd",
                "hosts:",
                "  foo: you@localhost",
                "roles:",
                "  doit:",
                "    hosts: ${{hostname}}",
                "    setup-scripts:",
                "    - foo",
                "states:",
                "  hostname: me@localhost"
        )));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());
        assertEquals("expect 2 roles because of ALL",2,config.getRoles().size());
        Role role = config.getRole("doit");
        assertNotNull("doit it should be a role: "+config.getRoleNames(),role);
        List<Host> declaredHosts = role.getDeclaredHosts();
        assertEquals("expect 1 host for role",1,declaredHosts.size());
        Host first = declaredHosts.get(0);
        assertNotNull("host should not be null",first);
        assertEquals("me",first.getUserName());
        assertEquals("localhost",first.getHostName());
    }
    @Test
    public void role_hosts_pattern_as_expression_new_host_array(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("first",stream(""+
                        "scripts:",
                "  foo:",
                "    - sh: pwd",
                "hosts:",
                "  foo: you@localhost",
                "roles:",
                "  doit:",
                "    hosts: ${{hostname}}",
                "    setup-scripts:",
                "    - foo",
                "states:",
                "  hostname: ['me@localhost','he@localhost','she@localhost']"
        )));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());
        assertEquals("expect 2 roles because of ALL",2,config.getRoles().size());
        Role role = config.getRole("doit");
        assertNotNull("doit it should be a role: "+config.getRoleNames(),role);
        List<Host> declaredHosts = role.getDeclaredHosts();
        assertEquals("expect 3 host for role",3,declaredHosts.size());
        assertTrue("hosts should container me@localhost: "+declaredHosts,declaredHosts.contains(Host.parse("me@localhost")));
        assertTrue("hosts should container he@localhost: "+declaredHosts,declaredHosts.contains(Host.parse("he@localhost")));
        assertTrue("hosts should container she@localhost: "+declaredHosts,declaredHosts.contains(Host.parse("she@localhost")));
    }

    @Test
    public void role_hosts_pattern_in_list_existing_host(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("first",stream(""+
                "scripts:",
                "  foo:",
                "    - sh: pwd",
                "hosts:",
                "  foo: me@localhost",
                "roles:",
                "  doit:",
                "    hosts:",
                "    - ${{hostname}}",
                "    setup-scripts:",
                "    - foo",
                "states:",
                "  hostname: foo"
        )));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());
        assertEquals("expect 2 roles because of ALL",2,config.getRoles().size());
        Role role = config.getRole("doit");
        assertNotNull("doit it should be a role: "+config.getRoleNames(),role);
        List<Host> declaredHosts = role.getDeclaredHosts();
        assertEquals("expect 1 host for role",1,declaredHosts.size());
        Host first = declaredHosts.get(0);
        assertNotNull("host should not be null",first);
        assertEquals("me",first.getUserName());
        assertEquals("localhost",first.getHostName());
    }

    @Test
    public void role_hosts_pattern_in_list_new_host(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("first",stream(""+
                "scripts:",
                "  foo:",
                "    - sh: pwd",
                "hosts:",
                "  foo: you@localhost",
                "roles:",
                "  doit:",
                "    hosts:",
                "    - ${{hostname}}",
                "    setup-scripts:",
                "    - foo",
                "states:",
                "  hostname: me@localhost"
        )));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());
        assertEquals("expect 2 roles because of ALL",2,config.getRoles().size());
        Role role = config.getRole("doit");
        assertNotNull("doit it should be a role: "+config.getRoleNames(),role);
        List<Host> declaredHosts = role.getDeclaredHosts();
        assertEquals("expect 1 host for role",1,declaredHosts.size());
        Host first = declaredHosts.get(0);
        assertNotNull("host should not be null",first);
        assertEquals("me",first.getUserName());
        assertEquals("localhost",first.getHostName());
    }

    @Test
    public void role_hosts_pattern_in_list_new_host_array(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("first",stream(""+
                        "scripts:",
                "  foo:",
                "    - sh: pwd",
                "hosts:",
                "  foo: you@localhost",
                "roles:",
                "  doit:",
                "    hosts:",
                "    - ${{hostname}}",
                "    setup-scripts:",
                "    - foo",
                "states:",
                "  hostname: ['me@localhost','he@localhost','she@localhost']"
        )));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());
        assertEquals("expect 2 roles because of ALL",2,config.getRoles().size());
        Role role = config.getRole("doit");
        assertNotNull("doit it should be a role: "+config.getRoleNames(),role);
        List<Host> declaredHosts = role.getDeclaredHosts();
        assertEquals("expect 3 host for role",3,declaredHosts.size());
        assertTrue("hosts should container me@localhost: "+declaredHosts,declaredHosts.contains(Host.parse("me@localhost")));
        assertTrue("hosts should container he@localhost: "+declaredHosts,declaredHosts.contains(Host.parse("he@localhost")));
        assertTrue("hosts should container she@localhost: "+declaredHosts,declaredHosts.contains(Host.parse("she@localhost")));

    }


    @Test
    public void multiple_yaml_override_state(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("first",stream(""+
            "states:",
            "  foo: bar"
        )));
        builder.loadYaml(parser.loadFile("first",stream(""+
            "states:",
            "  foo: biz"
        )));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());
        assertEquals("second should not change state\n"+config.getState().toJson().toString(2),"bar",config.getState().get("foo"));
    }

    @Test
    public void error_missing_signal_value() {
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("error", stream("" +
            "scripts:",
            "  foo:",
            "  - signal: ${{BAR}}",
            "hosts:",
            "  local: "+getHost(),
            "roles:",
            "  doit:",
            "    hosts: [local]",
            "    setup-scripts: [foo]",
            "states:",
            "  BAR: ${{BIZ}}",
            "  BIZx: "
        )));
        RunConfig config = builder.buildConfig(parser);
        assertTrue("config errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
        assertEquals("expect 1 error:\n"+ config.getErrorStrings().stream().collect(Collectors.joining("\n")),1,config.getErrors().size());
    }

    @Test
    public void error_missing_referenced_state_value() {
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("ctrlC", stream("" +
                "scripts:",
                "  foo:",
                "    - sh: echo HI",
                "      then:",
                "      - sh: echo ${{BAR}}",
                "hosts:",
                "  local: "+getHost(),
                "roles:",
                "  doit:",
                "    hosts: [local]",
                "    setup-scripts: [foo]",
                "states:",
                "  BAR: ${{BIZ}}",
                "  BIZx: "
        )));
        RunConfig config = builder.buildConfig(parser);
        assertTrue("config errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
        assertEquals("expect 1 error:\n"+ config.getErrorStrings().stream().collect(Collectors.joining("\n")),1,config.getErrors().size());
    }

    @Test
    public void error_missing_state_has_default() {
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("ctrlC", stream("" +
            "scripts:",
            "  foo:",
            "    - sh: echo HI",
            "      then:",
            "      - sh: echo ${{BAR}}",
            "hosts:",
            "  local: "+getHost(),
            "roles:",
            "  doit:",
            "    hosts: [local]",
            "    setup-scripts: [foo]",
            "states:",
            "  BAR: ${{BIZ:}}",
            "  BIZx: "
        )));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());
    }

    @Test
    public void error_invalid_top_level_entry() {
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("error_invalid_top_level_entry", stream("" +
                "scripts:",
                "  foo:",
                "    - sh: echo HI",
                "      then:",
                "      - sh: |",
                "          echo ${{BAR}}",
                "state-scan: false",
                "hosts:",
                "  local: "+getHost(),
                "roles:",
                "  doit:",
                "    hosts: [local]",
                "    setup-scripts: [foo]",
                "states:",
                "  BAR: ${{BIZ:}}",
                "  BIZx: "
        )));
        System.out.println(builder.errorCount());
        RunConfig config = builder.buildConfig(parser);
        assertTrue("expected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());
    }
    @Test
    public void error_invalid_statescan_indentation() {
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("error_invalid_statescan_indentation", stream("" +
                "scripts:",
                "  foo:",
                "    - sh: echo HI",
                "      then:",
                "      - sh: |",
                "          echo ${{BAR}}",
                "       state-scan: false",
                "hosts:",
                "  local: "+getHost(),
                "roles:",
                "  doit:",
                "    hosts: [local]",
                "    setup-scripts: [foo]",
                "states:",
                "  BAR: ${{BIZ:}}",
                "  BIZx: "
        )));
        System.out.println(builder.errorCount());
        RunConfig config = builder.buildConfig(parser);
        assertTrue("expected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());
    }


    @Test
    public void testScriptWithCtrlC(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("ctrlC",stream(""+
            "scripts:",
            "  foo:",
            "    - ctrlC",
            "    - sh: tail -f bar.txt",
            "      watch:",
            "      - regex: bar",
            "        then:",
            "        - ctrlC",
            "    - sh: echo 'yay'"
        )));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());
        assertFalse("runConfig errors:\n"+config.getErrorStrings().stream().collect(Collectors.joining("\n")),config.hasErrors());

        Script fooScript = config.getScript("foo");

        Cmd cmd = fooScript.getNext();//ctrlC
        cmd = cmd.getNext();//sh


        assertTrue("sh should have watchers",cmd.hasWatchers());
        cmd = cmd.getWatchers().get(0);

        cmd = cmd.getNext();

        assertTrue("regex should have a next command",cmd !=null);
        assertTrue("regex next should be ctrlC",cmd instanceof CtrlC);


    }

    @Test
    public void testRolesWithState(){
        RunConfigBuilder builder = getBuilder();
        Parser parser = Parser.getInstance();
        builder.loadYaml(parser.loadFile("with",stream("",
            "scripts:",
            "  build-agroal:",
            "  - echo",
            "  setup-wildfly:",
            "  - echo",
            "hosts:",
            "  local : me@localhost",
            "roles:",
            "  wildfly:",
            "    hosts: [local]",
            "    setup-scripts:",
            "    - build-agroal:",
            "        with:",
            "          GIT_COMMIT : ${{AGROAL_TAG}}",
            "    - setup-wildfly"
        )));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());

        assertTrue("run should have wildfly role",config.getRoleNames().contains("wildfly"));
        Role role = config.getRole("wildfly");
        assertEquals("hosts in wildfly role",1,role.getDeclaredHosts().size());
        List<ScriptCmd> setup = role.getSetup();
        assertEquals("two scripts in setup",2,setup.size());
    }

    @Test
    public void testVariableScriptWithWaitFor(){
        RunConfigBuilder builder = getBuilder();
        Parser parser = Parser.getInstance();
        builder.loadYaml(parser.loadFile("waitfor",stream("",
            "scripts:",
            "  signal:",
            "  - signal: SERVER_READY",
            "  waiter:",
            "  - wait-for: SERVER_READY",
            "hosts:",
            "  local: me@localhost",
            "  server: me@serverHostName",
            "roles:",
            "  test:",
            "    hosts: [local, server]",
            "    run-scripts:",
            "    - ${{SCRIPT_NAME}}",
            "    - ${{WAIT_NAME}}",
            "states:",
            "  SCRIPT_NAME : signal",
            "  WAIT_NAME : waiter"
        )));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());

        assertFalse("runConfig errors:\n"+config.getErrorStrings().stream().collect(Collectors.joining("\n")),config.hasErrors());

        List<String> signalNames = config.getSignalCounts().entries();

        assertTrue("signal: "+signalNames.toString(),signalNames.contains("SERVER_READY"));

        long signalCount = config.getSignalCounts().count("SERVER_READY");

        assertEquals("signal count for SERVER_READY",2,signalCount);
    }

    @Test
    public void testImplicitRunState(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("implicitState",stream("",
           "states:",
           "  foo : foo",
           "  host :",
           "    foo : bar"
        )));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());

        assertFalse("runConfig errors:\n"+config.getErrorStrings().stream().collect(Collectors.joining("\n")),config.hasErrors());

        State state = config.getState();

        assertTrue("missing default run state",state.has("foo"));
        assertEquals("foo",state.get("foo"));

        //TODO right now state is parsed as one big json, host based state requires scripts to know the host alias
//        assertTrue("state should have a host child",state.hasChild("host"));
//
//        State hostState = state.getChild("host");
//
//        assertEquals("host should see foo = bar","bar",hostState.get("foo"));
//        assertEquals("host["+RUN_PREFIX+"foo]=foo","foo",hostState.get(RUN_PREFIX+"foo"));
    }

    /**
     * The first yaml to set a state name wins. State merge without override
     */
    @Test @Ignore
    public void testSameStateName(){
        RunConfigBuilder builder = getBuilder();
        Parser parser = Parser.getInstance();
        builder.loadYaml(parser.loadFile("firstDef",stream("",
            "states:",
            "  run:",
            "    FOO : FOO"
        )));
        builder.loadYaml(parser.loadFile("secondDef",stream("",
            "states:",
            "  run:",
            "    FOO : BAR",
            "    BAR : BAR"
        )));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());

        assertFalse("runConfig errors:\n"+config.getErrorStrings().stream().collect(Collectors.joining("\n")),config.hasErrors());
        State state = config.getState();

        assertTrue("state missing BAR : "+state.getKeys(),state.getKeys().contains("BAR"));
        assertEquals("FOO should not change after initial load","FOO",state.get("FOO"));
        assertEquals("BAR should loaded from the second yaml","BAR",state.get("BAR"));
    }

    /**
     * The first yaml to load a script wins. Scripts do not merge
     */
    @Test
    public void testSameScriptName(){
        RunConfigBuilder builder = getBuilder();
        Parser parser = Parser.getInstance();
        builder.loadYaml(parser.loadFile("firstDef",stream("",
            "scripts:",
            "  first:",
            "  - sh: echo FOO",
            "hosts:",
            "  local : fakeUser@localhost"
        )));
        builder.loadYaml(parser.loadFile("secondDef",stream("",
            "scripts:",
            "  first:",
            "    - sh: echo BAR",
            ""
        )));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());

        assertFalse("runConfig errors:\n"+config.getErrorStrings().stream().collect(Collectors.joining("\n")),config.hasErrors());

        Script script = config.getScript("first");

        assertTrue("the first script definition should be used",script.tree().contains("echo FOO"));
        assertFalse("the second should script not be merged",script.tree().contains("echo BAR"));
    }

    @Test
    public void testSh_echoEnvironmentVariable(){
        RunConfigBuilder builder = getBuilder();
        Parser parser = Parser.getInstance();
        builder.loadYaml(parser.loadFile("echo",stream("",
            "scripts:",
            "  foo:",
            "  - sh: export FOO=$(ps -ef | grep jboss)",
            "    then:",
            "    - sh: echo ${FOO}"
        )));

        RunConfig config = builder.buildConfig(parser);
        assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());

        assertFalse("RunConfig should not contain errors but saw\n:"+config.getErrorStrings().stream().collect(Collectors.joining("\n")),config.hasErrors());

        Script foo = config.getScript("foo");
        assertNotNull("script foo should not be null",foo);
        Cmd cmd = foo.getNext();
        assertFalse("foo should have a next command",cmd==null);
        cmd = cmd.getNext();
        assertFalse("first command should have a next",cmd==null);

        assertTrue("second command should be sh",cmd instanceof Sh);
        assertTrue("second command should contain ${FOO}",cmd.toString().contains("${FOO}"));
    }

    @Test
    public void testSilentWithWatcher(){
        RunConfigBuilder builder = getBuilder();
        Parser parser = Parser.getInstance();
        builder.loadYaml(parser.loadFile("silentWithWatcher",stream("",
            "scripts:",
            "  first:",
            "    - sh: ",
            "        silent: true",
            "        command: tail -f ./standalone/server.log",
            "      watch:",
            "      - regex: .*FATAL.*",
            "        then:",
            "        - abort: fatal"
        )));

        RunConfig config = builder.buildConfig(parser);
        assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());

        assertFalse("RunConfig should not contain errors but saw\n:"+config.getErrorStrings().stream().collect(Collectors.joining("\n")),config.hasErrors());

        Script first = config.getScript("first");

        assertNotNull("first script should not be null",first);
        Cmd next = first.getNext();
        assertTrue("next should be an Sh command but was "+next.getClass(),(next instanceof Sh));

        Sh sh = (Sh) next;
        assertTrue("next should be silent",sh.isSilent());
        assertTrue("next should have a watcher",sh.hasWatchers());

    }

    @Test
    public void testCmdTimer(){
        RunConfigBuilder builder = getBuilder();
        Parser parser = Parser.getInstance();
        builder.loadYaml(parser.loadFile("cmdTimer",stream("",
            "scripts:",
            "  first:",
            "    - sh: long running command",
            "      timer:",
            "        30s:",
            "        - signal: 30_seconds_later",
            "        - abort: not good"
        )));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());
        assertFalse("RunConfig should not contain errors but saw\n:"+config.getErrorStrings().stream().collect(Collectors.joining("\n")),config.hasErrors());

        Script script = config.getScript("first");

        assertNotNull("script should exist",script);

        Cmd next = script.getNext();

        assertTrue("next should have a timer",next.hasTimers());
        Set<Long> timeouts = next.getTimeouts();

        assertTrue("timeouts should contain 30_000",timeouts.contains(30_000l));
        assertEquals("timeout should only contain 1 entry",1,timeouts.size());

        List<Cmd> timeout = next.getTimers(30_000l);

        assertEquals("timeout should have 2 entries:\n"+timeout,2,timeout.size());

        Cmd first = timeout.get(0);

        assertEquals("command should have 0 child command",0,first.getThens().size());

    }

    /**
     * A role defined in 2 yaml should merge
     */
    @Test
    public void test_merge_role_across_waml(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("scriptDef",stream(""+
          "scripts:",
           "  first:",
           "    - sh: ls",
           "    - sh: pwd",
           "  second:",
           "    - sh: whoami",
           "    - sh: echo ${PWD}",
           "roles:",
           "  role:",
           "    setup-scripts: [first]",
           "    run-scripts: [second]"
        )));
        builder.loadYaml(parser.loadFile("hostDef",stream(""+
           "hosts:",
           "  local: fakeUser@localhost",
           "roles:",
           "  role:",
           "    hosts: [local]"
        )));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());
        assertTrue("run has role",config.getRoleNames().contains("role"));
        Role role = config.getRole("role");

        assertEquals("role has setup script",1,role.getSetup().size());
        assertEquals("role has run script",1,role.getRun().size());
        assertEquals("role has a host",1,role.getDeclaredHosts().size());

        Cmd setupCmd = role.getSetup().get(0);
        List<ScriptCmd> runCmds = role.getRun();

        assertTrue("setup should contain first script",setupCmd.tree().contains("first"));
        assertEquals("role should contain one role script",1,runCmds.size());
        assertTrue("role should have second as a run script",runCmds.get(0).toString().contains("second"));
    }

    @Test @Ignore
    public void testRoleExpession(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("roleExpression",stream(""+
           "hosts:",
           "  foo : user@foo",
           "  bar : user@bar",
           "  biz : user@biz",
           "  buz : user@buz",
           "roles:",
           "  buzzer:",
           "    hosts: [buz]",
           "    run-scripts: [BuzScript]",
           "  foo:",
           "    hosts: [foo]",
           "    run-scripts: [FooRunScript]",
           "  foobarbiz",
           "    hosts: [foo, bar, biz]",
           "    run-scripts: [AllRunScript]",
           "  NotFoo",
           "    hosts: = foobarbiz - foo",
           "    run-scripts: [NotFooRunScript]",
           "  AllNotBar:",
           "    hosts: = all - foobarbiz",
           "    run-scripts: [AllNotBarScript]",
           ""
        )));

        RunConfig config = builder.buildConfig(parser);
        assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());
        assertFalse("RunConfig should not contain errors but saw:\n"+config.getErrorStrings().stream().collect(Collectors.joining("\n")),config.hasErrors());

        Host foo = new Host("user","foo");
        Host bar = new Host("user","bar");
        Host biz = new Host("user","biz");
        Host buz = new Host("user","buz");

        String fooScripts = "";//runConfig.getRunCmds(foo).toString();
        String barScripts = "";//runConfig.getRunCmds(bar).toString();
        String bizScripts = "";//runConfig.getRunCmds(biz).toString();
        String buzScripts = "";//runConfig.getRunCmds(buz).toString();

        assertTrue("foo host should run FooRunScript and AllRunScript",fooScripts.contains("FooRunScript") && fooScripts.contains("AllRunScript"));
        assertFalse("foo host should not have NotFooRunScript",fooScripts.contains("NotFooRunScript"));

        assertTrue("bar host should run AllRunScript and NotFooRunScript",barScripts.contains("AllRunScript") && barScripts.contains("NotFooRunScript"));
        assertFalse("bar host should not contain FooRunScript",barScripts.contains(" FooRunScript"));//need space otherwise matches NotFooRunScript

        assertTrue("buz should contain AllNotBarScript: "+buzScripts,buzScripts.contains("AllNotBarScript"));
        assertFalse("foo should not have AllNotBarScript: "+fooScripts,fooScripts.contains("AllNotBarScript"));
        assertFalse("bar should not have AllNotBarScript: "+barScripts,barScripts.contains("AllNotBarScript"));
        assertFalse("biz should not have AllNotBarScript: "+bizScripts,bizScripts.contains("AllNotBarScript"));
    }

    @Test
    public void testOldNestSyntax(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("oldSyntax",stream("",
           "name: oldSyntax",
           "scripts:",
           "  firstScript: #comment",
           "  - sh: do smomething",
           "    watch:",
           "    - regex: .*",
           "      then:",
           "      - abort: message true"
           )
        ));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());

        Script firstScript = config.getScript("firstScript");
        assertNotNull("firstScript should exist but was null",firstScript);
        Cmd next = firstScript.getNext();
        assertTrue("sh should have a watcher",next.hasWatchers());
        Cmd watcher = next.getLastWatcher();
        assertNotNull("sh watcher should not be null",watcher);
        assertTrue("watcher should have abort as child",watcher.getNext().toString().contains("abort"));
    }


}
