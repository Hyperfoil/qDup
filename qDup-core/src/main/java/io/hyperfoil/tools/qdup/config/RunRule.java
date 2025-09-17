package io.hyperfoil.tools.qdup.config;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.config.rule.CmdLocation;

public interface RunRule {

    void scan(CmdLocation location, Cmd command, Cmd.Ref ref, RunConfigBuilder config, RunSummary summary);

    void close(RunConfigBuilder config,RunSummary summary);
}
