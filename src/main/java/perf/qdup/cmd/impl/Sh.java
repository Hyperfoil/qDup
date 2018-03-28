package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;
import perf.qdup.cmd.CommandResult;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Sh extends Cmd {
    private String command;
    private Map<String,String> prompt;
    public Sh(String command){
        this(command,false, Collections.EMPTY_MAP);
    }
    public Sh(String command,boolean silent){
        this(command,silent,Collections.EMPTY_MAP);

    }
    public Sh(String command,boolean silent,Map<String,String> prompt){
        super(silent);
        this.command = command;
        this.prompt = prompt;
    }

    public String getCommand(){return command;}
    public Map<String,String> getPrompt(){
        return Collections.unmodifiableMap(prompt);
    }

    @Override
    protected void run(String input, Context context, CommandResult result) {
        String commandString = populateStateVariables(command,this,context.getState());
        if(prompt.isEmpty()) {
            context.getSession().sh(commandString, this, result);
        }else{
            context.getSession().sh(commandString,this, result,prompt);
        }

    }

    @Override
    protected Cmd clone() {
        return new Sh(this.command,super.isSilent(),prompt).with(this.with);
    }

    @Override public String toString(){return "sh: "+(isSilent()?"silent=true ":"")+(prompt.isEmpty()?"":"prompts="+prompt+" ")+command;}
}
