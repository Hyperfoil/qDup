package perf.ssh.cmd.impl;

import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.Context;
import perf.ssh.cmd.CommandResult;

public class Sh extends Cmd {
    private String command;
    public Sh(String command){
        this.command = command;
    }
    @Override
    protected void run(String input, Context context, CommandResult result) {

        context.getSession().setCommand(this,result);
        context.getSession().sh(populateStateVariables(command,context.getState()));

    }

    @Override
    protected Cmd clone() {
        return new Sh(this.command);
    }

    @Override public String toString(){return "sh "+command;}
}
