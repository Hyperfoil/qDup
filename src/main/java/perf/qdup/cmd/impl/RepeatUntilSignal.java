package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;
import perf.qdup.cmd.CommandResult;

public class RepeatUntilSignal extends Cmd.LoopCmd {
    private String name;
    public RepeatUntilSignal(String name){
        this.name = name;
    }
    public String getName(){return name;}

    @Override
    public void run(String input, Context context, CommandResult result) {
        String populatedName = Cmd.populateStateVariables(name,this,context.getState());

        if(populatedName==null || populatedName.isEmpty()){
            result.skip(this,input);
        }
        int amount = context.getCoordinator().getSignalCount(populatedName);
        if( amount > 0 ){
            result.next(this,input);
        }else{
            result.skip(this,input);
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
}
