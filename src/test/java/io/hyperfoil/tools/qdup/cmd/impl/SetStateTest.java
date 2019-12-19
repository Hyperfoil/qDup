package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.SshTestBase;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.config.CmdBuilder;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import io.hyperfoil.tools.yaup.json.Json;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SetStateTest extends SshTestBase {

   @Test
   public void javascript_spread_append_object_to_array(){
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = new RunConfigBuilder(CmdBuilder.getBuilder());
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

      RunConfig config = builder.buildConfig();
      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run("/tmp", config, dispatcher);

      doit.run();
      dispatcher.shutdown();

      Object FOO = config.getState().get("FOO");
      assertTrue("FOO should be json",FOO instanceof Json);
      assertEquals("FOO[0].test = worked","worked",((Json)FOO).getJson(0,new Json(false)).getString("test",""));
      //assertEquals("expect script:foo to finish before script:update starts","-SET-phase",config.getState().get("FOO"));
   }

}


