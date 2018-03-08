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
    public String toString(){return "invoke: "+name;}

    @Override
    protected void run(String input, Context context, CommandResult result) {
        Script toCall = context.getScript(this.name,this);
        injectThen(toCall.deepCopy(),context);
        //don't push this.WITH into context because it will be found by Cmd.populate...
//        for(String key : with.keySet()){
//            context.getState().set(key,with.get(key));
//        }


        result.next(this,input);
    }

    @Override
    protected Cmd clone() {
        return new ScriptCmd(name).with(with);
    }
}
