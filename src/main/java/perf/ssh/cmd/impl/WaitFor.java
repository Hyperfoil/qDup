package perf.ssh.cmd.impl;

import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.Context;
import perf.ssh.cmd.CommandResult;

public class WaitFor extends Cmd {
    private String name;
    public WaitFor(String name){this(name,false);}
    public WaitFor(String name,boolean silent){super(silent); this.name = name;}
    @Override
    protected void run(String input, Context context, CommandResult result) {
        String populatedName = Cmd.populateStateVariables(name,this,context.getState());
        context.getCoordinator().waitFor(populatedName,this,result,input);
    }

    @Override
    protected Cmd clone() {
        return new WaitFor(this.name).with(this.with);
    }

    public String getName(){return name;}
    @Override public String toString(){return "wait-for: "+name;}
}
