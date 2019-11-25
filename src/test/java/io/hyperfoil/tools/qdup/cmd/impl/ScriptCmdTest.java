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

      Cmd foo = config.getScript("foo");

      System.out.println("\n\n");
      System.out.println(foo.tree(2,true));

      Run doit = new Run("/tmp", config, dispatcher);

      doit.run();
      dispatcher.shutdown();

      assertEquals("script=update should invoke once per arg","-one-two-three-four",config.getState().get("FOO"));
   }
}
