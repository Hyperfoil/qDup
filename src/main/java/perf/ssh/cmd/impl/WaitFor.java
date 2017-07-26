package perf.ssh.cmd.impl;

import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.CommandContext;
import perf.ssh.cmd.CommandResult;

public class WaitFor extends Cmd {
    private String name;
    public WaitFor(String name){ this.name = name;}
    @Override
    protected void run(String input, CommandContext context, CommandResult result) {
        context.getCoordinator().waitFor(name,this,result,input);
    }

    @Override
    protected Cmd clone() {
        return new WaitFor(this.name);
    }

    public String getName(){return name;}
    @Override public String toString(){return "waitFor "+name;}
}
