package perf.ssh.cmd.impl;

import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.CommandContext;
import perf.ssh.cmd.CommandResult;

public class InvokeCmd extends Cmd {
    private Cmd command;
    public InvokeCmd(Cmd command){
        this.command = command.deepCopy();
        //moved here from run to avoid issue where dispatcher has the wrong tail cmd

        injectThen(command,null);//null context so we don't updated tail change
    }
    public Cmd getCommand(){return command;}

    @Override
    protected void run(String input, CommandContext context, CommandResult result) {
        result.next(this,input);
    }

    @Override
    protected Cmd clone() {
        return new InvokeCmd(command.deepCopy());
    }
    @Override
    public String toString(){return "invoke "+command.toString();}
}
