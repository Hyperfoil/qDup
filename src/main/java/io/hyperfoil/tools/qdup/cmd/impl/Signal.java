package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;

public class Signal extends Cmd {
    private String name;
    private String populatedName;
    public Signal(String name){ this.name = name;}
    public String getName(){return name;}
    @Override
    public void run(String input, Context context) {
        try {
            populatedName = Cmd.populateStateVariables(name, this, context.getState());
            context.getCoordinator().signal(populatedName);

            context.next(input);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public Cmd copy() {
        return new Signal(this.name);
    }

    @Override
    public String toString(){
        return "signal: "+name;
    }

    @Override
    public String getLogOutput(String output,Context context){
        String useName = populatedName!=null ? populatedName : name;
        return "signal: "+useName;
    }
}
