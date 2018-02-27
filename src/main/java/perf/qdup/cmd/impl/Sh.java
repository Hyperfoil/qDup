package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;
import perf.qdup.cmd.CommandResult;

public class Sh extends Cmd {
    private String command;
    public Sh(String command){
        this(command,false);
    }
    public Sh(String command,boolean silent){
        super(silent);
        this.command = command;
    }

    public String getCommand(){return command;}

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
