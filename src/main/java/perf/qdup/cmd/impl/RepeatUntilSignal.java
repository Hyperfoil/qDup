package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;

public class RepeatUntilSignal extends Cmd.LoopCmd {
    private String name;
    private String populatedName;
    private int amount=-1;
    public RepeatUntilSignal(String name){
        this.name = name;
    }
    public String getName(){return name;}

    @Override
    public void run(String input, Context context) {
        populatedName = Cmd.populateStateVariables(name,this,context.getState());

        if(populatedName==null || populatedName.isEmpty()){
            context.skip(input);
        }
        amount = context.getCoordinator().getSignalCount(populatedName);
        if( amount > 0 ){
            context.next(input);
        }else{
            context.skip(input);
        }
    }

    @Override
    protected void setSkip(Cmd skip){
        //prevent propegating skip to last then because it needs to skip to this
        this.forceSkip(skip);
    }

    @Override
    public Cmd copy() {
        return new RepeatUntilSignal(this.name);
    }
    @Override
    public String toString(){return "repeat-until: "+name;}

    @Override
    public String getLogOutput(String output,Context context){
        String toUse = populatedName!=null ? populatedName : name;
        if(amount > 0 ){
            return "repeat-until: "+toUse;
        }else{
            return "";
        }
    }
}
