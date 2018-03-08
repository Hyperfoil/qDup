package perf.qdup.config;

import org.junit.Test;
import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.impl.SetState;
import perf.qdup.cmd.impl.Sh;
import perf.qdup.cmd.impl.XmlCmd;
import perf.yaup.json.Json;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static perf.qdup.SshTestBase.stream;


public class CmdBuilderTest {


    @Test
    public void splitSpaces(){
        CmdBuilder builder = CmdBuilder.getBuilder();
        List<String> out = builder.split("foo   bar");

        assertEquals("split \"foo bar\" should create 2 entries",2,out.size());
    }
    @Test
    public void splitQuoted(){
        CmdBuilder builder = CmdBuilder.getBuilder();
        List<String> out = builder.split("\"foo \t\\\"bar\"  bar");

        assertEquals("split \"foo bar\" should create 2 entries",2,out.size());
    }
    @Test
    public void splitNotQuoteThenQuote(){
        CmdBuilder builder = CmdBuilder.getBuilder();
        List<String> out = builder.split("EXECUTOR \"unzip \"");

        assertEquals("split should create 2 entries",2,out.size());
    }
    @Test
    public void shSilent(){
        Json sh = Json.fromString("{\"key\":\"sh\",\"lineNumber\":1,\"child\":[[{\"key\":\"command\",\"lineNumber\":2,\"value\":\"tail -f server.log\"},{\"key\":\"silent\",\"lineNumber\":3,\"value\":\"true\"}]]}");

        CmdBuilder builder = CmdBuilder.getBuilder();
        Cmd command = builder.buildYamlCommand(sh,null);

        assertTrue("command should be sh",Sh.class.equals(command.getClass()));
        assertTrue("command should not be logging",command.isSilent());
    }



    @Test
    public void setStateTwoArgs(){
        YamlParser foo = new YamlParser();
        YamlParser parser = new YamlParser();
        parser.load("setstate",stream(""+
            "set-state: EXECUTOR \"unzip \""
        ));

        CmdBuilder cmdBuilder = CmdBuilder.getBuilder();

        Cmd built = cmdBuilder.buildYamlCommand(parser.getJson("setstate"),null);
        assertTrue("built "+built.getClass().getName(),built instanceof SetState);

    }

    @Test
    public void xmlOperationList(){
        YamlParser foo = new YamlParser();

        YamlParser parser = new YamlParser();
        parser.load("listCommandArgument",stream(""+
                "xml: ",
                "  path: \"xmlPath\"",
                "  operations: [",
                "    \"/foo/bar/biz/@attr == buz\"",
                "    \"/foo/bar/biz ++ <fizz/>\"",
                "  ]",
                ""
        ));

        CmdBuilder cmdBuilder = CmdBuilder.getBuilder();

        Cmd built = cmdBuilder.buildYamlCommand(parser.getJson("listCommandArgument"),null);

        assertTrue(built instanceof XmlCmd);
        XmlCmd builtXml = (XmlCmd)built;
        assertEquals("xmlPath",builtXml.getPath());
        assertEquals("operation count",2,builtXml.getOperations().size());
        assertEquals("operation[0]","/foo/bar/biz/@attr == buz",builtXml.getOperations().get(0));
        assertEquals("operation[1]","/foo/bar/biz ++ <fizz/>",builtXml.getOperations().get(1));
    }


}
