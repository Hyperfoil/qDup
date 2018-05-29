package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;
import perf.qdup.cmd.CommandResult;

public class WaitFor extends Cmd {
    private String name;
    public WaitFor(String name){this(name,true);}
    public WaitFor(String name,boolean silent){super(silent); this.name = name;}
    @Override
    public void run(String input, Context context, CommandResult result) {
        String populatedName = Cmd.populateStateVariables(name,this,context.getState());
        if(populatedName==null || populatedName.isEmpty()){
            result.next(this,input);
        }else {
            context.getCoordinator().waitFor(populatedName, this, result, input);
        }
    }

    @Override
    public Cmd copy() {
        return new WaitFor(this.name);
    }

    public String getName(){return name;}
    @Override public String toString(){return "wait-for: "+name;}
}
