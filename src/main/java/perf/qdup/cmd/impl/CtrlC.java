package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;
import perf.qdup.cmd.CommandResult;

public class CtrlC extends Cmd {
    public CtrlC(){}
    @Override
    public void run(String input, Context context, CommandResult result) {
        context.getSession().ctrlC();
        result.next(this,input); //now waits for shell to return prompt
    }

    @Override
    public Cmd copy() {
        return new CtrlC();
    }

    @Override public String toString(){return "ctrlC:";}
}
