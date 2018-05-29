package perf.qdup.cmd.impl;

import org.junit.Test;
import perf.qdup.SshTestBase;
import perf.qdup.State;
import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;
import perf.qdup.cmd.SpyCommandResult;
import perf.qdup.config.CmdBuilder;
import perf.qdup.config.YamlParser;

import static org.junit.Assert.*;

public class RegexTest extends SshTestBase {

    @Test
    public void lineEnding(){
        Cmd regex = Cmd.regex("^SUCCESS$");
        Context context = new Context(null,new State(""),null,null);
        SpyCommandResult result = new SpyCommandResult();

        result.clear();
        regex.run("SUCCESS",context,result);

        assertEquals("next should match entire pattern","SUCCESS",result.getNext());
        assertNull("regex should match",result.getSkip());
    }

    @Test
    public void removeDoubleSlashedRegex(){
        YamlParser parser = new YamlParser();
        parser.load("regex",stream(
                "regex: \".*? WFLYSRV0025: (?<eapVersion>.*?) started in (?<eapStartTime>\\\\d+)ms.*\""
        ));
        CmdBuilder builder = CmdBuilder.getBuilder();
        Cmd built = builder.buildYamlCommand(parser.getJson("regex"),null);
        assertTrue("built should be Regex:"+ builder.getClass(),built instanceof Regex);
        Regex regex = (Regex)built;
        assertFalse("should not contain \\\\\\\\",regex.getPattern().contains("\\\\\\\\"));
        assertTrue("should contain \\d",regex.getPattern().contains("\\d"));
    }
}
