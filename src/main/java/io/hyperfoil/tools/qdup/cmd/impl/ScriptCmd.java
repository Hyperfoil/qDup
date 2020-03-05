package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.SshSession;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.qdup.cmd.Script;
import io.hyperfoil.tools.qdup.cmd.ScriptContext;
import io.hyperfoil.tools.yaup.AsciiArt;

/*
This class is used when referencing a script in a role phase and when referencing a script in another script
It needs to inject the script command tree when run as a phase reference
Injecting when referenced inside a script causes it to break when the injection is in a loop
    It cannot inject each time it is called but does need to change the Context currentCmd or inject the first time?

 */

public class ScriptCmd extends Cmd {

    private final String name;
    private final boolean async;
    private final boolean addToCmdTree;

    private Cmd foundScript = null;

    private String populatedName = null;
    public ScriptCmd(String name){
        this(name,false,false);
    }
    public ScriptCmd(String name,boolean async, boolean addToCmdTree){
        this.name = name;
        this.async = async;
        this.addToCmdTree = addToCmdTree;
    }

    public boolean isAsync(){return async;}
    public String getName(){return name;}
    @Override
    public String toString(){return "script: "+name;}

    @Override
    public Cmd getNext() {

        //Exception e = new RuntimeException("");
        //e.printStackTrace();
        Cmd rtrn = null;

        boolean fromParent = false;

        if(hasToCall()){
            rtrn = getToCall();
            //clearToCall();
            Cmd next = super.getNext();
            if(next == null){

            }
            //rtrn.getTail().thenOrphaned(next);
            rtrn.thenOrphaned(next);

        }else{
            rtrn = super.getNext();
            fromParent = true;
        }

        return rtrn;
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
        populatedName = populateStateVariables(name,this,context.getState());
        Script toCall = context.getScript(populatedName,this);
        Cmd originalNext = getNext();
        if(toCall == null){
            logger.warn("could not find script: {}",populatedName);
        }else {
            Cmd copyCmd = toCall.deepCopy();
            if(isAsync()){
                //TODO how to invoke the script?
                SshSession ssh = context.getSession() != null ? context.getSession().openCopy() : null;
                State state = context.getState().clone();
                Run run = context instanceof ScriptContext ? ((ScriptContext)context).getRun() : null;

                //copy withs because it will not be inherited
                copyCmd.loadWith(this);
                //copyCmd.with(getWith(true,true));

                ScriptContext scriptContext = new ScriptContext(ssh,state,run,context.getProfiler(),copyCmd);
                if(run!=null){ //register context so phase does not end before script completes
                    //dispatcher will aslo start the context
                    run.getDispatcher().addScriptContext(scriptContext);
                }
            }else{
                //injectThen(copyCmd, context);
                copyCmd.loadWith(this);

                setToCall(copyCmd);
            }
        }
        context.next(input);
        if(!addToCmdTree && !async){
            //remove the injected command from the tree after it was picked up by context.next
            forceNext(originalNext);
        }
    }

    @Override
    public Cmd copy() {
        return new ScriptCmd(name,async,addToCmdTree);
    }

}
