package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.JsonServer;
import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.SshTestBase;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import org.junit.Test;

import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class ExecTest extends SshTestBase {


   @Test
   public void async_next_sibling_invoke_count_and_order() {
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("signal", stream("" +
            "scripts:",
         "  foo:",
         "    - sh: pwd",
         "    - exec:",
         "        command: date '+%s'",
         "        async: true",
         "      then:",
         "      - set-state: RUN.BAR",
         "    - set-state: RUN.PWD",
         "    - set-state: RUN.FOO ${{=${{RUN.FOO:0}}+1}}",
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
      assertEquals("expect FOO to be 1", 1L, config.getState().get("FOO"));
      assertNotNull("expect PWD", config.getState().get("PWD"));
      assertTrue("expect PWD starts with /", config.getState().get("PWD").toString().startsWith("/"));
      assertNotNull("expect BAR", config.getState().get("BAR"));
      assertTrue("expect BAR is a unix seconds since epoch " + config.getState().get("BAR"), config.getState().get("BAR").toString().matches("^\\d+$"));
   }

   @Test
   public void async_invoke_count() {
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("signal", stream("" +
            "scripts:",
         "  foo:",
         "    - sh: pwd",
         "    - exec:",
         "        command: echo 'alpha'",
         "        async: true",
         "      then:",
         "      - set-state: RUN.A",
         "      - set-state: RUN.FOO ${{=${{RUN.FOO:0}}+1}}",
         "    - exec:",
         "        command: echo 'bravo'",
         "        async: true",
         "      then:",
         "      - set-state: RUN.B",
         "      - set-state: RUN.FOO ${{=${{RUN.FOO:0}}+1}}",
         "    - exec:",
         "        command: echo 'charlie'",
         "        async: true",
         "      then:",
         "      - set-state: RUN.C",
         "      - set-state: RUN.FOO ${{=${{RUN.FOO:0}}+1}}",
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
      JsonServer jsonServer = new JsonServer(doit, 31337);
      //TODO somehow starting the jsonServer causes this test to not stop
//      jsonServer.start();
      doit.run();
      jsonServer.stop();
      assertNotNull("expect FOO", config.getState().get("FOO"));
      assertEquals("expect FOO to be 3", 3L, config.getState().get("FOO"));
      assertNotNull("expect A", config.getState().get("A"));
      assertEquals("expect A to be a", "alpha", config.getState().get("A"));
      assertNotNull("expect B", config.getState().get("B"));
      assertEquals("expect B to be b", "bravo", config.getState().get("B"));
      assertNotNull("expect C", config.getState().get("C"));
      assertEquals("expect C to be c", "charlie", config.getState().get("C"));
   }
}
