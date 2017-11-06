package perf.ssh.cmd.impl;

import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.Context;
import perf.ssh.cmd.CommandResult;

public class Signal extends Cmd {
    private String name;
    public Signal(String name){ this.name = name;}
    public String getName(){return name;}
    @Override
    protected void run(String input, Context context, CommandResult result) {
        String populatedName = Cmd.populateStateVariables(name,context.getState());
        context.getCoordinator().signal(populatedName);
        result.next(this,input);
    }

    @Override
    protected Cmd clone() {
        return new Signal(this.name);
    }

    @Override public String toString(){return "signal "+name;}
}
