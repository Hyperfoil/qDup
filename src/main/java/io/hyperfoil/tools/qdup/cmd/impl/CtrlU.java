package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;

//SIGKILL
public class CtrlU extends CtrlSignal {
    public CtrlU(){super("ctrlU",'U');}

    @Override
    public Cmd copy() {
        return new CtrlU();
    }
}
