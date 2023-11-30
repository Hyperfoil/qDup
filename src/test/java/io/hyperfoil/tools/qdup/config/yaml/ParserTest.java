package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.SshTestBase;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Script;
import io.hyperfoil.tools.qdup.cmd.impl.*;

import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class ParserTest extends SshTestBase {

    public static String join(String...args){
        return Arrays.asList(args).stream().collect(Collectors.joining("\n"));
    }

    @Test
    public void split_tab_separated(){
        List<String> split = Parser.split("one\ttwo\tthree");
        assertEquals("expect 3 entries:"+split,3,split.size());
        assertEquals("split[0]","one",split.get(0));
        assertEquals("split[1]","two",split.get(1));
        assertEquals("split[2]","three",split.get(2));
    }

    @Test
    public void split_space_separated(){
        List<String> split = Parser.split("one two three");
        assertEquals("expect 3 entries:"+split,3,split.size());
        assertEquals("split[0]","one",split.get(0));
        assertEquals("split[1]","two",split.get(1));
        assertEquals("split[2]","three",split.get(2));
    }

    @Test
    public void split_dont_split_statepattern(){
        List<String> split = Parser.split("one ${{ ignore spaces }} three");
        assertEquals("expect 3 entries:"+split,3,split.size());
        assertEquals("split[0]","one",split.get(0));
        assertEquals("split[1]","${{ ignore spaces }}",split.get(1));
        assertEquals("split[2]","three",split.get(2));
    }

    @Test
    public void split_dont_split_statepattern_custom(){
        List<String> split = Parser.split("one $[[ ignore spaces ]] three","$[[","]]");
        assertEquals("expect 3 entries:"+split,3,split.size());
        assertEquals("split[0]","one",split.get(0));
        assertEquals("split[1]","$[[ ignore spaces ]]",split.get(1));
        assertEquals("split[2]","three",split.get(2));
    }

    @Test
    public void split_dont_split_statepattern_nested(){
        List<String> split = Parser.split("one ${{ ${{ignore}} ${{spaces}} }} three");
        assertEquals("expect 3 entries:"+split,3,split.size());
        assertEquals("split[0]","one",split.get(0));
        assertEquals("split[1]","${{ ${{ignore}} ${{spaces}} }}",split.get(1));
        assertEquals("split[2]","three",split.get(2));
    }


    @Test
    public void load_regex_else(){
        Parser parser = Parser.getInstance();
        YamlFile loaded = parser.loadFile("test",join(""+
           "scripts:",
           "  foo:",
           "  - regex: foo",
           "    else:",
           "    - echo",
           "    then:",
           "    - done"
        ));

        assertNotNull(loaded);
        assertTrue(loaded.getScripts().containsKey("foo"));
        Script script = loaded.getScripts().get("foo");

        Cmd next = script.getNext();

        assertTrue("command should be a regex "+next,next instanceof Regex);
        Regex r = (Regex)next;
        assertTrue("regex should have commands on miss",r.hasElse());
        assertFalse("regex should not be for miss",r.isMiss());
        assertTrue("regex should have then commands",r.hasThens());

        String output = parser.dump(loaded);

        assertTrue("yaml contains else",output.contains("else"));
        assertTrue("yaml contains then",output.contains("then"));
        assertFalse("yaml should not contain miss",output.contains("miss"));
    }

    @Test
    public void state_without_value(){
        Parser parser = Parser.getInstance();
        YamlFile loaded = parser.loadFile("test",join(""+
                "states:",
                "  foo: bar",
                "  biz:",
                "  buz:"
        ));

        assertNotNull(loaded);

        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(loaded);

        RunConfig config = builder.buildConfig(parser);

        assertEquals("state should have biz\n"+config.getState().toJson().toString(2),"",config.getState().get("biz"));
        assertEquals("state should have buz\n"+config.getState().toJson().toString(2),"",config.getState().get("buz"));

    }


    @Test
    public void load_sh_all_opts(){
        Parser parser = Parser.getInstance();
        YamlFile loaded = parser.loadFile("test",join(""+
           "scripts:",
           "  foo:",
           "  - sh: ./test.sh",
           "    with:",
           "      one: 'uno'",
           "    watch:",
           "    - echo",
           "    on-signal:",
           "      bar:",
           "      - log: message",
           "    timer:",
           "      10s:",
           "      - ctrlC",
           "    then:",
           "    - done"
        ));

        assertNotNull(loaded);
        assertTrue(loaded.getScripts().containsKey("foo"));
        Script script = loaded.getScripts().get("foo");

        Cmd next = script.getNext();
        assertTrue("has with",!next.getWith().isEmpty());
        assertTrue("has watch",next.hasWatchers());
        assertTrue("has onsignal",next.hasSignalWatchers());
        assertTrue("has timer",next.hasTimers());
        assertTrue("has then",!next.getThens().isEmpty());

        String output = parser.dump(loaded);
        assertTrue(output,output.contains("with"));
        assertTrue(output,output.contains("one"));
        assertTrue(output,output.contains("watch"));
        assertTrue(output,output.contains("echo"));
        assertTrue(output,output.contains("on-signal"));
        assertTrue(output,output.contains("message"));
        assertTrue(output,output.contains("timer"));
        assertTrue(output,output.contains("ctrlC"));
        assertTrue(output,output.contains("done"));

    }

    @Test
    public void load_cmd_no_args_nested(){
        Parser parser = Parser.getInstance();
        YamlFile loaded = parser.loadFile("test",join(""+
           "scripts:",
           "  foo:",
           "  - ctrlC:",
           "    then:",
           "    - echo"
        ));

        assertNotNull("should be able to load a nested string cmd",loaded);
        assertTrue("missing foo script",loaded.getScripts().containsKey("foo"));

        Script foo = loaded.getScripts().get("foo");



        assertTrue("tail is echo",foo.getTail() instanceof Echo);

        String output = parser.dump(loaded);
        assertTrue("echo in output",output.contains("echo"));
        assertFalse("echo: not in output",output.contains("echo:"));
    }

    @Test
    public void cmd_with_else(){
        Parser p = Parser.getInstance();
        YamlFile loaded = p.loadFile("test",join(""+
                "scripts:",
                "  foo:",
                "  - read-state: BAR",
                "    else:",
                "    - sh: ls",
                "  - read-signal: FOO",
                "    else:",
                "    - sh: pwd"
                ));
        assertTrue("missing foo script",loaded.getScripts().containsKey("foo"));
        Script foo = loaded.getScripts().get("foo");
        assertTrue("next should be ReadState",foo.getNext() instanceof ReadState);
        assertTrue("read-state next next should be read-signal",foo.getNext().getNext() instanceof ReadSignal);
        String output = p.dump(loaded);
        assertTrue("yaml output should include ls\n"+output,output.contains("ls"));
        assertTrue("yaml output should include pwd\n"+output,output.contains("pwd"));

    }

    @Test
    public void load_cmd_no_args(){
        Parser parser = Parser.getInstance();
        YamlFile loaded = parser.loadFile("test",join(""+
           "scripts:",
           "  foo:",
           "  - ctrlC:",
           "  - echo",
           "  - done"
        ));
        assertTrue("missing foo script",loaded.getScripts().containsKey("foo"));
        Script foo = loaded.getScripts().get("foo");
        assertTrue("next should be ctrlC",foo.getNext() instanceof CtrlC);

        String output = parser.dump(loaded);
        assertTrue("missing ctrlC cmd",output.contains("ctrlC"));
        assertFalse("ctrlC should encode as a string",output.contains("ctrlC:"));
        assertTrue("missing echo cmd",output.contains("echo"));
        assertFalse("echo should encode as a string",output.contains("echo:"));
        assertTrue("missing done cmd",output.contains("done"));
        assertFalse("done should encode as a string",output.contains("done:"));
    }

    @Test
    public void load_timer_child(){
        Parser parser = Parser.getInstance();
        YamlFile loaded = parser.loadFile("test",join(""+
            "scripts:",
            "  # Records system-wide perf events",
            "  perf-record: #WAIT_START WAIT_STOP PERF_DATA",
            "    - wait-for: ${{WAIT_START:}}",
            "    - sh: perf record -a -g -o ${{PERF_DATA:/tmp/perf.data}} & export PERF_RECORD_PID=\"$!\"",
            "    - wait-for: ${{WAIT_STOP}}",
            "    - sh: kill ${PERF_RECORD_PID}",
            "      timer:",
            "        5s: # No idea why this is getting stuck at times",
            "        - ctrlC"
       ));
        assertNotNull(loaded);
        assertTrue(loaded.getScripts().containsKey("perf-record"));
        Script script = loaded.getScripts().get("perf-record");
        assertTrue(script.getTail().hasTimers());

    }

    @Test
    public void error_sh_incorrect_then_indent(){
        Parser parser = Parser.getInstance();
        YamlFile loaded = parser.loadFile("/test", join("" +
            "scripts:",
            "  foo:",
            "  - sh: ",
            "      command: pwd",
            "      then:",
            "      - set-state: PWD"
        ));
        assertNull("loaded should not be null",loaded);
    }
    @Test
    public void error_regex_incorrect_then_indent(){
        Parser parser = Parser.getInstance();
        YamlFile loaded = parser.loadFile("/test", join("" +
            "scripts:",
            "  foo:",
            "  - regex: ",
            "      pattern: foo",
            "      then:",
            "      - abort: boo foo"
        ));
        assertNull("loaded should not be null",loaded);
    }

    @Test
    public void load_yaml(){
        Parser parser = Parser.getInstance();
        YamlFile loaded = parser.loadFile("/test", join("" +
          "scripts:",
           "  foo:",
           "  - sh: pwd",
           "    then:",
           "    - set-state: PWD"
        ));

        assertNotNull("loaded should not be null",loaded);
        assertEquals("loaded scripts",1,loaded.getScripts().size());
        assertEquals("loaded hosts",0,loaded.getHostDefinitions().size());
        assertEquals("loaded roles",0,loaded.getRoles().size());
        assertEquals("loaded state",0,loaded.getState().allKeys().size());
    }

    @Test
    public void load_waml(){
        Parser parser = Parser.getInstance();
        YamlFile loaded = parser.loadFile("/test", join("" +
          "scripts:",
           "  foo:",
           "  - sh: pwd",
           "    with:",
           "      FOO: One,Two,Three",
           "  - set-state: PWD"
        ));
        assertNotNull("loaded should not be null",loaded);
        assertEquals("loaded scripts",1,loaded.getScripts().size());
        assertEquals("loaded hosts",0,loaded.getHostDefinitions().size());
        assertEquals("loaded roles",0,loaded.getRoles().size());
        assertEquals("loaded state",0,loaded.getState().allKeys().size());
    }
}
