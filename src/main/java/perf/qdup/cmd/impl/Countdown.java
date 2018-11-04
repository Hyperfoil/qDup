package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;

public class Countdown extends Cmd {
    private String name;
    private int startCount;
    public Countdown(String name, int count){

        this.name = name;
        this.startCount = count;

    }
    @Override
    public void run(String input, Context context) {
        int newCount = context.getCoordinator().decrease(this.name,this.startCount);
        if(newCount <= 0){
            context.next(input);
        }else{
            context.skip(input);
        }
    }
    @Override
    public Cmd copy() { return new Countdown(this.name,this.startCount); }
    @Override
    public String toString(){return "countdown: "+this.name+" "+this.startCount;}
}
