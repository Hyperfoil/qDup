package perf.ssh.cmd.impl;

import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.CommandContext;
import perf.ssh.cmd.CommandResult;
import perf.ssh.cmd.Script;

import java.util.HashMap;
import java.util.Map;

public class ScriptCmd extends Cmd {
    private String name;
    private Map<String,String> with;
    public ScriptCmd(String name){

        this.name = name;
        this.with = new HashMap<>();
    }

    private ScriptCmd with(Map<String,String> with){
        this.with.putAll(with);
        return this;
    }
    public ScriptCmd with(String key,String value){
        with.put(key,value);
        return this;
    }

    public String getName(){return name;}
    @Override
    public String toString(){return "invoke "+name;}

    @Override
    protected void run(String input, CommandContext context, CommandResult result) {
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
