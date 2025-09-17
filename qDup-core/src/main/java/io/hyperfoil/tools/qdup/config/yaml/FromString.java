package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.cmd.Cmd;

public interface FromString<T extends Cmd> {
    T apply(String input,String prefix,String suffix);
}
