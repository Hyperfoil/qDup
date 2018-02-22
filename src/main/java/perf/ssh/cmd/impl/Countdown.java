package perf.ssh.cmd.impl;

import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.Context;
import perf.ssh.cmd.CommandResult;

public class Countdown extends Cmd {
    private String name;
    private int startCount;
    public Countdown(String name, int count){

        this.name = name;
        this.startCount = count;

    }
    @Override
    protected void run(String input, Context context, CommandResult result) {
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
