package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;

public class WaitFor extends Cmd {
    private String name;
    private String populatedName;
    private String initial;
    private String populatedInitial;
    public WaitFor(String name){this(name,null);}
    public WaitFor(String name,String initial){
        super(true);
        this.name = name;
        this.initial = initial;
    }
    @Override
    public void run(String input, Context context) {
        populatedName = Cmd.populateStateVariables(name,this,context.getState());
        if(populatedName==null || populatedName.isEmpty()){
            context.next(input);
        }else {
            if(hasInitial()){
                populatedInitial = Cmd.populateStateVariables(initial,this,context.getState());

                try {
                    int intialLatches = Integer.parseInt(populatedInitial);
                    context.getCoordinator().setSignal(populatedName,intialLatches);
                }catch(NumberFormatException e){
                    logger.error("wait-for: {} could not setSignal {} due to NumberFormatException",populatedName,populatedInitial);
                }

            }

            context.getCoordinator().waitFor(populatedName, this, context, input);
        }
    }

    @Override
    public Cmd copy() {
        return new WaitFor(this.name);
    }

    public boolean hasInitial(){return initial!=null && !initial.isEmpty();}
    public String getInitial(){return initial;}
    public String getName(){return name;}
    @Override public String toString(){return "wait-for: "+name+(hasInitial()?" "+getInitial():"");}

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
