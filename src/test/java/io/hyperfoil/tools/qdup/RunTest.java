package io.hyperfoil.tools.qdup;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.hyperfoil.tools.qdup.cmd.*;
import io.hyperfoil.tools.qdup.cmd.impl.AddPrompt;
import io.hyperfoil.tools.qdup.cmd.impl.CtrlSignal;
import io.hyperfoil.tools.qdup.cmd.impl.ReadState;
import io.hyperfoil.tools.qdup.cmd.impl.Sh;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import io.hyperfoil.tools.yaup.AsciiArt;
import io.hyperfoil.tools.yaup.time.SystemTimer;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class RunTest extends SshTestBase {

//    @Rule
//    public final TestServer testServer = new TestServer();

   @Test(timeout = 10_000)
   public void watch_signal() {
      AtomicBoolean stopped = new AtomicBoolean(false);
      Script onSignal = new Script("onSignal");
      onSignal.then(Cmd.sleep("1h").onSignal("stop", Cmd.code(((input, state) -> {
         stopped.set(true);
         return Result.next("stopped");
      })).then(Cmd.abort("signal aborted"))));
      Script sendSignal = new Script("sendSignal");
      sendSignal.then(Cmd.sleep("2s").then(Cmd.signal("stop")));

      RunConfigBuilder builder = getBuilder();
      builder.addHostAlias("local", getHost().toString());
      builder.addScript(onSignal);
      builder.addScript(sendSignal);
      builder.addHostToRole("role", "local");
      builder.addRoleRun("role", "onSignal", new HashMap<>());
      builder.addRoleRun("role", "sendSignal", new HashMap<>());

      RunConfig config = builder.buildConfig(Parser.getInstance());
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run(tmpDir.toString(), config, dispatcher);

      doit.run();

      assertTrue("stopped should be reached", stopped.get());
   }

   @Test
   public void run_ALL_role() {
      AtomicBoolean allRan = new AtomicBoolean(false);
      Script allScript = new Script("allScript")
         .then(Cmd.code((input, state) -> {
            allRan.set(true);
            return Result.next(input);
         }));
      Script runScript = new Script("localScript")
         .then(Cmd.sh("pwd"));

      RunConfigBuilder builder = getBuilder();
      builder.addHostAlias("local", getHost().toString());
      builder.addScript(runScript);
      builder.addScript(allScript);
      builder.addHostToRole("role", "local");
      builder.addRoleRun("role", "localScript", new HashMap<>());
      builder.addRoleRun(RunConfigBuilder.ALL_ROLE, "allScript", new HashMap<>());

      RunConfig config = builder.buildConfig(Parser.getInstance());
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run(tmpDir.toString(), config, dispatcher);

      doit.run();

      assertTrue("all script should have run", allRan.get());
   }


   @Test
   public void pwd_in_dollar() {
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("pwd", stream("" +
         "scripts:",
         "  foo:",
         "    - sh: echo \"pwd is:$(pwd)\"",
         "    - echo:",
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
      //TODO post doit validation?
   }

   @Test
   public void test_check_exit_code(){
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("pwd", stream("" +
             "scripts:",
              "  foo:",
              "    - sh: ls /tmp; (exit 42);",
              "    - sh: echo $?",
              "    - sh: doesnotexist",
              "    - sh: echo $?",
              "    - sh: pwd",
              "    - sh: history",
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
   }

   @Test @Ignore
   public void test_reconnect_client4(){
      AtomicBoolean ran = new AtomicBoolean(false);
      StringBuilder sb = new StringBuilder();
      Script script = new Script("test");
      script.then(Cmd.sh("pwd"));
      script.then(new AddPrompt(" closed."));
      script.then(new AddPrompt("We are no longer a registered authentication agent."));
      script.then(Cmd.sh("sudo reboot"));
      script.then(Cmd.sh("whoami"));
      script.then(Cmd.code(((input, state) -> {
         sb.append(input);
         ran.set(true);
         return Result.next("ok");
      })));

      RunConfigBuilder builder = getBuilder();
      builder.addHostAlias("local", "benchuser@benchclient4.perf.lab.eng.rdu2.redhat.com:22");
      builder.addScript(script);
      builder.addHostToRole("role", "local");
      builder.addRoleRun("role", script.getName(), new HashMap<>());

      RunConfig config = builder.buildConfig(Parser.getInstance());
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run(tmpDir.toString(), config, dispatcher);

      JsonServer jsonServer = new JsonServer(doit, 31337);
      jsonServer.start();
      doit.run();
      jsonServer.stop();
      
      assertTrue("expect ran to be true",ran.get());
   }

   private class FakeReboot extends Sh {

      public FakeReboot(){
         super("sleep 10s");
      }
      @Override
      public void run(String input, Context context){
         //send context super.run so it will add the correct callback just like an Sh
         super.run(input,context);
         restartContainer();
         try {
            Thread.sleep(2_000);
         } catch (InterruptedException e) {
            //e.printStackTrace();
         }

      }
      @Override
      public Cmd copy() {
         return new FakeReboot();
      }

   }

   @Test
   public void test_force_reconnect(){
      SshSession session = getSession();
      String out;
      out = session.shSync("echo hi");
      restartContainer();
      try {
         Thread.sleep(10_000);
      } catch (InterruptedException e) {
         e.printStackTrace();
      }
      SshSession copy = session.openCopy();


      assertTrue("expect copy to be open",copy.isOpen());
      assertTrue("expect copy to be ready",copy.isReady());

      assertFalse("expect session to not be open",session.isOpen());
      assertFalse("expect session to not be ready",session.isReady());

      boolean connected = session.ensureConnected();

      assertTrue("expect session to be open",session.isOpen());
      assertTrue("expect session to be ready",session.isReady());
   }

   @Test
   public void test_reconnect() {
      AtomicBoolean ran = new AtomicBoolean(false);
      StringBuilder sb = new StringBuilder();
      Script script = new Script("test");
      script.then(Cmd.sh("whoami"));
      script.then(new FakeReboot());
      script.then(Cmd.sh("pwd"));
      script.then(Cmd.code(((input, state) -> {
         sb.append(input);
         ran.set(true);
         return Result.next("ok");
      })));

      RunConfigBuilder builder = getBuilder();
      builder.addHostAlias("local", getHost().toString());
      //builder.addHostAlias("local", "wreicher:imagine@desk:22");
      builder.addScript(script);
      builder.addHostToRole("role", "local");
      builder.addRoleRun("role", script.getName(), new HashMap<>());

      RunConfig config = builder.buildConfig(Parser.getInstance());
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run(tmpDir.toString(), config, dispatcher);
      doit.run();

      assertTrue("expect ran to be true",ran.get());
      assertEquals("pwd should be /root and not include motd","/root",sb.toString());
   }

   @Test
   public void test_exitCode() {
      Semaphore block = new Semaphore(0);
      Sh cmd = new Sh("whoami; pwd; (exit 42);");
      cmd.then(Cmd.code(((input, state) -> {
         block.release();
         return Result.next("ok");
      })));
      State state = new State(State.RUN_PREFIX);
      ScheduledThreadPoolExecutor scheduled = new ScheduledThreadPoolExecutor(4, runnable -> new Thread(runnable, "scheduled"));
      RunConfigBuilder builder = getBuilder();
      SshSession sshSession = new SshSession(
              getHost().toString(),
              getHost(),
              builder.getKnownHosts(),
              builder.getIdentity(),
              builder.getPassphrase(),
              builder.getTimeout(),
              "",
              scheduled,
              false
      );
      RunConfig config = builder.buildConfig(Parser.getInstance());
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run(tmpDir.toString(), config, dispatcher);
      List<String> errors = new ArrayList<>();
      ScriptContext context = new ScriptContext(sshSession,state,doit,new SystemTimer("test"),cmd,false){
         @Override
         public void error(String message){
            errors.add(message);
            super.error(message);
         }
      };
      cmd.run("",context);
      try{
         block.acquire();
      } catch (InterruptedException e) {
         fail(e.getMessage());
      }
   }

   //TODO run test when connected to the internet (or valid registry)
   //removed because disabling ENV.TMP_DIR
   @Test @Ignore
   public void tmp_env_dir() {
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("pwd", stream("" +
         "scripts:",
         "  foo:",
         "    - set-state: RUN.dir ${{"+RunConfigBuilder.TEMP_DIR+"}}",
         "    - sh: echo 'bar' > ${{"+RunConfigBuilder.TEMP_DIR+"}}/bar.txt",
         "    - sh: cat ${{"+RunConfigBuilder.TEMP_DIR+"}}/bar.txt",
         "    - set-state: RUN.bar",
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

      Object bar = state.get("bar");
      Object dir = state.get("dir");

      assertNotNull("bar should exist in state\n"+state.tree(),bar);
      assertEquals("bar should be the content of the file","bar",bar);

      assertNotNull("dir should exist in state\n"+state.tree(),dir);
      assertFalse("dir should not exist in the container after a run",exists(dir.toString()));


   }


   @Test
   public void invoke_with_state_script_name() {
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      StringBuilder sb = new StringBuilder();

      builder.loadYaml(parser.loadFile("test", stream("" +
         "scripts:",
         "  foo:",
         "  - sh: echo ${{NAME}}",
         "  - script: ${{NAME}}",
         "  bar:",
         "  - sh: echo ${{NAME}}",
         "  - script: foo",
         "    with:",
         "      NAME: biz",
         "  fail:",
         "  - sh: pwd",
         "  biz:",
         "  - sh: echo ${{NAME}}",
         "hosts:",
         "  local: " + getHost(),
         "roles:",
         "  doit:",
         "    hosts: [local]",
         "    run-scripts: [bar]",
         "states:",
         "  NAME: fail"
      )));
      RunConfig config = builder.buildConfig(parser);
      Cmd bar = config.getScript("bar");
      assertNotNull("missing bar script", bar);
      Cmd biz = config.getScript("biz");
      assertNotNull("missing biz script", biz);
      biz.getTail().then(Cmd.code(((input, state) -> {
         sb.append(input);
         return Result.next(input);
      })));
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run(tmpDir.toString(), config, dispatcher);
      doit.run();
      dispatcher.shutdown();
      assertEquals("biz should be called with biz", "biz", sb.toString());
   }

   @Test
   public void json_state_array() {
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();

      builder.loadYaml(parser.loadFile("json", stream("" +
         "scripts:",
         "  foo:",
         "  - for-each: FOO ${{BAR}}",
         "    then:",
         "    - read-state: ${{FOO.biz.buz}}",
         "hosts:",
         "  local: " + getHost(),
         "roles:",
         "  doit:",
         "    hosts: [local]",
         "    run-scripts: [foo]",
         "states:",
         "  BAR: [{biz: {buz: 'one'}},{biz: {buz: 'two'}},{biz: {buz: 'three'}}]"
      )));
      RunConfig config = builder.buildConfig(parser);
      assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());

      Cmd target = config.getScript("foo");
      while (target.getNext() != null && !(target instanceof ReadState)) {
         target = target.getNext();
      }
      List<String> splits = new ArrayList<>();
      if (target instanceof ReadState) {
         target.then(Cmd.code(((input, state) -> {
            splits.add(input);
            return Result.next(input);
         })));
      } else {
         fail("failed to find for-each in script foo");
      }

      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run(tmpDir.toString(), config, dispatcher);

      doit.run();
      dispatcher.shutdown();
      assertEquals("should see 3 entries:\n" + splits.stream().collect(Collectors.joining("\n")), 3, splits.size());
      assertEquals("first entry should be one","one",splits.get(0));
      assertEquals("second entry should be two","two",splits.get(1));
      assertEquals("third entry should be three","three",splits.get(2));
   }

   @Test
   public void signal_in_watch() {
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("signal", stream("" +
            "scripts:",
         "  foo:",
         "    - sh: doSomething",
         "      watch:",
         "        - regex: seeSomething",
         "          then:",
         "            - signal: ${{NAME}}-FOO",
         "      on-signal:",
         "        ${{FAKE}}-FOO:",
         "          - ctrlC:",
         "  bar:",
         "    - wait-for: ${{NAME}}-FOO",
         "    - sh: echo bar > /tmp/bar.txt",
         "    - signal: BAR",
         "  biz:",
         "    - wait-for: BAR",
         "    - sh: echo biz > /tmp/biz.txt",
         "hosts:",
         "  local: " + getHost(),
         "roles:",
         "  doit:",
         "    hosts: [local]",
         "    run-scripts: [foo,bar]",
         "states:",
         "  NAME: signalName"
      )));
      RunConfig config = builder.buildConfig(parser);
      assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
   }

   @Test
   public void array_state_with_watch_in_yaml(){
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("",stream(""+
              "scripts:",
              "  setSignals:",
              "  - for-each: OBJ ${{OBJS}}",
              "    then:",
              "    - set-signal: ${{OBJ.name}}-started 1",
              "  foo:",
              "  - sh: echo 'Starting!'",
              "  - for-each: OBJ ${{OBJS}}",
              "    then:",
              "      - sh: echo ${{OBJ.name}}-started",
              "        watch:",
              "          - regex: started",
              "            then:",
              "             - signal: ${{OBJ.name}}-started",
              "      - sleep: 2s",
              "  - sh: echo 'End!'",
              "  bar:",
              "  - for-each: OBJ ${{OBJS}}",
              "    then:",
              "    - wait-for: ${{OBJ.name}}-started",
              "    - sh: 'echo ${{OBJ.name}}-started has started!'",
              "hosts:",
              "  local: " + getHost(),
              "roles:",
              "  doit:",
              "    hosts: [local]",
              "    setup-scripts: [setSignals]",
              "    run-scripts: [foo, bar]",
              "states:",
              "  OBJS: [{'name': 'one', 'value':'foo'}, {'name': 'two', 'value':'bar'}, {'name': 'three', 'value':'biz'}]"
      )));
      RunConfig config = builder.buildConfig(parser);

      assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());

      Script foo = config.getScript("foo");

      assertTrue("Script should contain 3 top level commands", foo.getThens().size() == 3 );
      assertTrue("Last command should be a Sh cmd", foo.getLastThen() instanceof Sh);
      assertTrue("Last command should not have any thens", foo.getLastThen().hasThens() == false);

      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run(tmpDir.toString(), config, dispatcher);
      doit.run();

      String logContents = readFile(tmpDir.getPath().resolve("run.log"));
      assertTrue("run log is empty", logContents.length() > 0);
      Boolean containsUnsubstituted = logContents.contains("signal: ${{OBJ.name}}-started");
      assertTrue("File contains ${{OBJ.name}}-started", !containsUnsubstituted);



   }

   @Test
   public void suppress_state_logging(){
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("",stream(""+
                      "scripts:",
              "  setStates:",
              "  - for-each: OBJ ${{OBJS}}",
              "    then:",
              "    - set-state: ${{OBJ.name}}-started 1",
              "  foo:",
              "  - sh: echo 'Starting!'",
              "  - for-each: OBJ ${{OBJS}}",
              "    then:",
              "      - sh: echo state ${{OBJ.name}}-started ${{${{OBJ.name}}-started}}",
              "  - sh: echo 'End!'",
              "hosts:",
              "  local: " + getHost(),
              "roles:",
              "  doit:",
              "    hosts: [local]",
              "    setup-scripts: [setStates]",
              "    run-scripts: [foo]",
              "states:",
              "  OBJS: [{'name': 'one', 'value':'foo'}, {'name': 'two', 'value':'bar'}, {'name': 'three', 'value':'biz'}]"
      )));

      Logger root = (Logger) LoggerFactory.getLogger(Run.STATE_LOGGER_NAME);
      //set log level to INFO to disable STATE logger
      root.setLevel(Level.INFO);

      RunConfig config = builder.buildConfig(parser);

      assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());

      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run(tmpDir.toString(), config, dispatcher);

      doit.run();

      String logContents = readFile(tmpDir.getPath().resolve("run.log"));

      Boolean containsStrartingState = logContents.contains("starting state:");
      Boolean containsOutputState = logContents.contains("closing state:");
      assertTrue("File contains starting state", !containsStrartingState);
      assertTrue("File contains closing state", !containsOutputState);

   }

   @Test
   public void signal_in_previous_stage() {
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("signal", stream("" +
            "scripts:",
         "  foo:",
         "    - signal: FOO",
         "  bar:",
         "    - wait-for: FOO",
         "    - sh: echo bar > /tmp/bar.txt",
         "    - signal: BAR",
         "  biz:",
         "    - wait-for: BAR",
         "    - sh: echo biz > /tmp/biz.txt",
         "hosts:",
         "  local: " + getHost(),
         "roles:",
         "  doit:",
         "    hosts: [local]",
         "    setup-scripts: [foo]",
         "    run-scripts: [bar]",
         "    cleanup-scripts: [biz]"
      )));
      RunConfig config = builder.buildConfig(parser);
      assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run(tmpDir.toString(), config, dispatcher);
      doit.run();
      assertTrue("bar did not run", exists("/tmp/bar.txt"));
      assertTrue("biz did not run", exists("/tmp/biz.txt"));
   }

   @Test
   public void onSignal_already_signaled(){
      //TODO
   }

   @Test
   public void watch_wait_for_slow_watcher_to_finish() {
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
      echo.then(Cmd.sleep("5s")); // added because docker does not update tail before DONE sends CtrlC
      echo.then(Cmd.signal("FOO_DONE"));
      echo.then(Cmd.waitFor("BAR_READY"));
      echo.then(Cmd.sh("echo 'bar1' >> /tmp/bar.txt"));
      echo.then(Cmd.sh("echo 'bar2' >> /tmp/bar.txt"));
      echo.then(Cmd.sh("echo 'bar3' >> /tmp/bar.txt"));
      echo.then(Cmd.sleep("5s")); // added because docker does not update tail before DONE sends CtrlC
      echo.then(Cmd.signal("BAR_DONE"));

      RunConfigBuilder builder = getBuilder();
      builder.addHostAlias("local", getHost().toString());
      builder.addScript(tail);
      builder.addScript(echo);

      builder.addHostToRole("role", "local");
      builder.addRoleRun("role", "tail", new HashMap<>());
      builder.addRoleRun("role", "echo", new HashMap<>());

      RunConfig config = builder.buildConfig(Parser.getInstance());
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run(tmpDir.toString(), config, dispatcher);
      doit.run();

      assertEquals("foo buffer should see all 3 lines","foo1foo2foo3",fooOutput.toString());
      assertEquals("bar buffer should see all 3 lines","bar1bar2bar3",barOutput.toString());
   }


   @Test
   public void sh_output_trim() {
      StringBuilder output = new StringBuilder();
      Script runScript = new Script("cmd-output-trim");
      runScript
         .then(Cmd.sh("if true; then echo SUCCESS; fi")
            .then(Cmd.code((input, state) -> {
               output.append(input);
               return Result.next(input);
            }))
         );
      RunConfigBuilder builder = getBuilder();
      builder.addHostAlias("local", getHost().toString());
      builder.addScript(runScript);
      builder.addHostToRole("role", "local");
      builder.addRoleRun("role", "cmd-output-trim", new HashMap<>());
      RunConfig config = builder.buildConfig(Parser.getInstance());
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run(tmpDir.toString(), config, dispatcher);
      doit.run();
      boolean hasNewLine = false;
      for (int i = 0; i < output.length(); i++) {
         char c = output.charAt(i);
         hasNewLine = hasNewLine || '\n' == c || '\r' == c;
      }
      assertFalse("output should not have \\r or \\n", hasNewLine);
   }

   @Test
   public void echo_exitStatus() {
      StringBuilder pwdFirstChildInput = new StringBuilder();
      StringBuilder echoChildInput = new StringBuilder();
      StringBuilder pwdChildInput = new StringBuilder();
      StringBuilder pwdSiblingInput = new StringBuilder();
      Script runScript = new Script("run-echo-exitStatus");
      runScript
         .then(Cmd.sh("pwd")
            .then(Cmd.code((input, state) ->{
               pwdFirstChildInput.append(input);
               return Result.next(input);
            }))
            .then(Cmd.sh("echo $?")
               .then(Cmd.code((input, state) -> {
                  echoChildInput.append(input);
                  return Result.next(input);
               }))
            )
            .then(Cmd.code((input, state) -> {
               pwdChildInput.append(input);
               return Result.next(input);
            }))
         );
      runScript.then(Cmd.code((input, state) -> {
         pwdSiblingInput.append(input);
         return Result.next(input);
      }));

      RunConfigBuilder builder = getBuilder();
      builder.addHostAlias("local", getHost().toString());
      builder.addScript(runScript);
      builder.addHostToRole("role", "local");
      builder.addRoleRun("role", "run-echo-exitStatus", new HashMap<>());
      RunConfig config = builder.buildConfig(Parser.getInstance());
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run(tmpDir.toString(), config, dispatcher);
      doit.run();
      assertEquals("echo child should see echo output", "0", echoChildInput.toString());
      assertEquals("pwd child should see echo output", "0", pwdChildInput.toString());
      assertEquals("pwd sibling should see pwd", pwdFirstChildInput.toString(), pwdSiblingInput.toString());
   }

   //fails the first time it is run after sshd restart?
   @Test(timeout = 45_000)
   public void forEach_lastCommand() {
      AtomicInteger counter = new AtomicInteger(0);
      Script runScript = new Script("run-for-each");
      runScript
         .then(Cmd.code((input, state) -> {//force the input for next command
            return Result.next("1\n2\n3");
         }))
         .then(Cmd.forEach("FOO")
            .then(Cmd.code((input, state) -> {
               counter.incrementAndGet();
               return Result.next(input);
            }))
         );

      RunConfigBuilder builder = getBuilder();


      builder.addHostAlias("local", getHost().toString());
      builder.addScript(runScript);
      builder.addHostToRole("role", "local");
      builder.addRoleRun("role", "run-for-each", new HashMap<>());

      RunConfig config = builder.buildConfig(Parser.getInstance());

      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run(tmpDir.toString(), config, dispatcher);

      doit.run();

      assertEquals("for-each should run 3 times", 3, counter.get());
   }

   @Test
   public void regex_empty() {
      Script script = new Script("script");

      script.then(
         Cmd.sh("for line in 0 1 2; do if expr $line \">\" 50 > /dev/null; then echo SIGNIFICANT ; break; fi done;")
            .then(Cmd.regex("^$")
            )
      );


      RunConfigBuilder builder = getBuilder();


      builder.addHostAlias("local", getHost().toString());
      builder.addScript(script);

      builder.addHostToRole("role", "local");
      builder.addRoleRun("role", "script", new HashMap<>());

      RunConfig config = builder.buildConfig(Parser.getInstance());
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run(tmpDir.toString(), config, dispatcher);

      doit.run();

   }

   @Test(timeout = 45_000)
   public void abort_callsCleanup() {
      StringBuilder setup = new StringBuilder();
      StringBuilder postAbort = new StringBuilder();
      StringBuilder run = new StringBuilder();
      StringBuilder cleanup = new StringBuilder();
      AtomicBoolean cleanupCalled = new AtomicBoolean(false);
      Script setupScript = new Script("setup-abort");
      setupScript.then(Cmd.code((input, sate) -> {
         setup.append(System.currentTimeMillis());
         return Result.next("setup-abort @ " + System.currentTimeMillis());
      }));
      setupScript.then(Cmd.abort("abort-aborted", false));
      setupScript.then(Cmd.code((input, sate) -> {
         postAbort.append(System.currentTimeMillis());
         return Result.next("post-abort called");
      }));
      Script runScript = new Script("run-abort");
      runScript.then(Cmd.code((input, state) -> {
         run.append(System.currentTimeMillis());
         return Result.next(input);
      }));
      Script cleanupScript = new Script("cleanup-abort");
      cleanupScript.then(Cmd.log("fooooooooooo"));
      cleanupScript.then(Cmd.code((input, state) -> {
         cleanupCalled.set(true);
         cleanup.append(System.currentTimeMillis());
         return Result.next("invoked cleanup-abort " + System.currentTimeMillis());
      }));
      RunConfigBuilder builder = getBuilder();


      builder.addHostAlias("local", getHost().toString());
      builder.addScript(setupScript);
      builder.addScript(runScript);
      builder.addScript(cleanupScript);

      builder.addHostToRole("role", "local");
      builder.addRoleSetup("role", "setup-abort", new HashMap<>());
      builder.addRoleRun("role", "run-abort", new HashMap<>());
      builder.addRoleCleanup("role", "cleanup-abort", new HashMap<>());

      RunConfig config = builder.buildConfig(Parser.getInstance());
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run(tmpDir.toString(), config, dispatcher);

      doit.run();

      assertFalse("setup not called:" + setup.toString() + "||", setup.length() == 0);
      assertTrue("postAbort should not be called:" + postAbort.toString() + "||", postAbort.length() == 0);
      assertTrue("run should not be called:" + run.toString() + "||", run.length() == 0);
      assertTrue("cleanup should be called",cleanupCalled.get());
      assertFalse("cleanup not called:" + cleanup.toString() + "||", cleanup.length() == 0);

   }

   @Test
   public void allStagesInvoked() {
      StringBuilder setup = new StringBuilder();
      StringBuilder run = new StringBuilder();
      StringBuilder cleanup = new StringBuilder();

      Script setupScript = new Script("setup");
      setupScript.then(Cmd.code((input, sate) -> {
         setup.append(System.currentTimeMillis());
         return Result.next(input);
      }));
      Script runScript = new Script("run");
      runScript.then(Cmd.code((input, state) -> {
         run.append(System.currentTimeMillis());
         return Result.next(input);
      }));
      Script cleanupScript = new Script("cleanup");
      cleanupScript.then(Cmd.code((input, state) -> {
         cleanup.append(System.currentTimeMillis());
         return Result.next(input);
      }));
      RunConfigBuilder builder = getBuilder();


      builder.addHostAlias("local", getHost().toString());
      builder.addScript(setupScript);
      builder.addScript(runScript);
      builder.addScript(cleanupScript);

      builder.addHostToRole("role", "local");
      builder.addRoleSetup("role", "setup", new HashMap<>());
      builder.addRoleRun("role", "run", new HashMap<>());
      builder.addRoleCleanup("role", "cleanup", new HashMap<>());

      RunConfig config = builder.buildConfig(Parser.getInstance());
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run(tmpDir.toString(), config, dispatcher);

      doit.run();

      assertFalse("setup not called", setup.length() == 0);
      assertFalse("run not called", run.length() == 0);
      assertFalse("cleanup not called", cleanup.length() == 0);

   }

   @Test
   public void watch_only_ctrlC_if_active(){
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("",stream(""+
         "scripts:",
         "  foo:",
         "  - sleep: 1s",
         "  - sh: ",
         "      command: top",
         "      silent: true",
         "    watch:",
         "    - regex: Task",
         "      then:",
         "      - ctrlC",
         "      - sleep: 3s",
         "      - ctrlC",
         "      - set-state: RUN.FOO ${{RUN.FOO:}}-worked",
         "    - regex: PID",
         "      then:",
         "      - set-state: RUN.PID ${{RUN.PID:}}-found",
         "  - sleep: 5s",
         "hosts:",
         "  local: " + getHost(),
         "roles:",
         "  doit:",
         "    hosts: [local]",
         "    run-scripts: [foo]",
         "states:",
         "  alpha: [ {name: \"ant\"}, {name: \"apple\"} ]",
         "  bravo: [ {name: \"bear\"}, {name: \"bull\"} ]",
         "  charlie: {name: \"cat\"}"
      )));

      RunConfig config = builder.buildConfig(parser);

      Dispatcher dispatcher = new Dispatcher();

      List<String> signals = new ArrayList<>();

      dispatcher.addContextObserver(new ContextObserver() {
         @Override
         public void preStart(Context context, Cmd command) {
            if(command instanceof CtrlSignal){
               signals.add(command.toString());
            }
         }

         @Override
         public void preStop(Context context, Cmd command, String output) {

         }
      });
      Run doit = new Run(tmpDir.toString(), config, dispatcher);
      doit.run();
      dispatcher.shutdown();

      assertEquals("should only run one signal command",1,signals.size());
      assertEquals("watcher should finish even if watched already stopped","-found",config.getState().get("PID"));
      assertEquals("skipped singals for stopped watcher should not stop subsequent commands","-worked",config.getState().get("FOO"));
   }

   @Test
   public void watch_invoke_count() {

      List<String> lines = new ArrayList<>();
      StringBuilder tailed = new StringBuilder();

      Script tail = new Script("tail");
      tail.then(Cmd.sh("echo '!' > /tmp/foo.txt"));
      tail.then(Cmd.signal("ready"));
      tail.then(Cmd.sh("tail -f /tmp/foo.txt")
         .watch(Cmd.code((input, state) -> {
            lines.add(input);
            return Result.next(input);
         }))
         .watch(Cmd.regex("foo")
            .then(Cmd.code(((input, state) -> {
                  return Result.next(input);
               }))
            ))
         .watch(Cmd.regex("bar").then(Cmd.ctrlC()))
      );
      tail.then(Cmd.code(((input, state) -> {
         tailed.append(input);
         return Result.next(input);
      })));

      Script send = new Script("send");
      send.then(Cmd.waitFor("ready"));
      send.then(Cmd.sh("echo 'foo' >> /tmp/foo.txt"));
      send.then(Cmd.sleep("1s"));
      send.then(Cmd.sh("echo 'bar' >> /tmp/foo.txt"));
      send.then(Cmd.sleep("2s"));
      send.then(Cmd.sh("echo 'biz' >> /tmp/foo.txt"));

      RunConfigBuilder builder = getBuilder();

      builder.addHostAlias("local", getHost().toString());
      builder.addScript(tail);
      builder.addScript(send);

      builder.addHostToRole("role", "local");
      builder.addRoleRun("role", "tail", new HashMap<>());
      builder.addRoleRun("role", "send", new HashMap<>());

      RunConfig config = builder.buildConfig(Parser.getInstance());
      Dispatcher dispatcher = new Dispatcher();
      Run run = new Run(tmpDir.toString(), config, dispatcher);

      run.run();
      run.writeRunJson();

      assertFalse("should stop tail before biz", tailed.toString().contains("biz"));
      //TODO fix the 4th entry is empty that occurs when SuffixStream removes prompt but not preceeding \r\n
      //TODO fails when watch gets empty strings at start and end of watching [ , !, foo, bar, ]
      //assertEquals("lines should have 3 entries:"+lines,3,lines.size());
      assertEquals("lines[0] should be !:" + lines, "!", lines.get(0));
      assertEquals("lines[1] should be foo:" + lines, "foo", lines.get(1));
      assertEquals("lines[2] should be bar:" + lines, "bar", lines.get(2));
   }


   //Can fail when adding empty string before or after end of input
   //@Test(timeout = 45_000)
   @Test
   public void ctrlCTail() {
      for(int i=0; i<1; i++) {
         List<String> lines = new ArrayList<>();
         StringBuilder tailed = new StringBuilder();

         Script tail = new Script("tail");
         tail.then(Cmd.sh("echo '!' > /tmp/foo.txt"));
         tail.then(Cmd.signal("ready"));
         tail.then(Cmd.sh("tail -f /tmp/foo.txt")
            .watch(Cmd.code((input, state) -> {
               lines.add(input);
               return Result.next(input);
            }))
            .watch(Cmd.regex("bar").then(Cmd.ctrlC()))
         );
         tail.then(Cmd.code(((input, state) -> {
            tailed.append(input);
            return Result.next(input);
         })));

         Script send = new Script("send");
         send.then(Cmd.waitFor("ready"));
         send.then(Cmd.sh("echo 'foo' >> /tmp/foo.txt"));
         send.then(Cmd.sleep("1s"));
         send.then(Cmd.sh("echo 'bar' >> /tmp/foo.txt"));
         send.then(Cmd.sleep("2s"));
         send.then(Cmd.sh("echo 'biz' >> /tmp/foo.txt"));

         RunConfigBuilder builder = getBuilder();

         builder.addHostAlias("local", getHost().toString());
         builder.addScript(tail);
         builder.addScript(send);

         builder.addHostToRole("role", "local");
         builder.addRoleRun("role", "tail", new HashMap<>());
         builder.addRoleRun("role", "send", new HashMap<>());

         RunConfig config = builder.buildConfig(Parser.getInstance());

         Dispatcher dispatcher = new Dispatcher();
         Run run = new Run(tmpDir.toString(), config, dispatcher);

         run.run();


         assertFalse("should stop tail before biz", tailed.toString().contains("biz"));
         //TODO fix the 4th entry is empty that occurs when SuffixStream removes prompt but not preceeding \r\n
         assertTrue("lines should have 3+ entries:" + lines, lines.size() >= 3);
         assertEquals("lines[0] should be !:" + lines, "!", lines.get(0));
         assertEquals("lines[1] should be foo:" + lines, "foo", lines.get(1));
         assertEquals("lines[2] should be bar:" + lines, "bar", lines.get(2));
      }
   }

   @Test
   public void testTwoSetupNotSkipSecond() {
      final StringBuilder first = new StringBuilder();
      final StringBuilder second = new StringBuilder();

      RunConfigBuilder builder = getBuilder();

      Script firstScript = new Script("first");
      firstScript.then(Cmd.code((input, state) -> {
         first.append(System.currentTimeMillis());
         return Result.skip(input);
      }));

      Script secondScript = new Script("second");
      secondScript.then(Cmd.sleep("500"));//to ensure second is > first if called
      secondScript.then(Cmd.code((input, state) -> {
         second.append(System.currentTimeMillis());
         return Result.next(input);
      }));

      builder.addScript(firstScript);
      builder.addScript(secondScript);
      builder.addHostAlias("local", getHost().toString());//+testServer.getPort());
      builder.addHostToRole("role", "local");
      builder.addRoleSetup("role", "first", new HashMap<>());
      builder.addRoleSetup("role", "second", new HashMap<>());


      RunConfig config = builder.buildConfig(Parser.getInstance());
      Dispatcher dispatcher = new Dispatcher();
      Run run = new Run(tmpDir.toString(), config, dispatcher);
      run.run();

      assertFalse("first should be called but isEmpty", first.toString().isEmpty());
      assertFalse("second should be called but isEmtpy", second.toString().isEmpty());

      assertTrue("first should be called before second: first=" + first.toString() + " second=" + second.toString(), first.toString().compareTo(second.toString()) < 0);
   }

   @Test(timeout = 45_000)
   public void testDone() {
      final StringBuilder first = new StringBuilder();
      final AtomicLong cleanupTimer = new AtomicLong();
      final AtomicBoolean staysFalse = new AtomicBoolean(false);
      RunConfigBuilder builder = getBuilder();

      Script runSetup = new Script("run-setup");
      runSetup
         .then(Cmd.sh("pwd"))
         .then(Cmd.sh("ls -al ~/.ssh/"));

      Script runDone = new Script("run-done");

      runDone
         .then(Cmd.sh("echo foo > /tmp/foo.txt"))
         .then(Cmd.queueDownload("/tmp/foo.txt"))
         .then(Cmd.sleep("2_000"))
         .then(Cmd.log("done waiting"))
         .then(Cmd.done())
         .then(Cmd.code((input, state) -> {
            staysFalse.set(true);
            return Result.next(input);
         }));
      Script runWait = new Script("run-wait");
      runWait.then(Cmd.waitFor("NEVER"));

      Script runSignal = new Script("run-signal");
      runSignal.then(Cmd.sleep("30s")).then(Cmd.signal("NEVER"));

      Script cleanup = new Script("post-run-cleanup");
      cleanup.then(Cmd.code((input, state) -> {
         first.append(System.currentTimeMillis());
         cleanupTimer.set(System.currentTimeMillis());
         return Result.next(input);
      }));

      builder.addScript(runSetup);
      builder.addScript(runDone);
      builder.addScript(runWait);
      builder.addScript(runSignal);
      builder.addScript(cleanup);

      builder.addHostAlias("local", getHost().toString());//+testServer.getPort());
      builder.addHostToRole("role", "local");

      builder.addRoleSetup("role","run-setup",new HashMap<>());
      builder.addRoleRun("role", "run-done", new HashMap<>());
      builder.addRoleRun("role", "run-wait", new HashMap<>());
      builder.addRoleRun("role", "run-signal", new HashMap<>());

      builder.addRoleCleanup("role", "post-run-cleanup", new HashMap<>());


      RunConfig config = builder.buildConfig(Parser.getInstance());
      Dispatcher dispatcher = new Dispatcher();
      Run run = new Run(tmpDir.toString(), config, dispatcher);
      long start = System.currentTimeMillis();

      JsonServer server = new JsonServer(run);
      server.start();
      run.run();
      server.stop();

      assertFalse("script should not invoke beyond a done", staysFalse.get());
      assertTrue("cleanupTimer should be > 0", cleanupTimer.get() > 0);
      assertTrue("done should stop before NEVER is signalled", cleanupTimer.get() - start < 30_000);

      File outputPath = new File(run.getOutputPath());
      File downloaded = new File(outputPath.getAbsolutePath(), getHost().getHostName() + "/foo.txt");

      assertTrue("queue-download should execute despite done", downloaded.exists());
   }

   @Test
   public void timer_resolve_with_reference(){
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("",stream(""+
            "scripts:",
         "  foo:",
         "  - sh: sleep 10s",
         "    with:",
         "      data:  ${{alpha[0]}}",
         "    timer:",
         "      2s:",
         "      - set-state: RUN.timer ${{data.name}}",
         "hosts:",
         "  local: " + getHost(),
         "roles:",
         "  doit:",
         "    hosts: [local]",
         "    run-scripts: [foo]",
         "states:",
         "  alpha: [ {name: \"ant\"}, {name: \"apple\"} ]",
         "  bravo: [ {name: \"bear\"}, {name: \"bull\"} ]",
         "  charlie: {name: \"cat\"}"
      )));

      RunConfig config = builder.buildConfig(parser);

      assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());

      Dispatcher dispatcher = new Dispatcher();

      List<String> signals = new ArrayList<>();

      Run doit = new Run(tmpDir.toString(), config, dispatcher);
      doit.run();
      dispatcher.shutdown();

      State state = config.getState();

      assertTrue("state should have timer",state.has("timer"));
      assertEquals("timer should be ant","ant",state.getString("timer"));

   }

   @Test
   public void host_with_password(){
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("",stream(""+
             "scripts:",
              "  foo:",
              "  - set-state: RUN.worked true",
              "hosts:",
              "  local: " + getPasswordHost(),
              "roles:",
              "  doit:",
              "    hosts: [local]",
              "    run-scripts: [foo]",
              "states:",
              "  alpha: [ {name: \"ant\"}, {name: \"apple\"} ]",
              "  bravo: [ {name: \"bear\"}, {name: \"bull\"} ]",
              "  charlie: {name: \"cat\"}"
      )));

      RunConfig config = builder.buildConfig(parser);
      assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());

      Dispatcher dispatcher = new Dispatcher();

      List<String> signals = new ArrayList<>();

      Run doit = new Run(tmpDir.toString(), config, dispatcher);
      doit.run();
      dispatcher.shutdown();

      State state = config.getState();

      assertTrue("state should have worked",state.has("worked"));
      assertEquals("timer should be ant","true",state.getString("worked"));
   }


   @Test
   public void timer_resolve_with(){
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("",stream(""+
            "scripts:",
         "  foo:",
         "  - sh: sleep 10s",
         "    with:",
         "      data:  {name: \"ant\"}",
         "    timer:",
         "      2s:",
         "      - set-state: RUN.timer ${{data.name}}",
         "hosts:",
         "  local: " + getHost(),
         "roles:",
         "  doit:",
         "    hosts: [local]",
         "    run-scripts: [foo]",
         "states:",
         "  alpha: [ {name: \"ant\"}, {name: \"apple\"} ]",
         "  bravo: [ {name: \"bear\"}, {name: \"bull\"} ]",
         "  charlie: {name: \"cat\"}"
      )));

      RunConfig config = builder.buildConfig(parser);

      Dispatcher dispatcher = new Dispatcher();

      List<String> signals = new ArrayList<>();

      Run doit = new Run(tmpDir.toString(), config, dispatcher);
      doit.run();
      dispatcher.shutdown();

      State state = config.getState();

      assertTrue("state should have timer",state.has("timer"));
      assertEquals("timer should be ant","ant",state.getString("timer"));

   }
   @Test
   public void testTimer() {
      final StringBuilder first = new StringBuilder();
      final StringBuilder second = new StringBuilder();
      RunConfigBuilder builder = getBuilder();
      Script script = new Script("run-timer");
      script.then(
         Cmd.sleep("4_000").addTimer(2_000, Cmd.code(((input, state) -> {
            first.append(input);
            return Result.next(input);
         }))).addTimer(10_000, Cmd.code(((input, state) -> {
            second.append(input);
            return Result.next(input);
         })))
      );

      builder.addScript(script);
      builder.addHostAlias("local", getHost().toString());//+testServer.getPort());
      builder.addHostToRole("role", "local");
      builder.addRoleRun("role", "run-timer", new HashMap<>());

      RunConfig config = builder.buildConfig(Parser.getInstance());

      Dispatcher dispatcher = new Dispatcher();
      Run run = new Run(tmpDir.toString(), config, dispatcher);

      run.run();

      String firstString = first.toString();
      String secondString = second.toString();
      assertEquals("first should contain the 2000 timeout value", "2000", firstString);
      assertEquals("second should not run because the parent command finished", "", secondString);
   }

   @Test
   public void testEnvCapture() {

      final StringBuilder runEnvBuffer = new StringBuilder();

      RunConfigBuilder builder = getBuilder();


//"${JAVA_OPTS} ${{OPTS:}}"
//
//export JAVA_OPTS="${JAVA_OPTS} -Djava.net.preferIPv4Stack=true -Djboss.modules.system.pkgs=org.jboss.byteman -Djava.awt.headless=true -Djava.net.preferIPv4Stack=true -Dorg.jboss.resolver.warning=true -Dsun.rmi.dgc.client.gcInterval=3600000 -Dsun.rmi.dgc.server.gcInterval=3600000 -Dcom.arjuna.ats.arjuna.coordinator.CoordinatorEnvironmentBean.asyncPrepare=true -Dcom.arjuna.ats.arjuna.coordinator.CoordinatorEnvironmentBean.maxTwoPhaseCommitThreads=4 -Dcom.arjuna.ats.arjuna.coordinator.CoordinatorEnvironmentBean.asyncCommit=true -Djboss.server.default.config=standalone-full.xml -Dinfinispan.unsafe.allow_jdk8_chm=true -Dorg.apache.jasper.compiler.Parser.OPTIMIZE_SCRIPTLETS=true -Dorg.apache.cxf.io.CachedOutputStream.Threshold=4096000 -XX:+UseParallelOldGC -XX:ParallelGCThreads=32 -XX:+ParallelRefProcEnabled -Xmx12g -Xms12g -XX:MaxNewSize=5g -XX:NewSize=5g -XX:MetaspaceSize=256m -Xloggc:/tmp/gclogs/server_`date +%Y%m%d_%H%M%S`.gclog -Dactivemq.artemis.client.global.thread.pool.max.size=120 -XX:+UnlockCommercialFeatures -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints -XX:+FlightRecorder -XX:StartFlightRecording=compress=false,delay=0s,duration=24h,filename=/perf1/hprof/flight_record_`date +%Y%m%d_%H%M%S`.jfr,settings=lowOverhead"
      Script setupScript = new Script("setup-env");
      setupScript.then(Cmd.sh("env", false));
      setupScript.then(Cmd.sh("export FOO=\"FOO\""));
      setupScript.then(Cmd.sh("unset PROMPT_COMMAND"));
      setupScript.then(Cmd.sh("export VERTX_HOME=\"/tmp\""));
      setupScript.then(Cmd.sh("export JAVA_OPTS=\"${JAVA_OPTS} -Djava.net.preferIPv4Stack=true -Djboss.modules.system.pkgs=org.jboss.byteman -Djava.awt.headless=true -Djava.net.preferIPv4Stack=true -Dorg.jboss.resolver.warning=true -Dsun.rmi.dgc.client.gcInterval=3600000 -Dsun.rmi.dgc.server.gcInterval=3600000 -Dcom.arjuna.ats.arjuna.coordinator.CoordinatorEnvironmentBean.asyncPrepare=true -Dcom.arjuna.ats.arjuna.coordinator.CoordinatorEnvironmentBean.maxTwoPhaseCommitThreads=4 -Dcom.arjuna.ats.arjuna.coordinator.CoordinatorEnvironmentBean.asyncCommit=true -Djboss.server.default.config=standalone-full.xml -Dinfinispan.unsafe.allow_jdk8_chm=true -Dorg.apache.jasper.compiler.Parser.OPTIMIZE_SCRIPTLETS=true -Dorg.apache.cxf.io.CachedOutputStream.Threshold=4096000 -XX:+UseParallelOldGC -XX:ParallelGCThreads=32 -XX:+ParallelRefProcEnabled -Xmx12g -Xms12g -XX:MaxNewSize=5g -XX:NewSize=5g -XX:MetaspaceSize=256m -Xloggc:/tmp/gclogs/server_`date +%Y%m%d_%H%M%S`.gclog -Dactivemq.artemis.client.global.thread.pool.max.size=120 -XX:+UnlockCommercialFeatures -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints -XX:+FlightRecorder -XX:StartFlightRecording=compress=false,delay=0s,duration=24h,filename=/perf1/hprof/flight_record_`date +%Y%m%d_%H%M%S`.jfr,settings=lowOverhead\""));
      Script runScript = new Script("run-env").then(Cmd.log("post-run-env-script"));
      runScript.then(Cmd.sh("env", false).then(Cmd.code((input, state) -> {
         runEnvBuffer.append(input);
         return Result.next(input);
      })));
      builder.addScript(setupScript);
      builder.addScript(runScript);

      builder.addHostAlias("local", getHost().toString());//+testServer.getPort());
      builder.addHostToRole("role", "local");
      builder.addRoleRun("role", "run-env", new HashMap<>());
      builder.addRoleSetup("role", "setup-env", new HashMap<>());

      RunConfig config = builder.buildConfig(Parser.getInstance());

      Dispatcher dispatcher = new Dispatcher();
      Run run = new Run(tmpDir.toString(), config, dispatcher);

      run.run();

      String runEnv = runEnvBuffer.toString();

      assertTrue("run-env output should contain FOO=FOO but was\n" + runEnv, runEnv.contains("FOO=FOO"));
      assertTrue("run-env output should contain VERTX_HOME=/tmp", runEnv.contains("VERTX_HOME=/tmp"));
      assertTrue("run-env output should contain JAVA_OPTS", runEnv.contains("JAVA_OPTS"));
   }

}
