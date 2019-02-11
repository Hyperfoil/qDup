package perf.qdup.cmd.impl;

import org.junit.Test;
import perf.qdup.SshTestBase;
import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.SpyContext;
import perf.qdup.config.CmdBuilder;
import perf.qdup.config.waml.WamlParser;
import perf.yaup.json.Json;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class RegexTest extends SshTestBase {

    @Test
    public void lineEnding(){
        Cmd regex = Cmd.regex("^SUCCESS$");
        SpyContext context = new SpyContext();

        context.clear();
        regex.run("SUCCESS",context);

        assertEquals("next should match entire pattern","SUCCESS",context.getNext());
        assertNull("regex should match",context.getSkip());
    }

    @Test
    public void named_capture(){
                Cmd regex = Cmd.regex("(?<all>.*)");
                SpyContext context = new SpyContext();
                context.clear();
                regex.run("foo",context);

                        assertEquals("state.get(all) should be foo","foo",context.getState().get("all"));
            }
    @Test
    public void named_capture_with_dots(){
                Cmd regex = Cmd.regex("(?<all.with.dots>.*)");
                SpyContext context = new SpyContext();
                context.clear();
                regex.run("foo",context);

                        assertEquals("state.get(all.with.dots) should be foo","foo",context.getState().get("all.with.dots"));
                Object all = context.getState().get("all");
                assertTrue("state.get(all) should return json",all instanceof Json);
            }


    @Test
    public void removeDoubleSlashedRegex(){
        WamlParser parser = new WamlParser();
        parser.load("regex",stream(
                "regex: \".*? WFLYSRV0025: (?<eapVersion>.*?) started in (?<eapStartTime>\\\\d+)ms.*\""
        ));
        CmdBuilder builder = CmdBuilder.getBuilder();
        List<String> errors = new ArrayList<>();
        Cmd built = builder.buildYamlCommand(parser.getJson("regex"),null,errors);
        assertTrue("built should be Regex:"+ builder.getClass(),built instanceof Regex);
        Regex regex = (Regex)built;
        assertFalse("should not contain \\\\\\\\",regex.getPattern().contains("\\\\\\\\"));
        assertTrue("should contain \\d",regex.getPattern().contains("\\d"));
    }
}
