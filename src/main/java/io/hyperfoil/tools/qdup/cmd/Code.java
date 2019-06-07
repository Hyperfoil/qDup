package io.hyperfoil.tools.qdup.cmd;

import io.hyperfoil.tools.qdup.State;

/**
 * Created by wreicher
 */
public interface Code {
    Result run(String input, State state);
}
