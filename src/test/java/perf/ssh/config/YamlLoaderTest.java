package perf.ssh.config;

import org.junit.Assert;
import org.junit.Test;
import perf.ssh.State;
import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.Script;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.stream.Collectors;

public class YamlLoaderTest {


    @Test
    public void allStateOptions(){
        YamlLoader yamlLoader = new YamlLoader();
        yamlLoader.load("states.yaml",
            new StringReader("states:\n" +
                "  run:\n" +
                "    FOO: 42\n" +
                "\n" +
                "  host:\n" +
                "    local:\n" +
                "      BAR : home\n" +
                "      script:\n" +
                "        myScript:\n" +
                "          BUZ: bar")
        );
        Assert.assertEquals(
            yamlLoader.getErrors().stream().map(Object::toString).collect(Collectors.joining(",")),
            false,
            yamlLoader.hasErrors()
        );
        State runState = yamlLoader.getRunConfig().getState();
        Assert.assertEquals(1,runState.getChildNames().size());
        Assert.assertEquals(true,runState.getChildNames().contains("local"));
        State localState = runState.getChild("local");
        Assert.assertEquals(1,localState.getChildNames().size());
        Assert.assertEquals(true,localState.getChildNames().contains("myScript"));
    }

    @Test
    public void commandNesting(){
        YamlLoader yamlLoader = new YamlLoader();
        yamlLoader.load("nmesting.yaml",
            new StringReader(
                "scripts:\n" +
                "  myScript:\n" +
                "    - sh: 1\n" +
                "    - - sh: 2\n" +
                "    - - - sh: 3\n" +
                "    - - sh: 4\n" +
                "    - - - sh: 5\n" +
                "    - sh: 6\n"
            )
        );
        Assert.assertEquals(
                yamlLoader.getErrors().stream().map(Object::toString).collect(Collectors.joining(",")),
                false,
                yamlLoader.hasErrors()
        );
        Script myScript = yamlLoader.getRunConfig().getScript("myScript");
        Cmd one = myScript.getNext();
        Cmd two = one.getNext();
        Cmd four = two.getSkip();
        Cmd six = one.getSkip();
        Assert.assertEquals("sh: 1 should have a tail of 5",true,one.getTail().toString().contains("5"));
        Assert.assertEquals("sh: 1 should have a skip of 6",true,one.getSkip().toString().contains("6"));
        Assert.assertEquals("sh: 2 should have a tail of 3",true,two.getTail().toString().contains("3"));
        Assert.assertEquals("sh: 4 should have a skip of 6",true,four.getSkip().toString().contains("6"));
    }
}
