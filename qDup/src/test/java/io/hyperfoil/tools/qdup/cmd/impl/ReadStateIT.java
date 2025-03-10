package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.SshTestBase;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import org.junit.Test;

import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class ReadStateIT extends SshTestBase {

    @Test()
    public void run_read_boolean_for_regex(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("signal",
            """
            scripts:
              sig:
              - read-state: ${{VARIABLE}}
              - regex: true
                then:
                - set-state: RUN.FOUND true
                else:
                - set-state: RUN.FOUND false
            hosts:
              test: TEST_HOST
            roles:
              role:
                hosts: [test]
                run-scripts:
                - sig
            """.replaceAll("TEST_HOST",getHost().toString())
        ));
        builder.getState().set("VARIABLE",true);
        RunConfig config = builder.buildConfig(parser);

        assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());

        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();

        State state = config.getState();
        assertTrue(state.has("FOUND"));
        assertEquals("true",state.get("FOUND"));

    }
}
