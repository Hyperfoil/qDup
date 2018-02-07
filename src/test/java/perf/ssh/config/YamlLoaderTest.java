package perf.ssh.config;

import org.junit.Assert;
import org.junit.Test;
import perf.ssh.State;
import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.Script;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

public class YamlLoaderTest {

    private static InputStream stream(String input){
        return new ByteArrayInputStream(input.getBytes());
    }


//    @Test
//    public void variableScriptInRoles(){
//        YamlLoader yamlLoader = new YamlLoader();
//        yamlLoader.load("variableScriptInRole.yaml",
//            stream("name: variableScriptInRole \n"+
//                "--- \n"+
//                "hosts:\n"+
//                "  local: user@localhost\n"+
//                "roles:\n"+
//                "  test:\n"+
//                "    run-scripts:\n"+
//                "     - ${{runScript}}\n"+
//                "    setup-scripts:\n"+
//                "     - ${{setupScript}}\n"+
//                "    cleanup-scripts:\n"+
//                "     - ${{cleanupScript}}\n"+
//                "    hosts:\n"+
//                "      local\n"+
//                "scripts:\n"+
//                " - alpha: \n"+
//                "    - sh: alpha \n"+
//                " - bravo: \n"+
//                "    - sh: bravo \n"+
//                " - charlie: \n"+
//                "    - sh: charlie \n"+
//                "states:\n" +
//                "  run:\n"+
//                "    runScript: alpha\n"+
//                "    setupScript: bravo\n"+
//                "    cleanupScript: charlie"
//            )
//        );
//
//        List<Script> localRunScripts = yamlLoader.getRunConfig().getRunScripts("local");
//        List<Script> localSetupScripts = yamlLoader.getRunConfig().getSetupScripts("local");
//        List<Script> localCleanupScripts = yamlLoader.getRunConfig().getCleanupScripts("local");
//        Assert.assertEquals("run scripts should contain 1 script",1,localRunScripts.size());
//        Assert.assertEquals("setup scripts should contain 1 script",1,localSetupScripts.size());
//        Assert.assertEquals("cleanup scripts should contain 1 script",1,localCleanupScripts.size());
//        Assert.assertEquals("run script should be alpha","alpha",localRunScripts.get(0).getName());
//        Assert.assertEquals("setup script should be bravo","bravo",localSetupScripts.get(0).getName());
//        Assert.assertEquals("setup script should be charlie","charlie",localCleanupScripts.get(0).getName());
//
//    }
//
//    @Test
//    public void multipleHostsInRole(){
//        YamlLoader yamlLoader = new YamlLoader();
//        yamlLoader.load("multipleHosts.yaml",
//            stream("name: multipleHosts \n"+
//               "---\n" +
//                "hosts:\n" +
//                "  client1: benchuser@benchclient1\n" +
//                "  client2: benchuser@benchclient2\n" +
//                "  client3: benchuser@benchclient3\n" +
//                "  client4: benchuser@benchclient4\n" +
//                "  server3:\n" +
//                "    username: root\n" +
//                "    hostname: benchserver3\n" +
//                "  server4:\n" +
//                "    username: benchuser\n" +
//                "    hostname: benchserver4\n" +
//                "    port: 22\n" +
//                "\n" +
//                "---\n"+
//                "roles:\n" +
//                "  ALL:\n" +
//                "    setup-scripts:\n" +
//                "     - sync-time\n" +
//                "    run-scripts:\n" +
//                "     - dstat\n" +
//                "#  database:\n" +
//                "#    hosts: server3\n" +
//                "#    run-scripts: docker-oracle\n" +
//                "  satellite:\n" +
//                "    hosts:\n" +
//                "      - client1\n" +
//                "      - client2\n" +
//                "      - client3\n" +
//                "      - client4\n" +
//                "    run-scripts:\n" +
//                "      - satellite\n" +
//                "  controller:\n" +
//                "    hosts:\n" +
//                "      - client1\n" +
//                "    run-scripts:\n" +
//                "      - controller\n" +
//                "  server:\n" +
//                "    hosts: server4\n" +
//                "    run-scripts:\n" +
//                "      - amq7\n")
//        );
//
//        HostList hostsInRole = yamlLoader.getRunConfig().getHostsInRole();
//        System.out.println(hostsInRole.toList());
//        Role satellite = yamlLoader.getRunConfig().getRole("satellite");
//
//        Assert.assertEquals("satellite should have 4 hosts",4,satellite.toList().size());
//    }
//
//    @Test
//    public void multipleDocuments(){
//        YamlLoader yamlLoader = new YamlLoader();
//        yamlLoader.load("states.yaml",
//                stream("name: specjms \n" +
//                        "scripts: \n"+
//                        " - test: \n"+
//                        "    - sh: alpha \n"+
//                        "---\n"+
//                        "states: \n"+
//                        "  run:\n" +
//                        "    foo: bar\n"
//
//                )
//        );
//        State runState = yamlLoader.getRunConfig().getState();
//        Assert.assertEquals("state should have one entry",1,runState.getKeys().size());
//    }
//
//    @Test
//    public void emptyState(){
//
//        YamlLoader yamlLoader = new YamlLoader();
//        yamlLoader.load("states.yaml",
//        stream("states:\n\n")
//        );
//        State runState = yamlLoader.getRunConfig().getState();
//        Assert.assertEquals("state should be empty",true,runState.getKeys().isEmpty());
//    }
//
//    @Test
//    public void allStateOptions(){
//        YamlLoader yamlLoader = new YamlLoader();
//        yamlLoader.load("states.yaml",
//            stream("states:\n" +
//                "  run:\n" +
//                "    FOO: 42\n" +
//                "\n" +
//                "  host:\n" +
//                "    local:\n" +
//                "      BAR : home\n" +
//                "      script:\n" +
//                "        myScript:\n" +
//                "          BUZ: bar")
//        );
//        Assert.assertEquals(
//            yamlLoader.getRunConfig().getErrors().stream().map(Object::toString).collect(Collectors.joining(",")),
//            false,
//            yamlLoader.getRunConfig().hasErrors()
//        );
//        State runState = yamlLoader.getRunConfig().getState();
//        Assert.assertEquals(1,runState.getChildNames().size());
//        Assert.assertEquals(true,runState.getChildNames().contains("local"));
//        State localState = runState.getChild("local");
//        Assert.assertEquals(1,localState.getChildNames().size());
//        Assert.assertEquals(true,localState.getChildNames().contains("myScript"));
//    }
//
//    @Test
//    public void commandNesting(){
//        YamlLoader yamlLoader = new YamlLoader();
//        yamlLoader.load("nmesting.yaml",
//            stream(
//                "scripts:\n" +
//                "  myScript:\n" +
//                "    - sh: 1\n" +
//                "    - - sh: 2\n" +
//                "    - - - sh: 3\n" +
//                "    - - sh: 4\n" +
//                "    - - - sh: 5\n" +
//                "    - sh: 6\n"
//            )
//        );
//        Assert.assertEquals(
//                yamlLoader.getRunConfig().getErrors().stream().map(Object::toString).collect(Collectors.joining(",")),
//                false,
//                yamlLoader.getRunConfig().hasErrors()
//        );
//        Script myScript = yamlLoader.getRunConfig().getScript("myScript");
//        Cmd one = myScript.getNext();
//        Cmd two = one.getNext();
//        Cmd four = two.getSkip();
//        Cmd six = one.getSkip();
//        Assert.assertEquals("sh: 1 should have a tail of 5",true,one.getTail().toString().contains("5"));
//        Assert.assertEquals("sh: 1 should have a skip of 6",true,one.getSkip().toString().contains("6"));
//        Assert.assertEquals("sh: 2 should have a tail of 3",true,two.getTail().toString().contains("3"));
//        Assert.assertEquals("sh: 4 should have a skip of 6",true,four.getSkip().toString().contains("6"));
//    }
}
