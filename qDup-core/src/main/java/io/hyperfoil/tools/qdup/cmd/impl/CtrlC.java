package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;

//SIGINT
public class CtrlC extends CtrlSignal {
    public CtrlC(){super("ctrlC",'C');}

    @Override
    public Cmd copy() {
        return new CtrlC();
    }
}