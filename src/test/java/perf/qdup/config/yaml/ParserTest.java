package perf.qdup.config.yaml;

import org.junit.Test;
import perf.qdup.SshTestBase;
import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Script;
import perf.qdup.cmd.impl.CtrlC;
import perf.qdup.cmd.impl.Echo;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class ParserTest extends SshTestBase {

    public static String join(String...args){
        return Arrays.asList(args).stream().collect(Collectors.joining("\n"));
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
            "       - timer: 5s # No idea why this is getting stuck at times",
            "          - ctrlC"
       ));
        assertNotNull(loaded);
        assertTrue(loaded.getScripts().containsKey("perf-record"));
        Script script = loaded.getScripts().get("perf-record");
        assertTrue(script.getTail().hasTimers());
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
        assertEquals("loaded hosts",0,loaded.getHosts().size());
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
           "    - set-state: PWD"
        ));

        assertNotNull("loaded should not be null",loaded);
        assertEquals("loaded scripts",1,loaded.getScripts().size());
        assertEquals("loaded hosts",0,loaded.getHosts().size());
        assertEquals("loaded roles",0,loaded.getRoles().size());
        assertEquals("loaded state",0,loaded.getState().allKeys().size());
    }
}
