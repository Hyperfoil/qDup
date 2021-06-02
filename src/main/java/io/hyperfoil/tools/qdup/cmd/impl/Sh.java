package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.SshSession;
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
    private boolean ignoreExitCode = false;
    private String previousPrompt="";

    private void setPreviousPrompt(String prompt){
        this.previousPrompt = prompt;
    }
    public String getPreviousPrompt(){return previousPrompt;}

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

    public boolean isIgnoreExitCode(){return ignoreExitCode;}
    public void setIgnoreExitCode(boolean ignoreExitCode){
        this.ignoreExitCode = ignoreExitCode;
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
        populatedCommand = populateStateVariables(command,this,context);
        if(Cmd.hasStateReference(populatedCommand,this)){
            List<String> missing = Cmd.getStateVariables(populatedCommand,this,context);
            context.error(
               String.format("Abort! Failed to populate pattern: %s%n missing %s",
                  command,
                  missing
               )
            );
            context.abort(false);
        }
        context.getTimer().start("Sh-invoke:"+populatedCommand);
        //TODO do we need to manually remove the lineObserver?
        if(prompt.isEmpty()) {
            context.getSession().sh(populatedCommand, (output,promptName)->{
                setPreviousPrompt(promptName);
                context.next(output);
            });
        }else{
            HashMap<String,String> populated = new HashMap<>();
            prompt.forEach((key,value)->{
                String populatedKey = Cmd.populateStateVariables(key,this,context);
                String populatedValue = Cmd.populateStateVariables(value,this,context);
                populated.put(populatedKey,populatedValue);
            });

            context.getSession().sh(
                    populatedCommand,
                    (output,promptName)->{
                        setPreviousPrompt(promptName);
                        context.next(output);
                    },
                    populated
            );
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

    //TODO add a way to disable post-run for commands that change propt (e.g. psql or docker exec)
    @Override
    public void postRun(String output,Context context){
        String toLog = getLogOutput(output,context);
        //not working in benchlab?

        if(context.getSession()!=null && context.getSession().isOpen() && SshSession.PROMPT.equals(getPreviousPrompt()) && context.getSession().getHost().isSh()){
            String response = context.getSession().shSync("export __qdup_ec=$?; echo $__qdup_ec;");
            context.getSession().shSync("(exit $__qdup_ec);");
            context.getSession().flushAndResetBuffer();

            //not working in lab :(
            if(toLog != null && !toLog.isBlank()) {
                if ("0".equals(response)) {
                    context.log(toLog);
                } else {
                    context.error(toLog);
                    if (context.checkExitCode() && !isIgnoreExitCode()) {
                        boolean couldBeCtrlC = walk(true, (cmd) -> cmd instanceof CtrlC).stream().anyMatch(Boolean::booleanValue);
                        context.abort(false);
                    }
                }
            }
        }else{
            context.log(toLog);
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
