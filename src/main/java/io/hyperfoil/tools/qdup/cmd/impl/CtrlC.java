package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;

public class CtrlC extends Cmd {
    public CtrlC(){}
    @Override
    public void run(String input, Context context) {
        context.getSession().ctrlC();
        context.next(input); //now waits for shell to return prompt
    }

    @Override
    public Cmd copy() {
        return new CtrlC();
    }

    @Override
    public String toString(){return "ctrlC";}
}
