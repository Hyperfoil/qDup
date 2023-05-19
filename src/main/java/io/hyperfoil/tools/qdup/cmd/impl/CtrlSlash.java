package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;

//SIGQUIT
public class CtrlSlash extends CtrlSignal {
    public CtrlSlash(){super("ctrl/",'/');}

    @Override
    public Cmd copy() {
        return new CtrlSlash();
    }
}