package perf.ssh.cmd.impl;

import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.CommandContext;
import perf.ssh.cmd.CommandResult;

public class Abort extends Cmd {
    private String message;
    public Abort(String message){
        this.message = message;
    }

    @Override
    protected void run(String input, CommandContext context, CommandResult result) {
        String populatedMessage = Cmd.populateStateVariables(message,context.getState());
        context.getRunLogger().info("abort {}",populatedMessage);
        context.abort();
    }

    public String getMessage(){return message;}

    @Override
    protected Cmd clone() {
        return new Abort(this.message);
    }

    @Override
    public String toString(){return "abort "+this.message;}
}
