package perf.qdup.config;

import org.junit.Test;
import perf.qdup.SshTestBase;
import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.impl.*;
import perf.yaup.json.Json;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static perf.qdup.SshTestBase.stream;


public class CmdBuilderTest extends SshTestBase {

    @Test
    public void buildYamlCommand_invalidCommand(){
        YamlParser parser = new YamlParser();
        parser.load("download",stream("-wait=for: P"));
        List<String> errors = new ArrayList<>();
        Cmd cmd = CmdBuilder.getBuilder().buildYamlCommand(parser.getJson("download"),null,errors);
        assertTrue("cmd should be NO_OP",cmd.toString().contains("NO_OP"));
    }

    @Test
    public void download_path(){
        YamlParser parser = new YamlParser();
        parser.load("download",stream("-download: P"));
        List<String> errors = new ArrayList<>();
        Cmd cmd = CmdBuilder.getBuilder().buildYamlCommand(parser.getJson("download"),null,errors);
        assertTrue("cmd should be Download",cmd instanceof Download);
        Download download = (Download)cmd;
        assertEquals("unexpected path","P",download.getPath());
        assertEquals("unexpected destination","",download.getDestination());
    }
    @Test
    public void download_path_destination(){
        YamlParser parser = new YamlParser();
        parser.load("download",stream("-download: P D"));
        List<String> errors = new ArrayList<>();
        Cmd cmd = CmdBuilder.getBuilder().buildYamlCommand(parser.getJson("download"),null,errors);
        assertTrue("cmd should be Download",cmd instanceof Download);
        Download download = (Download)cmd;
        assertEquals("unexpected path","P",download.getPath());
        assertEquals("unexpected destination","D",download.getDestination());
    }

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
        List<String> errors = new ArrayList<>();
        Cmd command = builder.buildYamlCommand(sh,null,errors);

        assertTrue("command should be sh",Sh.class.equals(command.getClass()));
        assertTrue("command should not be logging",command.isSilent());
    }


    @Test
    public void queueDownloadCmd(){
        YamlParser parser = new YamlParser();
        parser.load("queueDownload",stream(
                "queue-download: /tmp/wf.webprofile.console.log"
        ));

        CmdBuilder cmdBuilder = CmdBuilder.getBuilder();
        List<String> errors = new ArrayList<>();
        Cmd built = cmdBuilder.buildYamlCommand(parser.getJson("queueDownload"),null,errors);
        assertTrue("built "+built.getClass().getName(),built instanceof QueueDownload);
    }

    @Test
    public void ctrlCCmd(){
        YamlParser parser = new YamlParser();
        parser.load("ctrlC",stream("ctrlC:"));
        CmdBuilder cmdBuilder = CmdBuilder.getBuilder();
        List<String> errors = new ArrayList<>();
        Cmd built = cmdBuilder.buildYamlCommand(parser.getJson("ctrlC"),null,errors);

        assertTrue("built "+built.getClass().getName(),built instanceof CtrlC);

    }
    @Test
    public void sleepCmd(){
        YamlParser parser = new YamlParser();
        parser.load("sleep",stream(""+
                "sleep: 5m"
        ));

        CmdBuilder cmdBuilder = CmdBuilder.getBuilder();
        List<String> errors = new ArrayList<>();
        Cmd built = cmdBuilder.buildYamlCommand(parser.getJson("sleep"),null,errors);
        assertTrue("built "+built.getClass().getName(),built instanceof Sleep);
        assertEquals("5m",((Sleep)built).getAmount());
    }

    @Test
    public void setStateTwoArgs(){
        YamlParser parser = new YamlParser();
        parser.load("setstate",stream(""+
            "set-state: EXECUTOR \"unzip \""
        ));

        CmdBuilder cmdBuilder = CmdBuilder.getBuilder();
        List<String> errors = new ArrayList<>();
        Cmd built = cmdBuilder.buildYamlCommand(parser.getJson("setstate"),null,errors);
        assertTrue("built "+built.getClass().getName(),built instanceof SetState);

    }

    @Test
    public void sh_echoEnvironmentVariable(){
        YamlParser parser = new YamlParser();
        parser.load("echo",stream(""+
        "sh: echo ${SERVER_PID}"));

        CmdBuilder cmdBuilder = CmdBuilder.getBuilder();
        List<String> errors = new ArrayList<>();
        Cmd built = cmdBuilder.buildYamlCommand(parser.getJson("echo"),null,errors);

        assertTrue(built instanceof Sh);
        assertTrue(built.toString().contains("${SERVER_PID}"));
    }
    @Test
    public void sh_Responsemap(){
        YamlParser parser = new YamlParser();
        parser.load("response",stream(""+
            "sh: {",
            "  silent: true",
            "  command: su",
            "  prompt : {",
            "    \"Password:\" : passwordValue",
            "  }",
            "}"
                ));
        CmdBuilder builder = CmdBuilder.getBuilder();
        List<String> errors = new ArrayList<>();
        Cmd built = builder.buildYamlCommand(parser.getJson("response"),null,errors);
        assertTrue("expect a sh",(built instanceof Sh));
        Sh sh = (Sh)built;
        assertFalse("expect sh to have a prompt",sh.getPrompt().isEmpty());
        assertTrue("expect sh to have Password: prompt",sh.getPrompt().containsKey("Password:"));
    }

    @Test
    public void xmlOperationList(){
        YamlParser parser = new YamlParser();
        parser.load("listCommandArgument",stream(""+
                "xml: ",
                "  path: \"xmlPath\"",
                "  operations: [",
                "    \"/foo/bar/biz/@attr == ",
                "buz\"",
                "    \"/foo/bar/biz ++ <fizz/>\"",
                "  ]",
                ""
        ));

        CmdBuilder cmdBuilder = CmdBuilder.getBuilder();
        List<String> errors = new ArrayList<>();
        Cmd built = cmdBuilder.buildYamlCommand(parser.getJson("listCommandArgument"),null,errors);

        assertTrue(built instanceof XmlCmd);
        XmlCmd builtXml = (XmlCmd)built;
        assertEquals("xmlPath",builtXml.getPath());
        assertEquals("operation count",2,builtXml.getOperations().size());
        assertEquals("operation[0]="+builtXml.getOperations().get(0),"/foo/bar/biz/@attr ==\nbuz",builtXml.getOperations().get(0));
        assertEquals("operation[1]="+builtXml.getOperations().get(1),"/foo/bar/biz ++ <fizz/>",builtXml.getOperations().get(1));
    }


}
