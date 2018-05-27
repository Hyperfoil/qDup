package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;
import perf.qdup.cmd.CommandResult;

public class Abort extends Cmd {
    private String message;
    public Abort(String message){
        this.message = message;
    }

    @Override
    protected void run(String input, Context context, CommandResult result) {
        String populatedMessage = Cmd.populateStateVariables(message,this,context.getState());
        context.getRunLogger().info("abort {}",populatedMessage);
        context.abort();
        //TODO I don't think it actually has to call result.next
        result.next(this,input);//abort no longer ends the run but the stage so it has to return
    }

    public String getMessage(){return message;}

    @Override
    protected Cmd clone() {
        return new Abort(this.message).with(this.with);
    }

    @Override
    public String toString(){return "abort: "+this.message;}
}
