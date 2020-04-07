package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.qdup.cmd.Script;
import io.hyperfoil.tools.qdup.cmd.ScriptContext;
import io.hyperfoil.tools.qdup.cmd.SyncContext;
import io.hyperfoil.tools.yaup.AsciiArt;

import java.util.Arrays;
import java.util.stream.Collectors;

//Uses a new Ssh Exec Channel to run a single command on the server and save the output
public class Exec extends Cmd {

    private String command;
    private boolean async;

    public Exec(String command){
        this(command,false,false);
    }
    public Exec(String command,boolean async,boolean silent){
        super(silent);
        this.command = command;
        this.async = async;
    }

    public boolean isAsync(){return async;}
    public String getCommand(){return command;}


    @Override
    public Cmd getSkip(){
        return super.getSkip();
    }

    //
    @Override
    public Cmd getNext(){
        Cmd next = null;
        if( isAsync() ){
            if(hasThens()){
                next = getThens().get(0);
            }else{
                next = null; //no next when async and no children
            }
        }else{
            next = super.getNext();
        }
        return next;
    }

    @Override
    public void run(String input, Context context) {
        String populatedCommand = Cmd.populateStateVariables(getCommand(),this,context.getState());
        if(isAsync() && context instanceof ScriptContext){
            ScriptContext scriptContext = (ScriptContext)context;
            Cmd copy = this.deepCopy(); //use a copy so context.next does not advance to next siblings
            ((Exec)copy).async=false;//remove async so it will execute normally
            if(this.hasParent()){
                copy.loadWith(this.getParent());//copy the with from parents because it will be lost for the copy
            }
            //SyncContext syncContext = new SyncContext(scriptContext.getSession(),scriptContext.getState(),scriptContext.getRun(),scriptContext.getTimer(),copy,scriptContext);
            ScriptContext newContext = scriptContext.newChildContext(
               scriptContext.getTimer().start(this.toString(),true),
               copy
            );
            scriptContext.getRun().getDispatcher().addScriptContext(newContext,false);
            Cmd skip = getSkip();
            newContext.getSession().exec(populatedCommand, (response ) ->{
                newContext.next(response);
            });
            if (skip != null) {
                scriptContext.setCurrentCmd(this,skip);
                scriptContext.run(skip,input);
            }else{
                //noting to skip to so just be done
                scriptContext.setCurrentCmd(this,null);
                scriptContext.run();
            }
        }else {
            context.getSession().exec(populatedCommand, (response) -> {
                context.next(response);
            });
        }
    }
    @Override
    public Cmd copy() {
        return new Exec(getCommand(),isAsync(),isSilent());
    }

    @Override
    public String toString(){return "exec: "+command;}
}
