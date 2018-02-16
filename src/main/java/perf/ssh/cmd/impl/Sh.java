package perf.ssh.cmd.impl;

import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.Context;
import perf.ssh.cmd.CommandResult;

public class Sh extends Cmd {
    private String command;
    public Sh(String command){
        this(command,false);
    }
    public Sh(String command,boolean silent){
        super(silent);
        this.command = command;
    }

    @Override
    protected void run(String input, Context context, CommandResult result) {
        String commandString = populateStateVariables(command,this,context.getState());
        context.getSession().sh(commandString,this,result);

    }

    @Override
    protected Cmd clone() {
        return new Sh(this.command).with(this.with);
    }

    @Override public String toString(){return "sh: "+command;}
}
