package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.JsonServer;
import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.SshTestBase;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.config.CmdBuilder;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ScriptCmdTest extends SshTestBase {

   //TOOD test with injection
   @Test
   public void async_using_with_on_phase(){
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = new RunConfigBuilder(CmdBuilder.getBuilder());
      builder.loadYaml(parser.loadFile("",stream(""+
         "scripts:",
         "  update:",
         "  - sleep: 2s",
         "  - set-state: RUN.FOO ${{RUN.FOO}}-${{arg}}",
         "  foo:",
         "  - script: ",
         "      name: update",
         "      async: true",
         "  - set-state: RUN.FOO ${{RUN.FOO}}-SET",
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
      ),false));

      RunConfig config = builder.buildConfig();
      Dispatcher dispatcher = new Dispatcher();
      Cmd foo = config.getScript("foo");
      Run doit = new Run("/tmp", config, dispatcher);

      doit.run();
      dispatcher.shutdown();
      assertEquals("expect script:foo to finish before script:update starts","-SET-phase",config.getState().get("FOO"));
   }

   @Test
   public void async_using_with_on_script(){
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = new RunConfigBuilder(CmdBuilder.getBuilder());
      builder.loadYaml(parser.loadFile("",stream(""+
            "scripts:",
         "  update:",
         "  - sleep: 2s",
         "  - set-state: RUN.FOO ${{RUN.FOO}}-${{arg}}",
         "  foo:",
         "  - script: ",
         "      name: update",
         "      async: true",
         "    with:",
         "      arg: script",
         "  - set-state: RUN.FOO ${{RUN.FOO}}-SET",
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
      Cmd foo = config.getScript("foo");
      Run doit = new Run("/tmp", config, dispatcher);

      doit.run();
      dispatcher.shutdown();
      System.out.println(config.getState().get("FOO"));
      assertEquals("expect script:foo to finish before script:update starts","-SET-script",config.getState().get("FOO"));
   }

   @Test
   public void async(){

      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = new RunConfigBuilder(CmdBuilder.getBuilder());
      builder.loadYaml(parser.loadFile("",stream(""+
         "scripts:",
         "  update:",
         "  - sleep: 2s",
         "  - set-state: RUN.FOO ${{RUN.FOO}}-UPDATED",
         "  foo:",
         "  - script: ",
         "      name: update",
         "      async: true",
         "  - set-state: RUN.FOO ${{RUN.FOO}}-SET",
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
      Cmd foo = config.getScript("foo");
      Run doit = new Run("/tmp", config, dispatcher);

      doit.run();
      dispatcher.shutdown();
      assertEquals("expect script:foo to finish before script:update starts","-SET-UPDATED",config.getState().get("FOO"));
   }

   @Test
   public void javascript_array_spread(){
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = new RunConfigBuilder(CmdBuilder.getBuilder());
      builder.loadYaml(parser.loadFile("",stream(""+
         "scripts:",
         "  update:",
         "  - log: ${{arg}}",
         "  - set-state: RUN.FOO ${{RUN.FOO}}-${{arg}}",
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
      ),false));

      RunConfig config = builder.buildConfig();
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run("/tmp", config, dispatcher);
      doit.run();
      dispatcher.shutdown();

      assertEquals("script=update should invoke once per arg","-one-two-three-four",config.getState().get("FOO"));
   }
}
