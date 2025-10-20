package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import io.hyperfoil.tools.qdup.shell.SshShellTest;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ZshShellTest extends SshTestBase {

    @BeforeClass
    public static void createContainer() {
        try {
            setup(getPath("keys/qdup.pub"),"ZshDockerfile");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }



    @Test
    public void test_zsh_shell(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("",
                """
                        scripts:
                          echo:
                          - sh: echo "ran"
                          - set-state: RUN.foo
                        hosts:
                          local: TARGET_HOST
                        roles:
                          doit:
                            hosts: [local]
                            run-scripts: [echo]
                        """.replaceAll("TARGET_HOST", getHost().toString())
        ));
        RunConfig config = builder.buildConfig(parser);
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.ensureConsoleLogging();
        doit.run();
        dispatcher.shutdown();
        State state = config.getState();
        Object found = state.get("foo");
        assertNotNull("foo should be set in state because the test ran\n" + state.tree(), found);
        assertEquals("ran", found.toString());
    }
}
