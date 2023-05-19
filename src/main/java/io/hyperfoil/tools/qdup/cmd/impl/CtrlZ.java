package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;

//SIGSUSPEND
public class CtrlZ extends CtrlSignal {
    public CtrlZ(){super("ctrlZ",'Z');}

    @Override
    public Cmd copy() {
        return new CtrlZ();
    }
}