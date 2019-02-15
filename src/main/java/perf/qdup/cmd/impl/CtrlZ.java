package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;

public class CtrlZ extends Cmd {
    public CtrlZ(){}
    @Override
    public void run(String input, Context context) {
        context.getSession().ctrlZ();
        context.next(input); //now waits for shell to return prompt
    }

    @Override
    public Cmd copy() {
        return new CtrlZ();
    }

    @Override public String toString(){return "ctrlC:";}
}
