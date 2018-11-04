package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;

public class Log extends Cmd {
    String value;
    public Log(String value){
        this.value = value;
        if(this.value==null){
            this.value="";
        }
    }
    @Override
    public void run(String input, Context context) {
        context.getRunLogger().info(Cmd.populateStateVariables(value,this,context.getState()));
        context.next(input);
    }
    @Override
    public Cmd copy() {
        return new Log(value);
    }
    @Override
    public String toString(){return "log: "+this.value;}
}
