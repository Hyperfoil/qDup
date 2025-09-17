package io.hyperfoil.tools.qdup.config.rule;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.impl.Sh;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.RunRule;
import io.hyperfoil.tools.qdup.config.RunSummary;
import io.hyperfoil.tools.yaup.AsciiArt;

/*
 Make sure sh is not called in a watch, on-signal, or timer
 */
public class NonObservingCommands implements RunRule {
    @Override
    public void scan(CmdLocation location, Cmd command, Cmd.Ref ref, RunConfigBuilder config, RunSummary summary) {
        if(location.getPosition().isWatching()){
            if(command instanceof Sh){
                summary.addError(
                    location.getRoleName(),
                    location.getStage(),
                    location.getScriptName(),
                    command.toString(),
                    "cannot use sh when observing another command"
                );
            }
        }
    }

    @Override
    public void close(RunConfigBuilder config, RunSummary summary) {}
}
