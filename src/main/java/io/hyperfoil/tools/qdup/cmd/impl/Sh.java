package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.yaup.AsciiArt;

import java.util.*;
import java.util.stream.Collectors;

public class Sh extends Cmd {

    private String command;
    private String populatedCommand;
    private Map<String,String> prompt;

    private String exitCode = "";

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

        populatedCommand = populateStateVariables(command,this,context.getState());
        if(Cmd.hasStateReference(populatedCommand,this)){
            context.terminal(
               String.format("%sAbort! %s%s",
                  context.isColorTerminal()? AsciiArt.ANSI_RED:"",
                  command,
                  context.isColorTerminal()?AsciiArt.ANSI_RESET:""
               )
            );
            context.abort(false);
        }
        context.getTimer().start("Sh-invoke:"+populatedCommand);
        //TODO do we need to manually remove the lineObserver?
        if(prompt.isEmpty()) {
            context.getSession().sh(populatedCommand, (output)->{
                context.next(output);
            });
        }else{
            HashMap<String,String> populated = new HashMap<>();
            prompt.forEach((key,value)->{
                String populatedValue = Cmd.populateStateVariables(value,this,context.getState());
                populated.put(key,populatedValue);
            });

            context.getSession().sh(populatedCommand,context::next,populated);
        }
        context.getTimer().start("Sh-await-callback:"+populatedCommand);
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
    public void postRun(String output,Context context){
        //not working in benchlab
        String response = context.getSession().shSync("export __qdup_ec=$?; echo $__qdup_ec;");
        context.getSession().shSync("(exit $__qdup_ec);");
        String toLog = getLogOutput(output,context);
//        context.log(toLog);
        //not working in lab :(
        if(toLog != null && !toLog.isBlank()) {
            if ("0".equals(response)) {
                context.log(toLog);
            }else{
                context.error(toLog);
            }
        }
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
