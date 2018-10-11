package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;
import perf.qdup.cmd.CommandResult;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Sh extends Cmd {

    private static final String LINE_OBSERVER = "sh_line_observer";

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
    public void run(String input, Context context, CommandResult result) {
        String commandString = populateStateVariables(command,this,context.getState());

        //TODO do we need to manually remove the lineObserver?
        context.getSession().removeLineObserver(LINE_OBSERVER);
        if(this.hasWatchers()) {
            context.getSession().addLineObserver(LINE_OBSERVER, (line) -> {
                result.update(this, line);
            });
        }
        if(prompt.isEmpty()) {
            context.getSession().sh(commandString, (output)->{
                result.next(this,output);
            });
        }else{
            HashMap<String,String> populated = new HashMap<>();
            prompt.forEach((key,value)->{
                String populatedValue = Cmd.populateStateVariables(value,this,context.getState(),true);
                populated.put(key,populatedValue);
            });
            context.getSession().sh(commandString,(output)->{
                result.next(this,output);
            },populated);
        }
    }

    @Override
    public Cmd copy() {
        return new Sh(this.command,super.isSilent(),prompt);
    }

    @Override public String toString(){return "sh: "+command;}
}
