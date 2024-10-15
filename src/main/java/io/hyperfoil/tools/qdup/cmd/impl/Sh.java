package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.Globals;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.qdup.cmd.Script;
import io.hyperfoil.tools.qdup.config.rule.CmdLocation;
import io.hyperfoil.tools.yaup.time.SystemTimer;

import java.util.*;

public class Sh extends Cmd {

    private String command;
    private String populatedCommand;
    private Map<String,String> prompt;

    private String exitCode = "";
    private String ignoreExitCode = "";
    private String previousPrompt="";

    private SystemTimer commandTimer = null;

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

    public boolean hasIgnoreExitCode(){
        return ignoreExitCode!=null && !ignoreExitCode.isBlank();
    }
    public String getIgnoreExitCode(){return ignoreExitCode;}
    public void setIgnoreExitCode(String ignoreExitCode){
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
        if(populatedCommand.contains(getPatternPrefix())){

        }
        context.getCommandTimer().start("invoke");
        //TODO do we need to manually remove the lineObserver?
        if(prompt.isEmpty()) {
            context.getShell().sh(populatedCommand, (output, promptName)->{
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

            context.getShell().sh(
                    populatedCommand,
                    (output,promptName)->{
                        setPreviousPrompt(promptName);
                        context.next(output);
                    },
                    populated
            );
            //log the command if using stream logging
            if(context.getCoordinator().getSetting(Globals.STREAM_LOGGING,false)){
                context.log(populatedCommand);
            }
        }
        context.getCommandTimer().start("await-callback");
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



        //if the remove shell has exit codes and the response came from the base shell
        if(context.getShell()!=null &&
                context.getShell().isOpen() &&
                /*SshSession.PROMPT.equals(getPreviousPrompt()) &&*/
                context.getShell().isPromptShell(getPreviousPrompt()) &&
                context.getShell().getHost().isShell())
        {
            String response = context.getShell().shSync("export __qdup_ec=$?; echo $__qdup_ec;");
            // ensure the output does not contain characters from other processes
            // this gets into a hot loop when ctrl+C the process
            int retry = 0;
            while(retry < 5 && !response.matches("\\d+") && !response.isBlank() && context.getShell().isReady() && !context.isAborted()){
                response = context.getShell().shSync("echo $__qdup_ec;");
                retry++;
            }
            if (!response.isBlank() && context.getShell().isReady() && !context.isAborted()) {
                //trying to only run these if the previous shSync had a result to avoid deadlocking
                String pwd = context.getShell().shSync("pwd");
                context.setCwd(pwd);
                context.getCommandTimer().getData().set("exit_code", response);
                //not including this in the profile data atm
                //context.getCommandTimer().getData().set("cwd", pwd);
                //restore the exit code for any user exit code checking in their script
                context.getShell().shSync("(exit $__qdup_ec);");
                context.getShell().flushAndResetBuffer();
            }
            //log the output if not using stream logging
            if(!context.getCoordinator().getSetting(Globals.STREAM_LOGGING,false)){
                String toLog = getLogOutput(output,context);
                if (toLog != null && !toLog.isBlank()) {
                    if ("0".equals(response)) {
                        context.log(toLog);
                    } else {
                        context.error(toLog);
                    }
                }
            }
            //abort on non-zero exit if needed
            if(!"0".equals(response) && shouldCheckExit(context)){
                    boolean couldBeCtrlC = walk(CmdLocation.createTmp(), (cmd) -> {
                        return cmd instanceof CtrlC;
                    }).stream().anyMatch(Boolean::booleanValue);
                    if( !couldBeCtrlC) {
                        Cmd cmd = this;
                        StringBuilder stack = new StringBuilder();
                        while(cmd!=null){
                            if( !(cmd instanceof ScriptCmd) ){
                                stack.append(System.lineSeparator());
                                stack.append((cmd instanceof Script ? "script: ":"") + cmd.toString());
                            }
                            cmd = cmd.getParent();
                        }

                        context.error("aborting run due to exit code "+response+"\n  host: "+context.getShell().getHost()+"\n  command: "+ this +(stack.length()>0?"\nstack:"+stack.toString():""));
                        context.abort(false);
                    }
            }

        }else{
            //duplicate getting toLog to avoid the overhead if not needed
            String toLog = getLogOutput(output,context);
            context.log(toLog);
        }
    }

    public boolean shouldCheckExit(Context context){
        if(context.checkExitCode() && hasIgnoreExitCode()){
            String populated = Cmd.populateStateVariables(getIgnoreExitCode(), this, context);
            if(Cmd.hasStateReference(populated, this)){
                //we failed to populate ignore exit code
                context.error("failed to populate ignore-exit-code: "+populated+" for "+this);
                context.abort(false);
            }else{
                boolean ignore = Boolean.parseBoolean(populated);
                return !ignore;
            }

        }
        return context.checkExitCode();
    }

    @Override
    public Cmd copy() {
        Sh rtrn = new Sh(this.getCommand(),super.isSilent(),prompt);
        if(hasIgnoreExitCode()){
            rtrn.setIgnoreExitCode(getIgnoreExitCode());
        }
        return rtrn;
    }

    @Override public String toString(){
        String toUse = populatedCommand!=null ? populatedCommand : command;
        return "sh: "+toUse;
    }
}
