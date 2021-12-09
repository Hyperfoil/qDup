package io.hyperfoil.tools.qdup.config;

import io.hyperfoil.tools.qdup.Stage;
import io.hyperfoil.tools.qdup.cmd.Cmd;

public interface RunRule {

    public static enum Location {Watcher(true),
        OnTimer(true),
        OnSignal(true),
        Normal(false);

        private boolean isWatching;
        Location(boolean isWatching){
            this.isWatching = isWatching;
        }
        public boolean isWatching(){return isWatching;}
    }

    void scan(String role, Stage stage, String script, String host, Cmd command, Location location, Cmd.Ref ref, RunConfigBuilder config, RunSummary summary);

    void close(RunConfigBuilder config,RunSummary summary);
}
