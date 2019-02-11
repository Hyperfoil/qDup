package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;

public class Countdown extends Cmd {
    private String name;
    private int initial;
    public Countdown(String name, int count){

        this.name = name;
        this.initial = count;

    }

    public String getName(){return name;}
    public int getInitial(){return initial;}

    @Override
    public void run(String input, Context context) {
        int newCount = context.getCoordinator().decrease(this.name,this.initial);
        if(newCount <= 0){
            context.next(input);
        }else{
            context.skip(input);
        }
    }
    @Override
    public Cmd copy() { return new Countdown(this.name,this.initial); }
    @Override
    public String toString(){return "countdown: "+this.name+" "+this.initial;}
}
