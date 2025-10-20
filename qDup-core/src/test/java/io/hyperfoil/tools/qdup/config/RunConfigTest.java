package io.hyperfoil.tools.qdup.config;

import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.SshTestBase;
import io.hyperfoil.tools.qdup.cmd.Script;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class RunConfigTest extends SshTestBase {

    @Test
    public void script_load_order(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("local",
                """
                scripts:
                  uno:
                  - sh: echo "hi"
                """
        ));
        builder.loadYaml(parser.loadFile("remote",
                """
                scripts:
                  uno:
                  - wait-for: bar
                """
        ));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());

        Script uno = config.getScript("uno");
        assertNotNull("uno", uno);
        assertTrue(uno.getNext().toString().contains("sh"));
    }

   @Test
   public void host_load_order(){
       Parser parser = Parser.getInstance();
       RunConfigBuilder builder = getBuilder();
       builder.loadYaml(parser.loadFile("local",
               """
               hosts:
                 uno: LOCAL//quay.io/fedora/fedora
               """
       ));
       builder.loadYaml(parser.loadFile("remote",
               """
               scripts:
                 foo:
                 - sh: echo "foo"
               hosts:
                 uno: user@localhost:2222
               roles:
                 test:
                   hosts:
                   - uno
                   setup-scripts:
                   - foo
               """
       ));
       RunConfig config = builder.buildConfig(parser);
       assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
       Set<Host> hosts = config.getAllHostsInRoles();
       assertEquals(1, hosts.size());
       Host first = hosts.iterator().next();
       assertNotNull(first);
       assertTrue(first.isLocal());
       assertTrue(first.isContainer());
       assertTrue(first.getDefinedContainer().contains("fedora"));
   }


   @Test
   public void getAllHostsInRoles_alias_same_host(){
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("pwd",
              """
              scripts:
                foo:
                  - signal: ${{signal:}}
              hosts:
                uno: LOCAL//quay.io/fedora/fedora
                dos: LOCAL//quay.io/fedora/fedora
              roles:
                one:
                  hosts: [uno]
                  run-scripts:
                  - bar:
                two:
                  hosts: [dos]
                  run-scripts:
                  - bar:
              states:
                MASTER: ["one","two","three"]
              """.replaceAll("HOST_USERNAME",getHost().getUserName())
                      .replaceAll("HOST_HOSTNAME",getHost().getHostName())
      ));
      RunConfig config = builder.buildConfig(parser);
      Set<Host> hosts = config.getAllHostsInRoles();
      assertEquals(2,hosts.size());

   }

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
