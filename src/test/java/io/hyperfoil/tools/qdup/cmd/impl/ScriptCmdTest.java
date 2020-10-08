package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.SshTestBase;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import org.junit.Test;

import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ScriptCmdTest extends SshTestBase {




   //TOOD test with injection
   @Test
   public void async_using_with_on_phase(){
         Parser parser = Parser.getInstance();
         RunConfigBuilder builder = getBuilder();
         builder.loadYaml(parser.loadFile("", stream("" +
            "scripts:",
            "  update:",
            "  - sleep: 2s",
            "  - set-state: RUN.FOO ${{RUN.FOO:}}-${{arg}}",
            "  foo:",
            "  - script: ",
            "      name: update",
            "      async: true",
            "  - set-state: RUN.FOO ${{RUN.FOO:}}-SET",
            "hosts:",
            "  local: " + getHost(),
            "roles:",
            "  doit:",
            "    hosts: [local]",
            "    run-scripts:",
            "      - foo:",
            "          with:",
            "            arg: phase",
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
         assertEquals("expect script:foo to finish before script:update starts", "-SET-phase", config.getState().get("FOO"));
   }

   @Test
   public void async_using_with_on_script(){
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("",stream(""+
            "scripts:",
         "  update:",
         "  - sleep: 2s",
         "  - set-state: RUN.FOO ${{RUN.FOO:}}-${{arg}}",
         "  foo:",
         "  - script: ",
         "      name: update",
         "      async: true",
         "    with:",
         "      arg: script",
         "  - set-state: RUN.FOO ${{RUN.FOO:}}-SET",
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
      Cmd foo = config.getScript("foo");
      Run doit = new Run(tmpDir.toString(), config, dispatcher);

      doit.run();
      dispatcher.shutdown();
      assertEquals("expect script:foo to finish before script:update starts","-SET-script",config.getState().get("FOO"));
   }

   @Test
   public void async(){

      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("",stream(""+
         "scripts:",
         "  update:",
         "  - sleep: 2s",
         "  - set-state: RUN.FOO ${{RUN.FOO:}}-UPDATED",
         "  foo:",
         "  - script: ",
         "      name: update",
         "      async: true",
         "  - set-state: RUN.FOO ${{RUN.FOO:}}-SET",
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
      Cmd foo = config.getScript("foo");
      Run doit = new Run(tmpDir.toString(), config, dispatcher);

      doit.run();
      dispatcher.shutdown();
      assertEquals("expect script:foo to finish before script:update starts","-SET-UPDATED",config.getState().get("FOO"));
   }


   @Test
   public void nested_foreach_script_invocation(){
      String foo="";
      int c = 0;
      do{
         c++;
         Parser parser = Parser.getInstance();
         RunConfigBuilder builder = getBuilder();
         builder.loadYaml(parser.loadFile("",stream(""+
               "scripts:",
            "  update:",
            "  - set-state: RUN.FOO ${{RUN.FOO:}}-<${{BAR}}>",
            "  foo:",
            "  - for-each:",
            "      name: arg1",
            "      input: [\"one\",\"two\"]",
            "    then:",
            "    - for-each:",
            "        name: arg2",
            "        input: [\"uno\",\"dos\"]",
            "      then:",
            "      - script: update",
            "        with:",
            "          BAR: ${{arg1}}+${{arg2}}",
            "hosts:",
            "  local: " + getHost(),
            "roles:",
            "  doit:",
            "    hosts: [local]",
            "    run-scripts: [foo]",
            "states:",
            "  alpha: [ {name: \"ant\"}, {name: \"apple\"} ]",
            "  bravo: [ {name: \"bear\"}, {name: \"bull\"} ]",
            "  charlie: {name: \"cat\"}",
            "  BAR: def"
         )));

         RunConfig config = builder.buildConfig(parser);
         Dispatcher dispatcher = new Dispatcher();
         Run doit = new Run(tmpDir.toString(), config, dispatcher);
         doit.run();
         dispatcher.shutdown();
         foo = config.getState().get("FOO").toString();
      }while("-<one+uno>-<one+dos>-<two+uno>-<two+dos>".equals(foo) && c < 20);

      assertEquals("race condition prevented correct output","-<one+uno>-<one+dos>-<two+uno>-<two+dos>",foo);
   }

   @Test
   public void javascript_array_spread(){
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("",stream(""+
         "scripts:",
         "  update:",
         "  - log: ${{arg}}",
         "  - set-state: RUN.FOO ${{RUN.FOO:}}-${{arg:}}",
         "  foo:",
         "  - for-each:",
         "      name: arg",
         "      input: [\"one\",\"two\",\"three\",\"four\"]",
         "    then:",
         "    - script: update",
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
      Run doit = new Run(tmpDir.toString(), config, dispatcher);
      doit.run();
      dispatcher.shutdown();

      assertEquals("script=update should invoke once per arg","-one-two-three-four",config.getState().get("FOO"));
   }
}
