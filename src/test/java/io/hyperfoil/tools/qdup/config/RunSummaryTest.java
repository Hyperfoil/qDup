package io.hyperfoil.tools.qdup.config;

import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.SshTestBase;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import org.junit.Test;

import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;

public class RunSummaryTest extends SshTestBase {

    @Test
    public void self_referencing_two_scripts(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("signal",
            """
            scripts:
              foo:
              - script: bar
              biz:
              - sh: dosomething.sh
              bar:
                - script: biz
                - script: biz
                - sh: pwd
                  then:
                  - script:
                      name: foo
                      async: true
            hosts:
              test: TARGET_HOST
            roles:
              role:
                hosts: [test]
                run-scripts:
                - bar
            """.replaceAll("TARGET_HOST",getHost().toString())
        ));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());



        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
//        doit.run();
        State state = config.getState();
    }
}
