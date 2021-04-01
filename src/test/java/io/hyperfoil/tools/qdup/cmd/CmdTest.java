package io.hyperfoil.tools.qdup.cmd;

import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.SshTestBase;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import io.hyperfoil.tools.yaup.json.Json;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class CmdTest extends SshTestBase {

   @Test
   public void walk_single_then(){
      AtomicInteger counter = new AtomicInteger(0);
      BiFunction<Cmd,Boolean,Void> consumer = (cmd, watching)->{counter.incrementAndGet();return null;};

      Cmd cmd = Cmd.sh("ls").then(Cmd.sh("pwd"));
      cmd.walk(false,consumer);

      assertEquals("walk should run twice",2,counter.get());
   }
   @Test
   public void walk_then_and_else(){
      AtomicInteger counter = new AtomicInteger(0);
      BiFunction<Cmd,Boolean,Void> consumer = (cmd, watching)->{counter.incrementAndGet();return null;};

      Cmd cmd = Cmd.regex("foo").onElse(Cmd.sh("ls")).then(Cmd.sh("pwd"));
      cmd.walk(false,consumer);

      assertEquals("walk should run 3 times",3,counter.get());
   }

   @Test
   public void populateStateVariables_part_of_path() {
      State state = new State("");
      state.set("FOO", "alpha");

      String response = Cmd.populateStateVariables("/tmp/${{FOO}}/bravo", null, state, null);
      assertEquals("expected value", "/tmp/alpha/bravo", response);
   }


   @Test
   public void populateStateVariables_simple_arithmetic() {
      State state = new State("");
      state.set("FOO", "2");
      state.set("BAR", "2");
      state.set("BIZ", "3");

      String response = Cmd.populateStateVariables("${{2 * (2 + 3)}}", null, state, null);
      assertEquals("expected value", "10", response);
   }

   @Test
   public void populateStateVariable_javascript_using_state_json_member_from_global(){
      State state = new State("");
      state.set("FOO", Json.fromString("{\"bar\" : 2}"));
      state.set("BAR", "2");
      state.set("BIZ", "3");

      String response = Cmd.populateStateVariables("${{=FOO.bar * (BAR + BIZ)}}", null, state, null);
      assertEquals("expected value", "10", response);
   }

   @Test
   public void populateStateVariable_qdup_global_state_dot_reference(){
      SpyContext context = new SpyContext();
      context.getState().set("biz","buz");
      context.getCoordinator().setSignal("foo",4);
      context.getCoordinator().setCounter("one",1);
      context.getCoordinator().setCounter("two",2);

      String response = Cmd.populateStateVariables("${{=$QD.state.biz}}",null,context);
      assertEquals("state from qdup global","buz",response);
   }
   @Test
   public void populateStateVariable_qdup_global_counter_dot_reference(){
      SpyContext context = new SpyContext();

      //context.getState().set("biz","buz");
      context.getCoordinator().setCounter("one",1);
      context.getCoordinator().setCounter("two",2);

      String response = Cmd.populateStateVariables("${{=$QD.counter.one}}",null,context);
      assertEquals("state from qdup global","1",response);
   }
   @Test
   public void populateStateVariable_qdup_global_signal_dot_reference(){
      SpyContext context = new SpyContext();
      context.getCoordinator().setSignal("foo",4);

      String response = Cmd.populateStateVariables("${{=$QD.signal.foo}}",null,context);
      assertEquals("state from qdup global","4",response);
   }

   @Test
   public void populateStateVariable_javascript_using_state_from_global(){
      State state = new State("");
      state.set("FOO", "2");
      state.set("BAR", "2");
      state.set("BIZ", "3");

      String response = Cmd.populateStateVariables("${{=FOO * (BAR + BIZ)}}", null, state, null);
      assertEquals("expected value", "10", response);
   }

   @Test
   public void populateStateVariables_arithmetic() {
      State state = new State("");
      state.set("FOO", "2");
      state.set("BAR", "2");
      state.set("BIZ", "3");

      String response = Cmd.populateStateVariables("${{=${{FOO}} * (${{BAR}} + ${{BIZ}})}}", null, state, null);
      assertEquals("expected value", "10", response);
   }

   @Test
   public void populateStateVariables_arithmetic_time() {
      State state = new State("");
      state.set("FOO", "10");
      state.set("BAR", "'1m'");

      String response = Cmd.populateStateVariables("${{= 2*(seconds(${{BAR}})+${{FOO}}) :-1}}", null, state, null);
      assertEquals("expected value with seconds()", "140", response);
   }

   @Test
   public void populateStateVariables_combine_path_with_slash_in_values() {
      State state = new State("");
      state.set("PATH", "/tmp/foo/");
      state.set("FOLDER", "bar");
      String response = Cmd.populateStateVariables("${{PATH}}${{FOLDER}}", null, state, null);
      assertEquals("/tmp/foo/bar", response);
   }

   @Test
   public void populateStateVariables_combine_path_with_slash_between_values() {
      State state = new State("");
      state.set("PATH", "/tmp/foo");
      state.set("FOLDER", "bar");
      String response = Cmd.populateStateVariables("${{PATH}}/${{FOLDER}}", null, state, null);
      assertEquals("/tmp/foo/bar", response);
   }

   @Test
   public void populateStateVariables_value_from_multiple_values() {
      State state = new State("");
      state.set("PATH", "/tmp/foo");
      state.set("FOLDER", "bar");
      state.set("DEST", "${{PATH}}/${{FOLDER}}");
      String response = Cmd.populateStateVariables("${{DEST}}", null, state, null);
      assertEquals("/tmp/foo/bar", response);
   }

   @Test
   public void populateStateVariables_arithmetic_missing_state() {
      State state = new State("");
      state.set("FOO", "10");
      state.set("BAR", "1m");
      String response = Cmd.populateStateVariables("${{ 2*MISSING :-1}}", null, state, null);
      assertEquals("expected default value when missing state", "-1", response);
   }

   @Test
   public void populateStateVariables_arithmetic_missing_function() {
      State state = new State("");
      state.set("FOO", "10");
      state.set("BAR", "1m");
      String response = Cmd.populateStateVariables("${{ 2*doesNotExist(FOO) :-1}}", null, state, null);
      assertEquals("expected default value when missing state", "-1", response);
   }

   @Test
   public void populateStateVariables_arithmetic_strings() {
      State state = new State("");
      state.set("FOO", "10");
      String response = Cmd.populateStateVariables("${{= (2*${{FOO}})+'m' :5m}}", null, state, null);
      assertEquals("expected string concat after maths", "20m", response);
   }

   @Test
   public void populateStateVariables_arithmetic_time_concat() {
      State state = new State("");
      state.set("FOO", "1");
      String response = Cmd.populateStateVariables("${{=milliseconds(${{FOO}}+'m') :5m}}", null, state, null);
      assertEquals("expected string concat after maths", "60000", response);
   }

   @Test
   public void populateStateVariables_envVariable() {
      String populated = Cmd.populateStateVariables("$foo", null, null);
      assertEquals("$ env variable reference", "$foo", populated);

      populated = Cmd.populateStateVariables("${foo}", null, null);
      assertEquals("${} env variable reference", "${foo}", populated);
   }

   @Test
   public void populateStateVariables_foundState() {
      State state = new State("RUN");
      state.set("foo", "FOO");
      state.set("bar", "BAR");

      String populated = Cmd.populateStateVariables("${{foo}}.${{foo}}.${{bar}}", null, state, null);

      assertEquals("all 3 found", "FOO.FOO.BAR", populated);
   }

   @Test
   public void populateStateVariables_foundCmd() {
      Cmd cmd = Cmd.NO_OP();
      cmd.with("foo", "foo");

      State state = new State("RUN");
      state.set("foo", "FOO");
      state.set("bar", "BAR");

      String populated = Cmd.populateStateVariables("${{foo}}.${{foo}}.${{bar}}", cmd, state, null);
      assertEquals("cmd override state", "foo.foo.BAR", populated);
   }

   @Test
   public void populateStateVariables_javascript_regex(){
      State parent = new State("RUN.");
      String populated;
      populated = Cmd.populateStateVariables("${{='<foo>'.match(/<(.*?)>/)[1]}}", null, parent, null);
      assertEquals("expect to match","foo",populated);
   }

   @Test
   public void populateStateVariables_parentState() {
      State parent = new State("RUN.");
      State state = parent.addChild("localhost", "HOST.");

      parent.set("FOO", "foo");

      String populated;
      populated = Cmd.populateStateVariables("${{FOO}}", null, state, null);
      assertEquals("should use parent value", "foo", populated);

      state.set("FOO", "FOO");
      populated = Cmd.populateStateVariables("${{FOO}}", null, state, null);
      assertEquals("should use child value", "FOO", populated);
   }

   @Test
   public void populateStateVariables_prefixState() {
      State parent = new State("RUN.");
      State state = parent.addChild("localhost", "HOST.");

      parent.set("FOO", "foo");
      state.set("FOO", "FOO");

      String populated;
      populated = Cmd.populateStateVariables("${{RUN.FOO}}", null, state, null);
      assertEquals("should use parent value due to prefix", "foo", populated);
      populated = Cmd.populateStateVariables("${{HOST.FOO}}", null, state, null);
      assertEquals("should use parent value due to prefix", "FOO", populated);
   }

   @Test
   public void populateStateVariables_nestVariable() {
      State state = new State("RUN.");
      state.set("FOO", "BAR");
      state.set("BAR", "bar");

      String populated = Cmd.populateStateVariables("${{${{FOO}}}}", null, state, null);
      assertEquals("evaluate as two state refernces", "bar", populated);
   }

   @Test
   public void populateStateVariables_notFound() {
      String populated = Cmd.populateStateVariables("${{FOO}}", null, null);

      assertEquals("not replaced", "${{FOO}}", populated);
      //no longer valid because we do not support replaceMissing
//        populated = Cmd.populateStateVariables("${{FOO}}",null,null);
//        assertEquals("replaced","",populated);
   }

   @Test
   public void populateStateVariables_defaultValue() {
      String populated = Cmd.populateStateVariables("${{FOO:foo}}", null, null);
      assertEquals("use default value", "foo", populated);
   }

   @Test
   public void populateStateVariables_WithToState() {
      State state = new State("RUN.");
      state.set("FOO", "foo");

      Cmd cmd = Cmd.NO_OP();
      cmd.with("BAR", "${{FOO}}");

      String populated = Cmd.populateStateVariables("${{BAR}}", cmd, state, null);
      assertEquals("${{BAR}} should resolve to foo", "foo", populated);
   }
   @Test
   public void populateStateVariables_empty_state_value_uses_default() {
      State state = new State("RUN.");
      state.set("FOO", "");

      Cmd cmd = Cmd.NO_OP();
      cmd.with("BAR", "${{FOO:bar}}");

      String populatedDefault = Cmd.populateStateVariables("${{FOO:bar}}", cmd, state, null);
      String populatedEmpty = Cmd.populateStateVariables("${{FOO}}", cmd, state, null);
      assertEquals("${{FOO:bar}} should resolve to bar", "bar", populatedDefault);
      assertEquals("${{FOO}} should resolve to bar", "", populatedEmpty);
   }

   @Test
   public void populateStateVariables_defaultEmpty_bindState() {
      State state = new State("RUN.");
      state.set("FOO", "foo");
      String populated = Cmd.populateStateVariables("${{FOO:}}", null, state, null);
      assertEquals("should populate from state", "foo", populated);
   }

   @Test
   public void populateStateVariables_defaultEmpty_bindWith() {
      State state = new State("RUN.");
      state.set("FOO", "bar");
      Cmd cmd = Cmd.NO_OP();
      cmd.with("FOO", "foo");

      String populated = Cmd.populateStateVariables("${{FOO:}}", cmd, state, null);
      assertEquals("should populate from state", "foo", populated);
   }

   @Test
   public void getStateVariables_two_states(){
      List<String> list = Cmd.getStateVariables("${{RUN.BAR:}}-${{FOO.name}}",null,null,null, null);
      assertEquals("expect two entries",2,list.size());
   }

   @Test
   public void populateStateVariables_default(){
      State state = new State("RUN.");
      Cmd cmd = Cmd.NO_OP();
      String populated = Cmd.populateStateVariables("RUN.BAR ${{RUN.BAR:}}-${{FOO.name}}", cmd, state, null, new Cmd.Ref(cmd));
   }

   @Test
   public void populateStateVariables_with_javascript(){
      State state = new State("RUN.");
      state.set("FOO", Json.fromString("[\"uno\",\"dos\"]"));
      Cmd cmd = Cmd.NO_OP();
      cmd.with("value","${{= [ ...${{RUN.FOO}}, \"tres\" ] }}");
      String populated = Cmd.populateStateVariables("${{value}}", cmd, state, null, new Cmd.Ref(cmd));
      assertEquals("should populate from state", "[\"uno\",\"dos\",\"tres\"]", populated);
   }

   @Test
   public void populateStateVariables_with_json_from_ref(){
      State state = new State("RUN.");
      state.set("FOO", "bar");
      Cmd cmd = Cmd.NO_OP();
      cmd.with(Json.fromString("{\"key\":{\"value\":\"foo\"}}"));
      Cmd use = Cmd.sh("pwd");
      String populated = Cmd.populateStateVariables("${{key.value}}", use, state, null, new Cmd.Ref(cmd));
      assertEquals("should populate from state", "foo", populated);
   }
   @Test
   public void populateStateVariables_defaultIgnored_bindWith() {
      State state = new State("RUN.");
      state.set("FOO", "bar");
      Cmd cmd = Cmd.NO_OP();
      cmd.with("FOO", "foo");
      String populated = Cmd.populateStateVariables("${{FOO:biz}}", cmd, state, null);
      assertEquals("should populate from state", "foo", populated);
   }

   @Test
   public void getStateValue_twoValues() {
      State state = new State("");
      state.set("FOO", "foo");
      state.set("BAR", "bar");
      Object populated = Cmd.populateStateVariables("${{FOO}}${{BAR}}", null, state, null);
      assertEquals("expect both values to replace", "foobar", populated);
   }

   @Test
   public void run_with_same_name_from_state(){
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("", stream("" +
         "scripts:",
         "  foo:",
         "  - set-state: RUN.resolved ${{value}}",
         "hosts:",
         "  local: " + getHost(),
         "roles:",
         "  doit:",
         "    hosts: [local]",
         "    run-scripts:",
         "    - foo:",
         "        with:",
         "          value: ${{value}}",
         "states:",
         "  value: worked"
      )));

      RunConfig config = builder.buildConfig(parser);
      Dispatcher dispatcher = new Dispatcher();

      Cmd foo = config.getScript("foo");

      Run doit = new Run(tmpDir.toString(), config, dispatcher);
      doit.run();
      dispatcher.shutdown();

      State s = config.getState();

      assertTrue("state should contain resolved",s.has("resolved"));
      assertEquals("resolved should get value from state","worked",s.get("resolved"));
   }

   @Test
   public void run_with_json_from_state() {
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("", stream("" +
         "scripts:",
         "  foo:",
         "  - set-state: RUN.name ${{host.name}}",
         "hosts:",
         "  local: " + getHost(),
         "roles:",
         "  doit:",
         "    hosts: [local]",
         "    run-scripts:",
         "    - foo:",
         "        with:",
         "          host: ${{charlie}}",
         "states:",
         "  alpha: [ {name: \"ant\"}, {name: \"apple\"} ]",
         "  bravo: [ {name: \"bear\"}, {name: \"bull\"} ]",
         "  charlie: {name: \"cat\"}"
      )));

      RunConfig config = builder.buildConfig(parser);

      assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());

      Dispatcher dispatcher = new Dispatcher();

      Cmd foo = config.getScript("foo");

      Run doit = new Run(tmpDir.toString(), config, dispatcher);
      doit.run();
      dispatcher.shutdown();


      assertEquals("state.name should populate", "cat", config.getState().get("name"));
   }

   @Test
   public void run_with_value_type(){
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("", stream("" +
              "scripts:",
              "  foo:",
              "  - set-state: RUN.FOO ${{foo}}",
              "hosts:",
              "  local: " + getHost(),
              "roles:",
              "  doit:",
              "    hosts: [local]",
              "    run-scripts:",
              "    - foo:",
              "        with:",
              "          host: {name: \"cat\"}",
              "states:",
              "  alpha: [ {name: \"ant\"}, {name: \"apple\"} ]",
              "  bravo: [ {name: \"bear\"}, {name: \"bull\"} ]",
              "  charlie: {name: \"cat\"}"
      )));

      RunConfig config = builder.buildConfig(parser);
      assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());
      Dispatcher dispatcher = new Dispatcher();

      Cmd foo = config.getScript("foo");

      Run doit = new Run(tmpDir.toString(), config, dispatcher);
      doit.run();
      dispatcher.shutdown();
   }

   @Test
   public void run_with_json() {
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("", stream("" +
            "scripts:",
         "  foo:",
         "  - set-state: RUN.name ${{host.name}}",
         "hosts:",
         "  local: " + getHost(),
         "roles:",
         "  doit:",
         "    hosts: [local]",
         "    run-scripts:",
         "    - foo:",
         "        with:",
         "          host: {name: \"cat\"}",
         "states:",
         "  alpha: [ {name: \"ant\"}, {name: \"apple\"} ]",
         "  bravo: [ {name: \"bear\"}, {name: \"bull\"} ]",
         "  charlie: {name: \"cat\"}"
      )));

      RunConfig config = builder.buildConfig(parser);
      Dispatcher dispatcher = new Dispatcher();

      Cmd foo = config.getScript("foo");

      Run doit = new Run(tmpDir.toString(), config, dispatcher);
      doit.run();
      dispatcher.shutdown();

      assertEquals("state.name should populate", "cat", config.getState().get("name"));
   }

   @Test
   public void has_with_self() {
      Cmd top = Cmd.NO_OP();
      top.with("foo", "bar");
      assertTrue("top should have bar", top.hasWith("foo"));
   }

   @Test
   public void with_integer(){
      Cmd test = Cmd.NO_OP();
      test.with("foo",10);
      Object found = test.getWith("foo");
      assertNotNull(found);
      assertEquals(10,found);
   }
   @Test
   public void with_integer_from_state(){
      Cmd test = Cmd.setState("RUN.foo","${{input}}");
      test.with("input","${{bar}}");
      SpyContext spyContext = new SpyContext();
      spyContext.getState().set("foo",10);
      test.run("",spyContext);
      Object found = spyContext.getState().get("foo");
      assertNotNull(found);
      assertEquals(10,found);
   }

   @Test
   public void with_double(){
      Cmd test = Cmd.NO_OP();
      test.with("foo",10.5);
      Object found = test.getWith("foo");
      assertNotNull(found);
      assertTrue("found should be a double: "+found,found instanceof Double);
      assertEquals(10.5,((Double)found).doubleValue(),0.001);
   }
   @Test
   public void with_double_from_state(){
      Cmd test = Cmd.setState("RUN.foo","${{input}}");
      test.with("input","${{bar}}");
      SpyContext spyContext = new SpyContext();
      spyContext.getState().set("foo",10.5);
      test.run("",spyContext);
      Object found = spyContext.getState().get("foo");
      assertTrue("found should be a double: "+found,found instanceof Double);
      assertEquals(10.5,((Double)found).doubleValue(),0.001);
   }
   @Test
   public void with_json(){
      Cmd test = Cmd.NO_OP();
      test.with("foo",Json.fromString("{\"key\":\"uno\"}"));
      Object found = test.getWith("foo");
      assertNotNull(found);
      assertTrue("found should be a json: "+found,found instanceof Json);
      assertEquals(found,Json.fromString("{\"key\":\"uno\"}"));
   }
   @Test
   public void with_json_from_state(){
      Cmd test = Cmd.setState("RUN.foo","${{input}}");
      test.with("input","${{bar}}");
      SpyContext spyContext = new SpyContext();
      spyContext.getState().set("foo",Json.fromString("{\"key\":\"uno\"}"));
      test.run("",spyContext);
      Object found = spyContext.getState().get("foo");
      assertNotNull(found);
      assertEquals(found,Json.fromString("{\"key\":\"uno\"}"));
   }
   @Test
   public void has_with_parent() {
      Cmd top = Cmd.NO_OP();
      Cmd mid = Cmd.NO_OP();
      Cmd bot = Cmd.NO_OP();

      top.then(mid);
      mid.then(bot);

      top.with("foo", "bar");
      assertTrue("bot should have bar", bot.hasWith("foo"));
   }

   @Test
   public void populateStateVariables_from_parent_with() {
      Cmd top = Cmd.NO_OP();
      Cmd mid = Cmd.NO_OP();
      Cmd bot = Cmd.NO_OP();

      top.then(mid);
      mid.then(bot);

      top.with("foo", "bar");

      State state = new State("");
      state.set("foo", "state");

      String populated = Cmd.populateStateVariables("${{foo}}", bot, state, null);
      assertEquals("with should take priority over state", "bar", populated);
   }
   @Test
   public void populateStateVariables_from_parent_with_json() {
      Cmd top = Cmd.NO_OP();
      Cmd mid = Cmd.NO_OP();
      Cmd bot = Cmd.NO_OP();

      top.then(mid);
      mid.then(bot);

      top.with("foo", Json.fromString("{ \"bar\":\"value\"}"));

      State state = new State("");
      state.set("foo", "state");

      String populated = Cmd.populateStateVariables("${{foo.bar}}", bot, state, null);
      assertEquals("with should take priority over state", "value", populated);
   }
   @Test
   public void populateStateVariables_from_timer_parent_with_json() {
      Cmd top = Cmd.NO_OP();
      Cmd mid = Cmd.NO_OP();
      Cmd bot = Cmd.NO_OP();

      top.then(mid);
      mid.addTimer(100,bot);

      top.with("foo", Json.fromString("{ \"bar\":\"value\"}"));

      State state = new State("");
      state.set("foo", "state");

      String populated = Cmd.populateStateVariables("${{foo.bar}}", bot, state, null);
      assertEquals("with should take priority over state", "value", populated);
   }

   @Test
   public void test_timer() {

   }

   @Test
   public void run_test_on_signal_variable() {
      RunConfigBuilder builder = getBuilder();
      StringBuilder first = new StringBuilder();

      Script runSleep = new Script("run-sleep");
      runSleep.then(
         Cmd.sleep("2m")
            .onSignal("${{NAME}}-signal",
               Cmd.code((input, ctx) -> {
                  first.append("called");
                  return Result.next(input);
               })
                  .then(Cmd.abort("done"))
            )
      );
      Script runSignal = new Script("run-signal");
      runSignal.then(Cmd.sleep("2s"));
      runSignal.then(Cmd.signal("${{NAME}}-signal"));


      builder.addScript(runSignal);
      builder.addScript(runSleep);

      builder.addHostAlias("local", getHost().toString());
      builder.addHostToRole("role", "local");
      builder.addRoleRun("role", "run-signal", new HashMap<>());
      builder.addRoleRun("role", "run-sleep", new HashMap<>());

      builder.setRunState("NAME", "variable");

      RunConfig config = builder.buildConfig(Parser.getInstance());
      Dispatcher dispatcher = new Dispatcher();
      Run run = new Run(tmpDir.toString(), config, dispatcher);
      run.run();

      assertEquals("on-signal should be called", "called", first.toString());
   }

   @Test
   public void run_test_on_signal() {
      RunConfigBuilder builder = getBuilder();
      StringBuilder first = new StringBuilder();

      Script runSleep = new Script("run-sleep");
      runSleep.then(
         Cmd.sleep("2m")
            .onSignal("signalFoo",
               Cmd.code((input, ctx) -> {
                  first.append("called");
                  return Result.next(input);
               })
                  .then(Cmd.abort("done"))
            )
      );
      Script runSignal = new Script("run-signal");
      runSignal.then(Cmd.sleep("2s"));
      runSignal.then(Cmd.signal("signalFoo"));

      builder.addScript(runSignal);
      builder.addScript(runSleep);

      builder.addHostAlias("local", getHost().toString());
      builder.addHostToRole("role", "local");
      builder.addRoleRun("role", "run-signal", new HashMap<>());
      builder.addRoleRun("role", "run-sleep", new HashMap<>());

      RunConfig config = builder.buildConfig(Parser.getInstance());
      Dispatcher dispatcher = new Dispatcher();
      Run run = new Run(tmpDir.toString(), config, dispatcher);
      run.run();

      assertEquals("on-signal signalFoo should be called", "called", first.toString());
   }

   @Test
   public void run_test_with() {
      RunConfigBuilder builder = getBuilder();

      StringBuilder first = new StringBuilder();
      StringBuilder second = new StringBuilder();
      StringBuilder third = new StringBuilder();

      Script runScript = new Script("run-with");
      runScript.then(Cmd.setState("FOO", "BAR"));
      runScript.then(Cmd.sh("echo 1-${{FOO}}").then(Cmd.code((input, state) -> {
         first.append(input.trim());
         return Result.next(input);
      })));
      runScript.then(Cmd.NO_OP().with("FOO", "FOO").then(Cmd.sh("echo 2-${{FOO}}").then(Cmd.code((input, state) -> {
         second.append(input.trim());
         return Result.next(input);
      }))));
      runScript.then(Cmd.sh("echo 3-${{FOO}}").then(Cmd.code((input, state) -> {
         third.append(input.trim());
         return Result.next(input);
      })));

      builder.addScript(runScript);

      builder.addHostAlias("local", getHost().toString());
      builder.addHostToRole("role", "local");
      builder.addRoleRun("role", "run-with", new HashMap<>());

      RunConfig config = builder.buildConfig(Parser.getInstance());
      Dispatcher dispatcher = new Dispatcher();
      Run run = new Run(tmpDir.toString(), config, dispatcher);
      run.run();

      assertEquals("first should see FOO=BAR", "1-BAR", first.toString());
      assertEquals("second should see FOO=FOO", "2-FOO", second.toString());
      assertEquals("third should see FOO=BAR", "3-BAR", third.toString());
   }

   @Test
   public void test_cmd_tree_previous() {
      Cmd A = Cmd.log("A")
         .then(Cmd.log("B")
            .then(Cmd.log("C")
               .then(Cmd.log("D")))

         )
         .then(Cmd.log("X"));

      Cmd X = A.getTail();

      Assert.assertEquals("X should be tail of A", true, X.toString().contains("X"));
      Assert.assertEquals("X previous should be B", true, X.getPrevious().toString().contains("B"));

   }
}
