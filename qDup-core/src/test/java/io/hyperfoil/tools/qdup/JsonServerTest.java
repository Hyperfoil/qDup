package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import io.vertx.core.Vertx;
import org.junit.Test;

import java.io.*;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JsonServerTest extends SshTestBase {

    @Test(timeout = 30_000)
    public void active() {
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("signal",
                """
                        scripts:
                          start-script:
                            - log: "Running script"
                            - sh: echo "Hello World!"
                            - log: "Finished script"
                        hosts:
                          target-host: HOST
                        roles:
                          db:
                            hosts:
                              - target-host
                            setup-scripts:
                              - start-script
                        states:
                          HOST: LOCAL
                        """.replaceAll("HOST", getHost().toString())
        ));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());

        Dispatcher dispatcher = new Dispatcher();
        Run run = new Run(tmpDir.toString(), config, dispatcher);
        JsonServer server = new JsonServer(Vertx.vertx(),run, 31337);
        server.start();
        run.run();
        String active = readUrl("http://localhost:31337/active");
        assertTrue(active.startsWith("["));
        assertTrue(active.endsWith("]"));
        server.stop();
    }
}
