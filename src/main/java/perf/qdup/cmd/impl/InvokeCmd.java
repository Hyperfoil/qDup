package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;

public class InvokeCmd extends Cmd {
    private Cmd command;
    public InvokeCmd(Cmd command){
        this.command = command.deepCopy();
    }
    public Cmd getCommand(){return command;}

    @Override
    public void run(String input, Context context) {
        //move  to constructor to avoid issue where dispatcher has the wrong tail cmd?
        injectThen(this.command,null);//null context so we don't updated tail change
        context.next(input);
    }

    @Override
    public Cmd copy() {
        return new InvokeCmd(command.deepCopy());
    }
    @Override
    public String toString(){return "invoke: "+command.toString();}
}
