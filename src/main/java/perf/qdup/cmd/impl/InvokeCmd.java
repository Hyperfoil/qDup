package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;
import perf.qdup.cmd.CommandResult;

public class InvokeCmd extends Cmd {
    private Cmd command;
    public InvokeCmd(Cmd command){
        this.command = command.deepCopy();
        //moved here from run to avoid issue where dispatcher has the wrong tail cmd

        injectThen(command,null);//null context so we don't updated tail change
    }
    public Cmd getCommand(){return command;}

    @Override
    protected void run(String input, Context context, CommandResult result) {
        result.next(this,input);
    }

    @Override
    protected Cmd clone() {
        return new InvokeCmd(command.deepCopy()).with(this.with);
    }
    @Override
    public String toString(){return "invoke: "+command.toString();}
}
