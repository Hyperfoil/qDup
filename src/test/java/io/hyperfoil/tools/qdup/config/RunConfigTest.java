package io.hyperfoil.tools.qdup.config;

import io.hyperfoil.tools.qdup.SshTestBase;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RunConfigTest extends SshTestBase {

   @Test @Ignore /* static scan does not evaluate content of set-signal*/
   public void signal_variable_in_script_with(){
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("pwd", stream(
         "" +
            "scripts:",
         "  foo:",
         "    - signal: ${{signal:}}",
         "  bar:",
         "  - set-signal:",
         "      name: master-ready",
         "      count: 3",
         "  - for-each:",
         "      name: host",
         "      input: ${{MASTER}}",
         "    then:",
         "    - script:",
         "        name: foo",
         "        async: true",
         "      with:",
         "        signal: master-ready",
         "    - wait-for: master-ready",
         "hosts:",
         "  local: " + getHost(),
         "roles:",
         "  doit:",
         "    hosts: [local]",
         "    run-scripts:",
         "    - bar:",
         "states:",
         "  MASTER: [\"one\",\"two\",\"three\"]"
      )));
      RunConfig config = builder.buildConfig(parser);
      long signalCount = config.getSignalCounts().count("master-ready");

      assertEquals("expect 3 signals",3,signalCount);

   }
}
