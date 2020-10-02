package io.hyperfoil.tools.qdup.config.rule;

import io.hyperfoil.tools.qdup.Stage;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.impl.Sh;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.RunRule;
import io.hyperfoil.tools.qdup.config.RunSummary;

/*
 Make sure sh is not called in a watch, on-signal, or timer
 */
public class NonObservingCommands implements RunRule {
    @Override
    public void scan(String role, Stage stage, String script, String host, Cmd command, boolean isWatching, Cmd.Ref ref, RunConfigBuilder config, RunSummary summary) {
        if(isWatching){
            if(command instanceof Sh){
                summary.addError(
                    role,
                    stage,
                    script,
                    command.toString(),
                    "cannot use sh when observing another command"
                );
            }
        }
    }

    @Override
    public void close(RunConfigBuilder config, RunSummary summary) {}
}
