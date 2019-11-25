package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.JsonServer;
import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.cmd.Result;
import io.hyperfoil.tools.qdup.config.CmdBuilder;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.waml.WamlParser;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import org.junit.Ignore;
import org.junit.Test;
import io.hyperfoil.tools.qdup.SshTestBase;
import io.hyperfoil.tools.qdup.cmd.SpyContext;
import io.hyperfoil.tools.yaup.json.Json;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class RegexTest extends SshTestBase {

    @Test @Ignore
    public void systemctlBug(){
       Parser parser = Parser.getInstance();
       RunConfigBuilder builder = new RunConfigBuilder(CmdBuilder.getBuilder());
       WamlParser wamlParser = new WamlParser();

       StringBuilder sb = new StringBuilder();

       wamlParser.load("test",stream(""+
             "scripts:",
          "  foo:",
          "  - sh: sudo systemctl status docker",
          "    - regex: \"\\s*Active: (?<active>\\w+) \\(.*\" #Test to see if docker is running",
          "      - log: active=${{active}}",
          "hosts:",
          "  local: root@benchclient1.perf.lab.eng.rdu2.redhat.com:22",//+getHost(),
          "roles:",
          "  doit:",
          "    hosts: [local]",
          "    run-scripts: [foo]"
       ));
       builder.loadWaml(wamlParser);
       RunConfig config = builder.buildConfig();
       Dispatcher dispatcher = new Dispatcher();

       Cmd foo = config.getScript("foo");
       foo.getNext().getNext().injectThen(Cmd.code(((input, state) -> {
          return Result.next(input);
       })));

       Run doit = new Run("/tmp",config,dispatcher);

       JsonServer jsonServer = new JsonServer(doit);
       jsonServer.start();
       doit.run();
       dispatcher.shutdown();
    }

    @Test @Ignore
    public void one_line_in_multi_line(){
       Cmd regex = Cmd.regex("(?<date>\\d{4}-\\d{2}-\\d{2})\\s+(?<time>\\d{2}:\\d{2}:\\d{2})\\s+(?<offset>[+-]\\d{4})");
       SpyContext context = new SpyContext();

       regex.run(
          "fatal: unable to read source tree (ea9f40f5940637b18c197952e7d0bd0a28185ae9)"
             +"\n"+"2019-10-01 16:21:04 -1000",
          context);

       System.out.println(context.getState().get("date"));
    }

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
