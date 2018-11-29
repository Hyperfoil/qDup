package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;

public class Signal extends Cmd {
    private String name;
    private String populatedName;
    public Signal(String name){ this.name = name;}
    public String getName(){return name;}
    @Override
    public void run(String input, Context context) {
        populatedName = Cmd.populateStateVariables(name,this,context.getState());
        context.getCoordinator().signal(populatedName);
        
        context.next(input);
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
