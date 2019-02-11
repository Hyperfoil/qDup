package perf.qdup.config.yaml;

import perf.qdup.cmd.Cmd;

public interface CmdEncoder<T extends Cmd> {

    public Object encode(T cmd);
}
