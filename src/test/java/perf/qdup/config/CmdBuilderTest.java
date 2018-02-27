package perf.qdup.config;

import org.junit.Test;
import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.impl.Sh;
import perf.yaup.json.Json;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;



public class CmdBuilderTest {

    private static InputStream stream(String input){
        return new ByteArrayInputStream(input.getBytes());
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
    public void shSilent(){
        Json sh = Json.fromString("{\"key\":\"sh\",\"lineNumber\":1,\"child\":[[{\"key\":\"command\",\"lineNumber\":2,\"value\":\"tail -f server.log\"},{\"key\":\"silent\",\"lineNumber\":3,\"value\":\"true\"}]]}");

        CmdBuilder builder = CmdBuilder.getBuilder();
        Cmd command = builder.buildYamlCommand(sh,null);

        assertTrue("command should be sh",Sh.class.equals(command.getClass()));
        assertTrue("command should not be logging",command.isSilent());
    }


}
