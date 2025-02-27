package io.hyperfoil.tools.qdup.config;

import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.SshTestBase;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class RunConfigTest extends SshTestBase {

   @Test
   public void host_with_download_preserves_variables(){
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("pwd",
              """
              scripts:
                foo:
                  - signal: ${{signal:}}
              hosts:
                local:
                  username: HOST_USERNAME
                  hostname: HOST_HOSTNAME
                  download:
                  - ${{host.username}}
                  - ${{source}}
                  - ${{destination}}
              roles:
                doit:
                  hosts: [local]
                  run-scripts:
                  - bar:
              states:
                MASTER: ["one","two","three"]
              """.replaceAll("HOST_USERNAME",getHost().getUserName())
             .replaceAll("HOST_HOSTNAME",getHost().getHostName())
      ));
      RunConfig config = builder.buildConfig(parser);
      Set<Host> hosts = config.getAllHostsInRoles();

      assertEquals("expected 1 host",1,hosts.size());

      Host host = hosts.iterator().next();
      List<String> download = host.getDownload();

      assertEquals("download step count",3,download.size());
      assertEquals("download[0]","${{host.username}}",download.get(0));
      assertEquals("download[1]","${{source}}",download.get(1));
      assertEquals("download[2]","${{destination}}",download.get(2));
   }

   @Test @Ignore /* static scan does not evaluate content of set-signal*/
   public void signal_variable_in_script_with(){
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("pwd",
         """
         scripts:
           foo:
             - signal: ${{signal:}}
           bar:
           - set-signal:
               name: master-ready
               count: 3
           - for-each:
               name: host
               input: ${{MASTER}}
             then:
             - script:
                 name: foo
                 async: true
               with:
                 signal: master-ready
             - wait-for: master-ready
         hosts:
           local: TARGET_HOST
         roles:
           doit:
             hosts: [local]
             run-scripts:
             - bar:
         states:
           MASTER: ["one","two","three"]
         """.replaceAll("TARGET_HOST",getHost().toString())
      ));
      RunConfig config = builder.buildConfig(parser);
      long signalCount = config.getSignalCounts().count("master-ready");

      assertEquals("expect 3 signals",3,signalCount);

   }
}
