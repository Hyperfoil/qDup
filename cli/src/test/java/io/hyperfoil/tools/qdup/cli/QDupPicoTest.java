package io.hyperfoil.tools.qdup.cli;

import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusMainTest
public class QDupPicoTest {

    /**
     * No arg execution should be an error that displays the usage hint
     * @param launcher
     */
    @Test
    public void no_arg_help(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch();
        assertNotEquals(0,result.exitCode());
        assertTrue(result.getErrorOutput().contains("Usage:"),result.getErrorOutput());
    }
}
