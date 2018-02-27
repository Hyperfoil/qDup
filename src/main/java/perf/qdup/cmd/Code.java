package perf.qdup.cmd;

import perf.qdup.State;

/**
 * Created by wreicher
 */
public interface Code {
    Result run(String input, State state);
}
