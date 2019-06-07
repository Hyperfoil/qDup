package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.cmd.Cmd;

public interface CmdEncoder<T extends Cmd> {

    public Object encode(T cmd);
}
