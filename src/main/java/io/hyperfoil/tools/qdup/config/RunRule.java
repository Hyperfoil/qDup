package io.hyperfoil.tools.qdup.config;

import io.hyperfoil.tools.qdup.Stage;
import io.hyperfoil.tools.qdup.cmd.Cmd;

public interface RunRule {
    void scan(String role, Stage stage, String script,String host, Cmd command, boolean isWatching,  Cmd.Ref ref, RunConfigBuilder config,RunSummary summary);

    void close(RunConfigBuilder config,RunSummary summary);
}
