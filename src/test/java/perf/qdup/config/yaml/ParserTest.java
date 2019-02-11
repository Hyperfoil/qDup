package perf.qdup.config.yaml;

import org.junit.Test;
import perf.qdup.SshTestBase;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class ParserTest extends SshTestBase {

    public static String join(String...args){
        return Arrays.asList(args).stream().collect(Collectors.joining("\n"));
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
        System.out.println(loaded);
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
