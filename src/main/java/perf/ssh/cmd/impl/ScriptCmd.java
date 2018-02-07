package perf.ssh.cmd.impl;

import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.Context;
import perf.ssh.cmd.CommandResult;
import perf.ssh.cmd.Script;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ScriptCmd extends Cmd {
    private String name;
    public ScriptCmd(String name){

        this.name = name;
    }


    public String getName(){return name;}
    @Override
    public String toString(){return "invoke "+name;}

    @Override
    protected void run(String input, Context context, CommandResult result) {
        Script toCall = context.getScript(this.name);
        injectThen(toCall.deepCopy(),context);
        for(String key : with.keySet()){
            context.getState().set(key,with.get(key));
        }
        result.next(this,input);
    }

    @Override
    protected Cmd clone() {
        return new ScriptCmd(name).with(with);
    }
}
