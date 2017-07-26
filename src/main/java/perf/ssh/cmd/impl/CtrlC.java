package perf.ssh.cmd.impl;

import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.CommandContext;
import perf.ssh.cmd.CommandResult;

public class CtrlC extends Cmd {
    public CtrlC(){}
    @Override
    protected void run(String input, CommandContext context, CommandResult result) {
        context.getSession().ctrlC();
        result.next(this,input); //now waits for shell to return prompt
    }

    @Override
    protected Cmd clone() {
        return new CtrlC();
    }

    @Override public String toString(){return "^C";}
}
