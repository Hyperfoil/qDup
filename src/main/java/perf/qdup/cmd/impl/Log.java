package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;

public class Log extends Cmd {
    String message;
    public Log(String message){
        super(true);
        this.message = message;
        if(this.message ==null){
            this.message ="";
        }
    }

    public String getMessage(){return message;}

    @Override
    public void run(String input, Context context) {
        context.getRunLogger().info(Cmd.populateStateVariables(message,this,context.getState()));
        context.next(input);
    }
    @Override
    public Cmd copy() {
        return new Log(message);
    }
    @Override
    public String toString(){return "log: "+this.message;}
}
