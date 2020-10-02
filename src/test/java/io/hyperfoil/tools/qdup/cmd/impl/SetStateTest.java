package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.SshTestBase;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.cmd.SpyContext;
import io.hyperfoil.tools.qdup.config.CmdBuilder;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import io.hyperfoil.tools.yaup.json.Json;
import org.junit.Test;

import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class SetStateTest extends SshTestBase {

   @Test
   public void replace_same_name_from_state(){
      SetState setState = new SetState("FOO","${{FOO:bar}}");
      SpyContext spyContext = new SpyContext();
      spyContext.getState().set("FOO","biz");
      setState.run("",spyContext);
      assertEquals("FOO should not change","biz",spyContext.getState().get("FOO"));
   }
   @Test
   public void replace_same_name_from_default(){
      SetState setState = new SetState("FOO","${{FOO:bar}}");
      SpyContext spyContext = new SpyContext();
      setState.run("",spyContext);
      assertEquals("FOO should change to bar","bar",spyContext.getState().get("FOO"));
   }
   @Test
   public void replace_same_name_from_emtpy_value(){
      SetState setState = new SetState("FOO","${{FOO:bar}}");
      SpyContext spyContext = new SpyContext();
      spyContext.getState().set("FOO","");
      setState.run("",spyContext);
      assertEquals("FOO should not change","bar",spyContext.getState().get("FOO"));
   }

   @Test
   public void replace_regex(){
      String mac = "00:11:22:33:44:0b";
      SetState setState = new SetState("key","${{= \"${{host.mac}}\".replace(/\\:/g,'-')}}");
      setState.setPatternSeparator("_");
      SpyContext spyContext = new SpyContext();
      spyContext.getState().set("host",Json.fromString("{\"mac\":\""+mac+"\"}"));

      setState.run("",spyContext);

      assertTrue("state should have a key",spyContext.getState().has("key"));
      assertEquals("key should be mac with : replaced with -",mac.replace(":","-"),spyContext.getState().get("key"));
   }

   @Test
   public void replace_regex_in_yaml_cmd_separator(){
      String mac = "00:11:22:33:44:0b";
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("",stream(""+
         "scripts:",
         "  foo:",
         "  - set-state:",
         "      key: RUN.key ",
         "      value: ${{=\"${{host.mac}}\".replace(/:/g,'-')}}",
         "    separator: _",
         "  - set-state: RUN.ARGS ${{= ${{FOO:[\"a\",\"b\"]}}.join(' ')}}",
         "hosts:",
         "  local: " + getHost(),
         "roles:",
         "  doit:",
         "    hosts: [local]",
         "    run-scripts:",
         "      - foo:",
         "states:",
         "  host:",
         "    mac: \""+mac+"\""
      ),false));

      RunConfig config = builder.buildConfig(parser);
      assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());

      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run(tmpDir.toString(), config, dispatcher);

      doit.run();

      dispatcher.shutdown();

      State state = config.getState();

      assertTrue("state should have key",state.has("key"));
      assertEquals("key should be mac with : replaced with -",mac.replace(":","-"),state.get("key"));
   }


   @Test(timeout = 10_000)
   public void javascript_spread_missing_arg(){
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("",stream(""+
         "scripts:",
         "  foo:",
         "  - set-state:",
         "      key: RUN.FOO ",
         "      value: ${{= [...${{RUN.FOO}},new String(${{BAR}}) ] }}",
         "      separator: _",
         "  - set-state: RUN.ARGS ${{= ${{FOO}}.join(' ')}}",
         "hosts:",
         "  local: " + getHost(),
         "roles:",
         "  doit:",
         "    hosts: [local]",
         "    run-scripts:",
         "      - foo:",
         "states:",
         "  FOO: [ \"ant\", \"apple\" ]"
      ),false));

      RunConfig config = builder.buildConfig(parser);
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run(tmpDir.toString(), config, dispatcher);

      doit.run();
      dispatcher.shutdown();

      Object FOO = config.getState().get("FOO");

      //TODO should FOO be set when BAR is missing?
      //assertTrue("FOO should be json but was "+(FOO==null ? "null" : FOO.getClass().getSimpleName()),FOO instanceof Json);
   }

   @Test
   public void javascript_spread_append_object_to_array(){
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("",stream(""+
         "scripts:",
         "  foo:",
         "  - set-state: RUN.FOO []",
         "  - set-state:",
         "      key: RUN.FOO ",
         "      value: \"${{ [...${{RUN.FOO}},{'test':'worked'}] }}\"",
         "      separator: _",
         "hosts:",
         "  local: " + getHost(),
         "roles:",
         "  doit:",
         "    hosts: [local]",
         "    run-scripts:",
         "      - foo:",
         "states:",
         "  alpha: [ {name: \"ant\"}, {name: \"apple\"} ]",
         "  bravo: [ {name: \"bear\"}, {name: \"bull\"} ]",
         "  charlie: {name: \"cat\"}"
      ),false));

      RunConfig config = builder.buildConfig(parser);
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run(tmpDir.toString(), config, dispatcher);

      doit.run();
      dispatcher.shutdown();

      Object FOO = config.getState().get("FOO");
      assertTrue("FOO should be json",FOO instanceof Json);
      assertEquals("FOO[0].test = worked","worked",((Json)FOO).getJson(0,new Json(false)).getString("test",""));
      //assertEquals("expect script:foo to finish before script:update starts","-SET-phase",config.getState().get("FOO"));
   }

}


