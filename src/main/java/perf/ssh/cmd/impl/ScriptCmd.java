package perf.ssh.cmd.impl;

import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.CommandContext;
import perf.ssh.cmd.CommandResult;
import perf.ssh.cmd.Script;

public class ScriptCmd extends Cmd {
    private String name;
    public ScriptCmd(String name){

        this.name = name;
    }
    public String getName(){return name;}
    @Override
    public String toString(){return "invoke "+name;}

    @Override
    protected void run(String input, CommandContext context, CommandResult result) {
        Script toCall = context.getScript(this.name);
        injectThen(toCall.deepCopy(),context);
        result.next(this,input);
    }

    @Override
    protected Cmd clone() {
        return new ScriptCmd(name);
    }
}
