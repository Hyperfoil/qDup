package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;
import perf.qdup.cmd.CommandResult;

public class CtrlC extends Cmd {
    public CtrlC(){}
    @Override
    protected void run(String input, Context context, CommandResult result) {
        context.getSession().ctrlC();
        result.next(this,input); //now waits for shell to return prompt
    }

    @Override
    protected Cmd clone() {
        return new CtrlC().with(this.with);
    }

    @Override public String toString(){return "ctrlC:";}
}
