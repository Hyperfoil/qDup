package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Code;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.qdup.cmd.ContextObserver;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.cmd.Result;
import io.hyperfoil.tools.qdup.cmd.Script;
import io.hyperfoil.tools.qdup.cmd.impl.CtrlSignal;
import io.hyperfoil.tools.qdup.cmd.impl.ReadState;
import io.hyperfoil.tools.qdup.config.CmdBuilder;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.waml.WamlParser;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import io.hyperfoil.tools.yaup.AsciiArt;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.Assert.*;


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

      RunConfig config = builder.buildConfig();
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run("/tmp", config, dispatcher);

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

      RunConfig config = builder.buildConfig();
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run("/tmp", config, dispatcher);

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
         "    - sh: echo \"pwd is: $(pwd)\"",
         "    - echo:",
         "hosts:",
         "  local: " + getHost(),
         "roles:",
         "  doit:",
         "    hosts: [local]",
         "    run-scripts: [foo]"
      ),true));
      RunConfig config = builder.buildConfig();
      assertFalse("runConfig errors:\n" + config.getErrors().stream().collect(Collectors.joining("\n")), config.hasErrors());
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run("/tmp", config, dispatcher);

      doit.run();
   }


   @Test
   public void invoke_with_state_script_name() {
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      WamlParser wamlParser = new WamlParser();

      StringBuilder sb = new StringBuilder();

      wamlParser.load("test", stream("" +
            "scripts:",
         "  foo:",
         "  - sh: echo ${{NAME}}",
         "  - script: ${{NAME}}",
         "  bar:",
         "  - sh: echo ${{NAME}}",
         "  - invoke: foo",
         "      with:",
         "        NAME: biz",
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
      ));
      builder.loadWaml(wamlParser);
      RunConfig config = builder.buildConfig();
      Cmd bar = config.getScript("bar");
      assertNotNull("missing bar script", bar);
      Cmd biz = config.getScript("biz");
      assertNotNull("missing biz script", biz);
      biz.getTail().then(Cmd.code(((input, state) -> {
         sb.append(input);
         return Result.next(input);
      })));
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run("/tmp", config, dispatcher);
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
      ),true));
      RunConfig config = builder.buildConfig();
      assertFalse("runConfig errors:\n" + config.getErrors().stream().collect(Collectors.joining("\n")), config.hasErrors());

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
      Run doit = new Run("/tmp", config, dispatcher);

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
      ),true));
      RunConfig config = builder.buildConfig();
      assertFalse("runConfig errors:\n" + config.getErrors().stream().collect(Collectors.joining("\n")), config.hasErrors());
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
      ),true));
      RunConfig config = builder.buildConfig();
      assertFalse("runConfig errors:\n" + config.getErrors().stream().collect(Collectors.joining("\n")), config.hasErrors());
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run("/tmp", config, dispatcher);
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

      RunConfig config = builder.buildConfig();
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run("/tmp", config, dispatcher);
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
      RunConfig config = builder.buildConfig();
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run("/tmp", config, dispatcher);
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
      RunConfig config = builder.buildConfig();
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run("/tmp", config, dispatcher);
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

      RunConfig config = builder.buildConfig();

      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run("/tmp", config, dispatcher);

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

      RunConfig config = builder.buildConfig();
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run("/tmp", config, dispatcher);

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

      RunConfig config = builder.buildConfig();
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run("/tmp", config, dispatcher);

      doit.run();

      assertFalse("setup not called:" + setup.toString() + "||", setup.length() == 0);
      assertTrue("postAbort should not be called:" + postAbort.toString() + "||", postAbort.length() == 0);
      assertTrue("run should not be called:" + run.toString() + "||", run.length() == 0);
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

      RunConfig config = builder.buildConfig();
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run("/tmp", config, dispatcher);

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
      ),false));

      RunConfig config = builder.buildConfig();

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
      Run doit = new Run("/tmp", config, dispatcher);
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

      RunConfig config = builder.buildConfig();
      Dispatcher dispatcher = new Dispatcher();
      Run run = new Run("/tmp", config, dispatcher);

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

         RunConfig config = builder.buildConfig();

         Dispatcher dispatcher = new Dispatcher();
         Run run = new Run("/tmp", config, dispatcher);

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


      RunConfig config = builder.buildConfig();
      Dispatcher dispatcher = new Dispatcher();
      Run run = new Run("/tmp", config, dispatcher);
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


      RunConfig config = builder.buildConfig();
      Dispatcher dispatcher = new Dispatcher();
      Run run = new Run("/tmp", config, dispatcher);
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
//
      downloaded.delete();
      downloaded.getParentFile().delete();
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

      RunConfig config = builder.buildConfig();

      Dispatcher dispatcher = new Dispatcher();
      Run run = new Run("/tmp", config, dispatcher);

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

      RunConfig config = builder.buildConfig();

      Dispatcher dispatcher = new Dispatcher();
      Run run = new Run("/tmp", config, dispatcher);

      run.run();

      String runEnv = runEnvBuffer.toString();

      assertTrue("run-env output should contain FOO=FOO but was\n" + runEnv, runEnv.contains("FOO=FOO"));
      assertTrue("run-env output should contain VERTX_HOME=/tmp", runEnv.contains("VERTX_HOME=/tmp"));
      assertTrue("run-env output should contain JAVA_OPTS", runEnv.contains("JAVA_OPTS"));
   }
}
