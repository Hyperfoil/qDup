package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;
import perf.qdup.cmd.Script;

public class ScriptCmd extends Cmd {
    private final String name;
    private final boolean async;
    private String populatedName = null;
    public ScriptCmd(String name){
        this(name,false);
    }
    public ScriptCmd(String name,boolean async){
        this.name = name;
        this.async = async;
    }

    public boolean isAsync(){return async;}
    public String getName(){return name;}
    @Override
    public String toString(){return "script: "+name;}

    @Override
    public void run(String input, Context context) {
        populatedName = populateStateVariables(name,this,context.getState());
        Script toCall = context.getScript(populatedName,this);
        if(toCall == null){
            logger.warn("could not find script: {}",populatedName);
        }else {


            if(isAsync()){
                //TODO how to invoke the script?
            }else{
                Cmd copyCmd = toCall.deepCopy();
                injectThen(copyCmd, context);

            }
        }
        context.next(input);
    }

    @Override
    public Cmd copy() {
        return new ScriptCmd(name,async);
    }

}
