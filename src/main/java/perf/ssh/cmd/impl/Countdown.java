package perf.ssh.cmd.impl;

import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.CommandContext;
import perf.ssh.cmd.CommandResult;

public class Countdown extends Cmd {
    private String name;
    private int startCount;
    public Countdown(String name, int count){

        this.name = name;
        this.startCount = count;

    }
    @Override
    protected void run(String input, CommandContext context, CommandResult result) {
        int newCount = context.getCoordinator().decrease(this.name,this.startCount);
        if(newCount <= 0){
            result.next(this,input);
        }else{
            result.skip(this,input);
        }
    }
    @Override
    protected Cmd clone() { return new Countdown(this.name,this.startCount); }
    @Override
    public String toString(){return "countdown "+this.name+" "+this.startCount;}
}
