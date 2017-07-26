package perf.ssh.cmd.impl;

import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.CommandContext;
import perf.ssh.cmd.CommandResult;

public class Log extends Cmd {
    String value;
    public Log(String value){ this.value = value; }
    @Override
    protected void run(String input, CommandContext context, CommandResult result) {
        context.getRunLogger().info(Cmd.populateStateVariables(value,context.getState()));
        result.next(this,input);
    }
    @Override
    protected Cmd clone() {
        return new Log(value);
    }
    @Override
    public String toString(){return "log "+this.value;}
}
