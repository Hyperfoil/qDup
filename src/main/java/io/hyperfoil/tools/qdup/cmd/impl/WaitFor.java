package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;

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
        String toUse = populatedName!=null ? populatedName : name;
        if(toUse.isEmpty()){
            return "";
        }else {
            return "wait-for: " + toUse;
        }
    }
}
