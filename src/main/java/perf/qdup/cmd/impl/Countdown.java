package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;
import perf.qdup.cmd.CommandResult;

public class Countdown extends Cmd {
    private String name;
    private int startCount;
    public Countdown(String name, int count){

        this.name = name;
        this.startCount = count;

    }
    @Override
    public void run(String input, Context context, CommandResult result) {
        int newCount = context.getCoordinator().decrease(this.name,this.startCount);
        if(newCount <= 0){
            result.next(this,input);
        }else{
            result.skip(this,input);
        }
    }
    @Override
    protected Cmd clone() { return new Countdown(this.name,this.startCount).with(this.with); }
    @Override
    public String toString(){return "countdown: "+this.name+" "+this.startCount;}
}
