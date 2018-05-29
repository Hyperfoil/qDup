package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;
import perf.qdup.cmd.CommandResult;

public class Log extends Cmd {
    String value;
    public Log(String value){
        this.value = value;
        if(this.value==null){
            this.value="";
        }
    }
    @Override
    public void run(String input, Context context, CommandResult result) {
        context.getRunLogger().info(Cmd.populateStateVariables(value,this,context.getState()));
        result.next(this,input);
    }
    @Override
    protected Cmd clone() {
        return new Log(value).with(this.with);
    }
    @Override
    public String toString(){return "log: "+this.value;}
}
