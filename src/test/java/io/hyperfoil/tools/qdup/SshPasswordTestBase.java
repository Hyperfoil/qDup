package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.cmd.Result;
import io.hyperfoil.tools.qdup.cmd.Script;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SshPasswordTestBase extends SshTestBase{

    @Override
    public String getIdentity() {
        return getPath("keys/qdup.password").toFile().getPath();
    }

    @BeforeClass
    public static void createContainer() {
        try {
            setup(getPath("keys/qdup.password.pub"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }


    @Test(timeout = 10_000)
    public void passphrase_id_missing_password(){
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
                """.replaceAll("TARGET_HOST",getHost().toString())
        ));
        //builder.setPassphrase("password");
        RunConfig config = builder.buildConfig(parser);
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();
        dispatcher.shutdown();
        State state = config.getState();
        Object found = state.get("foo");
        //we should not be able to find foo because the run should not connect
        assertEquals("we should not be able to find foo because the run should not connect",null,found);
    }
    @Test(timeout = 40_000)
    public void passphrase_id_wrong_password(){
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
                """.replaceAll("TARGET_HOST",getHost().toString())
        ));
        builder.setPassphrase("wrong_password");
        RunConfig config = builder.buildConfig(parser);
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();
        dispatcher.shutdown();
        State state = config.getState();
        Object found = state.get("foo");
        //we should not be able to find foo because the run should not connect
        assertEquals("we should not be able to find foo because the run should not connect",null,found);
    }
    @Test(timeout = 30_000)
    public void passphrase_id_correct_password(){
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
                """.replaceAll("TARGET_HOST",getHost().toString())
        ));
        builder.setPassphrase("password");
        RunConfig config = builder.buildConfig(parser);
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();
        dispatcher.shutdown();
        State state = config.getState();
        Object found = state.get("foo");
        assertNotNull("foo should be set in state because the test ran\n"+state.tree(),found);
        assertEquals("ran",found.toString());
    }


}
