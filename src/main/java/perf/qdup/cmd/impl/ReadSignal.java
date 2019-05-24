package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;

public class ReadSignal extends Cmd {

    private String name;
    private String populatedName;
    public ReadSignal(String name){
        this.name = name;
    }

    public String getName(){return name;}

    @Override
    public String toString(){
        return "read-signal: "+ name;
    }

    @Override
    public void run(String input, Context context) {
        //use getStateValue in case it is in WITH or contest
        Object value = Cmd.getStateValue(name, this, context.getState(), null);
        populatedName = value == null ? "" : value.toString();
        if(populatedName == null || populatedName.isEmpty()){
            context.skip(input);
        }else{
            if(context.getCoordinator().getSignalCount(populatedName) <= 0) {
                context.next(input);
            }else{
                context.skip(input);
            }
        }
    }

    @Override
    public Cmd copy() {
        return new ReadSignal(name);
    }

    @Override
    public String getLogOutput(String output,Context context){
        return "read-signal: "+ populatedName == null ? name : populatedName;
    }
}
