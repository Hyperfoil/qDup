package perf.ssh.cmd.impl;

import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.CommandContext;
import perf.ssh.cmd.CommandResult;

public class RepeatUntilSignal extends Cmd {
    private String name;
    public RepeatUntilSignal(String name){
        this.name = name;
    }
    public String getName(){return name;}

    @Override
    protected void run(String input, CommandContext context, CommandResult result) {
        int amount = context.getCoordinator().getSignalCount(name);
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
        return new RepeatUntilSignal(this.name);
    }
    @Override
    public String toString(){return "repeat-until "+name;}
}
