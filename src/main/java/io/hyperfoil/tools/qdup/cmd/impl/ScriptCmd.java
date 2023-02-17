package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.SshSession;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.*;
import io.hyperfoil.tools.qdup.config.RunRule;
import io.hyperfoil.tools.qdup.config.rule.CmdLocation;
import io.hyperfoil.tools.qdup.shell.AbstractShell;
import io.hyperfoil.tools.yaup.AsciiArt;

import java.util.List;
import java.util.function.BiFunction;

/*
This class is used when referencing a script in a role phase and when referencing a script in another script
It needs to inject the script command tree when run as a phase reference
Injecting when referenced inside a script causes it to break when the injection is in a loop
    It cannot inject each time it is called but does need to change the Context currentCmd or inject the first time?
 */
public class ScriptCmd extends Cmd {
    /**
     * Used to ensure script then's are called after the script with input from the script
     */
    private class CallbackCmd extends Cmd {
        @Override
        public void run(String input, Context context) {
            super.setOutput(input);
            context.next(input);
        }

        @Override
        public Cmd getNext(){
            Cmd rtrn = ScriptCmd.super.getNext();
            return rtrn;
        }

        @Override
        public void setOutput(String output){
        }

        @Override
        public Cmd copy() {return null;}

        @Override
        public String getLogOutput(String output, Context context) {
            return "";
        }
    }

    private final String name;
    private final boolean async;
    private final boolean addToCmdTree;
    private Cmd foundScript = null;

    @Override
    public String getOutput() {
        String rtrn = callback.getOutput() == null ? super.getOutput() : callback.getOutput();
        return rtrn;
    }

    private String populatedName = null;
    private final CallbackCmd callback = new CallbackCmd();
    public ScriptCmd(String name){
        this(name,false,false);
    }
    public ScriptCmd(String name,boolean async, boolean addToCmdTree){
        this.name = name;
        this.async = async;
        this.addToCmdTree = addToCmdTree;
    }

    public boolean isAsync(){return async;}
    public String getName(){
        if(populatedName != null){
            return populatedName;
        }
        return name;
    }
    @Override
    public String toString(){return "script-cmd: " + name;}

    @Override
    public Cmd getNext() {
        Cmd rtrn = null;
        if(hasToCall()){
            rtrn = getToCall();
        }else{
            rtrn = super.getNext();
        }
        return rtrn;
    }

    @Override
    public<T> void walk(BiFunction<Cmd, CmdLocation,T> converter, CmdLocation location, List<T> rtrn){
        if(isAsync()){
            super.walk(converter, location.newPosition(CmdLocation.Position.Child),rtrn);
        }else{
            super.walk(converter,location,rtrn);
        }
    }

    private void clearToCall(){this.foundScript = null;}
    private boolean hasToCall(){return foundScript!=null;}
    private Cmd getToCall(){return foundScript;}
    private void setToCall(Cmd cmd){
        this.foundScript = cmd;
    }

    @Override
    public void run(String input, Context context) {
        clearToCall();
        populatedName = populateStateVariables(name,this,context);

        Script toCall = context.getScript(populatedName,this);
        Cmd originalNext = getNext();
        if(toCall == null){
            logger.warn("could not find script: {}",populatedName);
        }else {
            Cmd copyCmd = toCall.deepCopy();
            if(isAsync()){
                //TODO how to invoke the script?
                AbstractShell ssh = context.getSession() != null ? context.getSession().openCopy() : null;
                State state = context.getState().clone();
                Run run = context instanceof ScriptContext ? ((ScriptContext)context).getRun() : null;
                if(run == null && context instanceof SyncContext){
                    run = ((SyncContext)context).getRun();
                }

                //copy withs because it will not be inherited
                copyCmd.loadWith(this);

                ScriptContext scriptContext = new ScriptContext(ssh,state,run,context.getContextTimer().start(populatedName,true),copyCmd,context.checkExitCode());
                if(run!=null){ //register context so phase does not end before script completes
                    //dispatcher will also start the context
                    run.getDispatcher().addScriptContext(scriptContext);
                }
            }else{
                copyCmd.setParent(this);
                setToCall(copyCmd);
                if(hasThens()) {
                    getToCall().then(callback);
                }
            }
        }
        context.next(input);

    }

    @Override
    public Cmd copy() {
        return new ScriptCmd(name,async,addToCmdTree);
    }

}
