package perf.ssh.cmd.impl;

import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.Context;
import perf.ssh.cmd.CommandResult;

public class WaitFor extends Cmd {
    private String name;
    public WaitFor(String name){ this.name = name;}
    @Override
    protected void run(String input, Context context, CommandResult result) {
        String populatedName = Cmd.populateStateVariables(name,context.getState());
        context.getCoordinator().waitFor(populatedName,this,result,input);
    }

    @Override
    protected Cmd clone() {
        return new WaitFor(this.name);
    }

    public String getName(){return name;}
    @Override public String toString(){return "waitFor "+name;}
}
