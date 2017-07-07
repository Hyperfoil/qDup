package perf.ssh.cmd;

import perf.ssh.State;

/**
 * Created by wreicher
 */
public interface Code {
    Result run(String input, State state);
}
