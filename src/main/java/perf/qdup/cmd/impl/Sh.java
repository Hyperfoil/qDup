package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class Sh extends Cmd {

    private String command;
    private String populatedCommand;
    private Map<String,String> prompt;
    public Sh(String command){
        this(command,false);
    }
    public Sh(String command,boolean silent){
        this(command,silent,new LinkedHashMap<>());

    }
    public Sh(String command, boolean silent, Map<String,String> prompt){
        super(silent);
        this.command = command;
        this.prompt = prompt;
    }

    public void addPrompt(String prompt,String response){
        this.prompt.put(prompt,response);
    }

    public String getCommand(){return command;}
    public Map<String,String> getPrompt(){
        return Collections.unmodifiableMap(prompt);
    }

    @Override
    public void run(String input, Context context) {
        context.getProfiler().start("Sh-invoke:"+command);
        populatedCommand = populateStateVariables(command,this,context.getState());

        //TODO do we need to manually remove the lineObserver?
        if(prompt.isEmpty()) {
            context.getSession().sh(populatedCommand, (output)->{
                context.next(output);
            });
        }else{
            HashMap<String,String> populated = new HashMap<>();
            prompt.forEach((key,value)->{
                String populatedValue = Cmd.populateStateVariables(value,this,context.getState(),true);
                populated.put(key,populatedValue);
            });
            context.getSession().sh(populatedCommand,context::next,populated);
        }
        context.getProfiler().start("Sh-await-callback:"+command);
    }

    @Override
    public String getLogOutput(String output,Context context){
        String rtrn = populatedCommand;
        if(!isSilent() && output!=null && !output.isEmpty()){
            rtrn+="\n"+output;
        }
        return rtrn;
    }

    @Override
    public Cmd copy() {
        return new Sh(this.command,super.isSilent(),prompt);
    }

    @Override public String toString(){
        String toUse = populatedCommand!=null ? populatedCommand : command;
        return "sh: "+toUse;
    }
}
