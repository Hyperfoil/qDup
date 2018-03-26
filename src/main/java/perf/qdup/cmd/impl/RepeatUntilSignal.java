package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;
import perf.qdup.cmd.CommandResult;

public class RepeatUntilSignal extends Cmd {
    private String name;
    public RepeatUntilSignal(String name){
        this.name = name;
    }
    public String getName(){return name;}

    @Override
    protected void run(String input, Context context, CommandResult result) {
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
    public Cmd then(Cmd command){
        Cmd currentTail = this.getTail();
        Cmd rtrn = super.then(command);
        currentTail.forceNext(command);
        command.forceNext(this);
        return rtrn;
    }

    @Override
    protected Cmd clone() {
        return new RepeatUntilSignal(this.name).with(this.with);
    }
    @Override
    public String toString(){return "repeat-until: "+name;}
}
