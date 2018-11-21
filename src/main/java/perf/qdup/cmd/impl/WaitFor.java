package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;

public class WaitFor extends Cmd {
    private String name;
    private String populatedName;
    public WaitFor(String name){this(name,true);}
    public WaitFor(String name,boolean silent){super(silent); this.name = name;}
    @Override
    public void run(String input, Context context) {
        populatedName = Cmd.populateStateVariables(name,this,context.getState());
        if(populatedName==null || populatedName.isEmpty()){
            context.next(input);
        }else {
            context.getCoordinator().waitFor(populatedName, this, context, input);
        }
    }

    @Override
    public Cmd copy() {
        return new WaitFor(this.name);
    }

    public String getName(){return name;}
    @Override public String toString(){return "wait-for: "+name;}

    @Override
    public String getLogOutput(String output,Context context){
        if(populatedName!=null) {
            return "wait-for: " + populatedName;
        }else{
            return "wait-for: " + name;
        }
    }
}
