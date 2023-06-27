package io.hyperfoil.tools.qdup.cmd;

import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.SshTestBase;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import org.junit.Test;

import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Test for the Dispatcher nanny task
 */
public class NannyTest extends SshTestBase {



   @Test(timeout = 120_000)//2 minutes
   public void idle_wait_for(){
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("pwd", stream("" +
         "scripts:",
         "  foo:",
         "    - sh: echo \"pwd is $(pwd)\"",
         "      watch:",
         "      - regex: \"missingValue\"",
         "        then:",
         "        - signal: MISSING",
         "    - set-state: RUN.done true",
         "  bar:",
         "    - wait-for: MISSING",
         "    - set-state: RUN.never true",
         "hosts:",
         "  local: " + getHost(),
         "roles:",
         "  doit:",
         "    hosts: [local]",
         "    run-scripts:",
         "    - foo:",
         "    - bar:"
      )));
      RunConfig config = builder.buildConfig(parser);
      assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run(tmpDir.toString(), config, dispatcher);

      doit.run();

      State state = config.getState();

      assertTrue("state should have done",state.has("done"));
      assertFalse("state should not have never",state.has("never"));
   }

   @Test(timeout = 120_000)//2 minutes
   public void idle_repeat_until(){
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("pwd", stream(
         "" +
         "scripts:",
         "  foo:",
         "    - sh: echo \"pwd is $(pwd)\"",
         "      watch:",
         "      - regex: \"missingValue\"",
         "        then:",
         "        - signal: MISSING",
         "    - set-state: RUN.done true",
         "  bar:",
         "    - repeat-until: MISSING",
         "      then:",
         "      - set-state: RUN.count ${{= 1 + ${{RUN.count:0}} }}",
         "      - sleep: 10s",
         "    - set-state: RUN.never true",
         "hosts:",
         "  local: " + getHost(),
         "roles:",
         "  doit:",
         "    hosts: [local]",
         "    run-scripts:",
         "    - foo:",
         "    - bar:"
      )));
      RunConfig config = builder.buildConfig(parser);
      assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run(tmpDir.toString(), config, dispatcher);

      doit.run();

      State state = config.getState();

      assertTrue("state should have done",state.has("done"));
      assertFalse("state should not have never",state.has("never"));
   }
   @Test(timeout = 120_000)//2 minutes
   public void self_finishing_repeat_until(){
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("pwd", stream(
         "" +
            "scripts:",
         "  foo:",
         "    - sh: echo \"pwd is $(pwd)\"",
         "      watch:",
         "      - regex: \"missingValue\"",
         "        then:",
         "        - signal: MISSING",
         "    - set-state: RUN.done true",
         "  bar:",
         "    - repeat-until: NOT_MISSING",
         "      then:",
         "      - set-state: RUN.count ${{= 1 + ${{RUN.count:0}} }}",
         "      - read-state: ${{RUN.count}}",
         "      - regex: 4",
         "        then:",
         "        - signal: NOT_MISSING",
         "      - sleep: 10s",
         "    - set-state: RUN.reached true",
         "hosts:",
         "  local: " + getHost(),
         "roles:",
         "  doit:",
         "    hosts: [local]",
         "    run-scripts:",
         "    - foo:",
         "    - bar:"
      )));
      RunConfig config = builder.buildConfig(parser);
      assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run(tmpDir.toString(), config, dispatcher);

      doit.run();

      State state = config.getState();

      assertTrue("state should have done",state.has("done"));
      assertTrue("state should have reached",state.has("reached"));
      assertTrue("state should have count",state.has("count"));
      assertEquals("count should be 4",4,Integer.parseInt(state.get("count").toString()));
   }

   @Test
   public void repeat_until_already_signalled(){
      int count = 5;
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("pwd", stream(
              "" +
              "scripts:",
              "  foo:",//docker
              "    - signal: foo",
              "    - set-state: RUN.foo ${{= 1 + ${{RUN.foo:0}} }}",
              "    - wait-for: done",
              "  biz:",//pmap
              "    - wait-for: foo",
              "    - repeat-until: bar",
              "      then:",
              "      - set-state: RUN.biz ${{= 1 + ${{RUN.biz:0}} }}",
              "      - sleep: 100s",
              "    - set-state: RUN.reachedBiz true",
              "    - signal: done",
              "  bar:",//wrk
              "    - wait-for: foo",
              "    - sh: sleep 10s",//work
              "    - signal: bar",
              "    - set-state: RUN.reachedBar true",
              "hosts:",
              "  local: " + getHost(),
              "roles:",
              "  doit:",
              "    hosts: [local]",
              "    run-scripts:",
              "    - foo:",
              "    - bar:",
              "    - biz:"
      )));
      RunConfig config = builder.buildConfig(parser);
      assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run(tmpDir.toString(), config, dispatcher);

      doit.run();

      State state = config.getState();
      assertTrue("state should have reached",state.has("reachedBar"));
      assertTrue("state should have reached",state.has("reachedBiz"));
   }
   @Test
   public void repeat_until_signaling_pair(){
      int count = 10;
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("pwd", stream(
              "" +
              "scripts:",
              "  foo:",
              "    - repeat-until: FOO",
              "      then:",
              "      - set-state: RUN.foo ${{=1 + ${{RUN.foo:0}} }}",
              "      - regex: "+count,
              "        then:",
              "        - signal: BAR",
              "      - sleep: 10s",
              "    - set-state: RUN.reachedFoo true",

              "  bar:",
              "    - repeat-until: BAR",
              "      then:",
              "      - set-state: RUN.bar ${{= 1 + ${{RUN.bar:0}} }}",
              "      - read-state: ${{RUN.bar}}",
              "      - regex: "+count,
              "        then:",
              "        - signal: FOO",
              "      - sleep: 10s",
              "    - set-state: RUN.reachedBar true",
              "hosts:",
              "  local: " + getHost(),
              "roles:",
              "  doit:",
              "    hosts: [local]",
              "    run-scripts:",
              "    - foo:",
              "    - bar:"
      )));
      RunConfig config = builder.buildConfig(parser);
      assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run(tmpDir.toString(), config, dispatcher);

      doit.run();

      State state = config.getState();
      System.out.println(state.tree());
      assertFalse("state should NOT have reachedBar\n"+state.tree(),state.has("reachedBar"));
      assertFalse("state should NOT have reachedFoo\n"+state.tree(),state.has("reachedFoo"));
      assertTrue("state should have foo\n"+state.tree(),state.has("foo"));
      assertTrue("state should have bar\n"+state.tree(),state.has("bar"));
      assertTrue("incorrect foo "+state.get("foo")+" vs count "+count+"\n"+state.tree(),count > Integer.parseInt(state.get("foo").toString()));
      assertTrue("incorrect bar "+state.get("bar")+" vs count "+count+"\n"+state.tree(),count > Integer.parseInt(state.get("bar").toString()));
   }
}
