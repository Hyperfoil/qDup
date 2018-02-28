package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;
import perf.qdup.cmd.CommandResult;

public class WaitFor extends Cmd {
    private String name;
    public WaitFor(String name){this(name,false);}
    public WaitFor(String name,boolean silent){super(silent); this.name = name;}
    @Override
    protected void run(String input, Context context, CommandResult result) {
        String populatedName = Cmd.populateStateVariables(name,this,context.getState());
        if(populatedName==null || populatedName.isEmpty()){
            result.next(this,input);
        }else {
            context.getCoordinator().waitFor(populatedName, this, result, input);
        }
    }

    @Override
    protected Cmd clone() {
        return new WaitFor(this.name).with(this.with);
    }

    public String getName(){return name;}
    @Override public String toString(){return "wait-for: "+name;}
}
