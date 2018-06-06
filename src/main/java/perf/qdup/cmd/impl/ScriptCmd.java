package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;
import perf.qdup.cmd.CommandResult;
import perf.qdup.cmd.Script;

public class ScriptCmd extends Cmd {
    private String name;
    public ScriptCmd(String name){

        this.name = name;
    }

    public String getName(){return name;}
    @Override
    public String toString(){return "script: "+name;}

    @Override
    public void run(String input, Context context, CommandResult result) {
        Script toCall = context.getScript(this.name,this);
        if(toCall == null){
            logger.warn("could not find script: {}",this.name);
        }else {
            Cmd copyCmd = toCall.deepCopy();
            injectThen(copyCmd, context);
        }
        result.next(this,input);
    }

    @Override
    public Cmd copy() {
        return new ScriptCmd(name);
    }

}
