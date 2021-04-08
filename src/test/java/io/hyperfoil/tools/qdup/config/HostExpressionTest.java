package io.hyperfoil.tools.qdup.config;

import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.SshTestBase;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class HostExpressionTest extends SshTestBase {


   @Test
   public void add_roles(){
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();

      builder.loadYaml(parser.loadFile("json", stream("" +
            "scripts:",
         "  foo:",
         "  - echo: FOO",
         "hosts:",
         "  alpha: user@alpha",
         "  bravo: user@bravo",
         "  charlie: user@charlie",
         "  delta: user@delta",
         "roles:",
         "  ant:",
         "    hosts: [alpha]",
         "    run-scripts: [foo]",
         "  bat:",
         "    hosts: [bravo]",
         "    run-scripts: [foo]",
         "  cat:",
         "    hosts: [charlie]",
         "    run-scripts: [foo]"
      )));
      RunConfig config = builder.buildConfig(parser);

      HostExpression hostExpression = new HostExpression("= ant + bat + cat");

      List<Host> hosts = hostExpression.getHosts(config);

      assertEquals("expression should contain 3 hosts: "+hosts,3,hosts.size());
   }


   @Test
   public void subtract_from_all(){
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();

      builder.loadYaml(parser.loadFile("json", stream("" +
            "scripts:",
         "  foo:",
         "  - echo: FOO",
         "hosts:",
         "  alpha: user@alpha",
         "  bravo: user@bravo",
         "  charlie: user@charlie",
         "  delta: user@delta",
         "roles:",
         "  ant:",
         "    hosts: [alpha]",
         "    run-scripts: [foo]",
         "  bat:",
         "    hosts: [bravo]",
         "    run-scripts: [foo]",
         "  cat:",
         "    hosts: [charlie]",
         "    run-scripts: [foo]"
      )));
      RunConfig config = builder.buildConfig(parser);

      HostExpression hostExpression = new HostExpression("= "+RunConfigBuilder.ALL_ROLE+" - cat");

      List<Host> hosts = hostExpression.getHosts(config);

      assertEquals("expression should contain 2 hosts: "+hosts,2,hosts.size());
   }

}
