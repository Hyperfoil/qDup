package io.hyperfoil.tools.qdup.cmd.impl;


import com.oracle.truffle.api.library.GenerateLibrary;
import io.hyperfoil.tools.qdup.*;
import io.hyperfoil.tools.qdup.cmd.*;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.Parser;

import io.hyperfoil.tools.qdup.shell.AbstractShell;
import io.hyperfoil.tools.qdup.shell.SshShell;
import io.hyperfoil.tools.qdup.stream.SuffixStream;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import org.junit.Test;

public class ShTest extends SshTestBase {

    @Test
    public void testExecutorDelay() throws InterruptedException {

        AbstractShell shell = getSession();

        Sh command = new Sh("echo Hello World");

        RunConfigBuilder builder = getBuilder();
        RunConfig runConfig = builder.buildConfig(Parser.getInstance());
        Run run = new Run(
                "/tmp/",
                runConfig,
                new Dispatcher()
        );

        CountDownLatch latch = new CountDownLatch(1);

        ScriptContext context = new ScriptContext(
                shell,
                new State(""),
                run,
                new Profiles().get("ScriptContextTest"),
                command,
                false
        )
        {
            @Override
            public void next(String output) {
                try {

                    super.next(output);
                } finally {

                    latch.countDown();
                }
            }
        };

        command.run("", context);

        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertTrue("Command should complete within 2 seconds", completed);

        int delay = shell.getDelay();

        assertTrue("Delay should be less than or equal to DEFAULT_DELAY",
                delay <= SuffixStream.DEFAULT_DELAY);
        assertTrue("Delay should be positive",
                delay > 0);

        }
    }
