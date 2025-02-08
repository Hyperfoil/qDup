package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.SshTestBase;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SleepTest extends SshTestBase {

    @Test
    public void parseMs(){

        assertEquals("1_000",1000,Sleep.parseToMs("1_000"));
        assertEquals("1ms",1,Sleep.parseToMs("1ms"));
        assertEquals("5s",5000,Sleep.parseToMs("5s"));
        assertEquals("2m",120000,Sleep.parseToMs("2m"));
        assertEquals("1h",60*60*1000,Sleep.parseToMs("1h"));

        assertEquals("1h 2m3s",(60*60*1000 + 120_000 + 3000),Sleep.parseToMs("1h 2m3s"));
        assertEquals("1ms2m3s",(1+120_000+3_000),Sleep.parseToMs("1ms2m3s"));

    }

    @Test
    public void sleep_call_next(){
        String sleep = "10s";
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("",
           """
           scripts:
             foo:
             - sleep: SLEEP_DURATION
             - set-state: RUN.FOO worked
           hosts:
             local: TARGET_HOST
           roles:
             doit:
               hosts: [local]
               run-scripts: [foo]
           states:
             alpha: [ {name: "ant"}, {name: "apple"} ]
             bravo: [ {name: "bear"}, {name: "bull"} ]
             charlie: {name: "cat"}
           """.replaceAll("TARGET_HOST",getHost().toString())
                   .replaceAll("SLEEP_DURATION",sleep)
        ));

        RunConfig config = builder.buildConfig(parser);
        Dispatcher dispatcher = new Dispatcher();

        List<String> signals = new ArrayList<>();

        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        long start = System.currentTimeMillis();
        doit.run();
        long stop = System.currentTimeMillis();
        dispatcher.shutdown();

        assertEquals("FOO should be set","worked",doit.getConfig().getState().get("FOO"));
        assertTrue((stop-start)+" runtime should be >= "+sleep,Sleep.parseToMs(sleep) <= (stop-start));
    }
}
