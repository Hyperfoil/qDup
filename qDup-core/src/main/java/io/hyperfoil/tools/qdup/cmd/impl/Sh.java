package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.Globals;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.qdup.cmd.Script;
import io.hyperfoil.tools.qdup.config.rule.CmdLocation;
import io.hyperfoil.tools.yaup.AsciiArt;
import io.hyperfoil.tools.yaup.time.SystemTimer;

import java.util.*;

import static io.hyperfoil.tools.qdup.stream.SuffixStream.DEFAULT_DELAY;

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
        //enable stream logging if enabled
        if(context.getCoordinator().getGlobals().getSetting(Globals.STREAM_LOGGING,false)){
            context.log(populatedCommand);
            context.getShell().addLineObserver("stream",context::log);
        }

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
            if(context.getCoordinator().getGlobals().getSetting(Globals.STREAM_LOGGING,false)){
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

        //turn off stream logging if enabled
        if(context.getCoordinator().getGlobals().getSetting(Globals.STREAM_LOGGING,false)){
            context.getShell().removeLineObserver("stream");
        }

        //if the remote shell has exit codes and the response came from the base shell
        //TODO the base shell is not the only one that supports exit code checking
        if(context.getShell()!=null &&
            context.getShell().isOpen() &&
            context.getShell().isPromptShell(getPreviousPrompt()) &&
            context.getShell().getHost().isShell())
        {

            long start = System.currentTimeMillis();

            // Combined exit code capture + pwd + exit code restore in a single shSync
            // to reduce per-command overhead from 3 round-trips to 1
            String combined = context.getShell().shSync("export __qdup_ec=$?; echo \"${__qdup_ec}:::$(pwd)\"; (exit $__qdup_ec)");

            long round_trip_time = System.currentTimeMillis() - start;

            round_trip_time = round_trip_time - context.getShell().getDelay();


            if(round_trip_time > DEFAULT_DELAY){
                round_trip_time = DEFAULT_DELAY;
            }

            if(round_trip_time > 0){
                context.getShell().setDelay((int) round_trip_time);
            }


            String response = "";
            String pwd = "";
            boolean parsed = false;

            // Parse combined response: "exitCode:::pwd" using indexOf to avoid
            // issues if ::: appears in the pwd path
            if (combined != null && !combined.isBlank()) {
                String trimmed = combined.trim();
                int delimIndex = trimmed.indexOf(":::");
                if (delimIndex > 0) {
                    String exitPart = trimmed.substring(0, delimIndex).trim();
                    String pwdPart = trimmed.substring(delimIndex + 3).trim();
                    if (exitPart.matches("-?\\d+")) {
                        response = exitPart;
                        pwd = pwdPart;
                        parsed = true;
                    }
                }
            }

            // Fallback: if parsing failed due to noisy output from concurrent processes,
            // retry by re-echoing the saved variable
            int retry = 0;
            while (!parsed && retry < 5 && combined != null && !combined.isBlank() && context.getShell().isReady() && !context.isAborted()) {
                combined = context.getShell().shSync("echo \"${__qdup_ec}:::$(pwd)\"");
                if (combined != null) {
                    String trimmed = combined.trim();
                    int delimIndex = trimmed.indexOf(":::");
                    if (delimIndex > 0) {
                        String exitPart = trimmed.substring(0, delimIndex).trim();
                        String pwdPart = trimmed.substring(delimIndex + 3).trim();
                        if (exitPart.matches("-?\\d+")) {
                            response = exitPart;
                            pwd = pwdPart;
                            parsed = true;
                        }
                    }
                }
                retry++;
            }
            // If we had to retry, $? was changed by the retries, so restore it
            if (retry > 0 && parsed) {
                context.getShell().shSync("(exit $__qdup_ec)");
            }

            if (!parsed) {
                // All retries failed — log error and abort to avoid running in an unknown state
                context.error("failed to parse exit code and pwd from postRun output [" + combined + "] for " + this);
                context.abort(false);
            } else if (context.getShell().isReady() && !context.isAborted()) {
                context.setCwd(pwd);
                if(response.matches("\\d+")){
                    try {
                        context.getCommandTimer().getData().set("exit_code", Integer.parseInt(response));
                    }catch (NumberFormatException e){
                        context.getCommandTimer().getData().set("exit_code", response);
                    }
                } else {
                    context.getCommandTimer().getData().set("exit_code", response);
                }
                context.getShell().flushAndResetBuffer();
            }
            //log the output if not using stream logging
            if(!context.getCoordinator().getGlobals().getSetting(Globals.STREAM_LOGGING,false)){
                String toLog = getLogOutput(output,context);
                if (toLog != null && !toLog.isBlank()) {
                    if ("0".equals(response) || !shouldCheckExit(context)) {
                        context.log(toLog);
                    } else {
                        context.error(toLog);
                    }
                }
            }
            //abort on non-zero exit if needed
            if(parsed && !"0".equals(response) && shouldCheckExit(context)){
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
                    if(response.matches("-?\\d+")){
                        context.error("aborting run due to exit code ["+response+"]\n  host: "+context.getShell().getHost()+"\n  command: "+ this +(stack.length()>0?"\nstack:"+stack.toString():""));
                    }else{
                        context.error("aborting run due to unexpected characters in exit code ["+response+"] The prompt may have changed\n  host: "+context.getShell().getHost()+"\n  command: "+ this +(stack.length()>0?"\nstack:"+stack.toString():""));
                    }

                    context.abort(false);
                }
            }
        }else{
            //turn off stream logging if on
            if(!context.getCoordinator().getGlobals().getSetting(Globals.STREAM_LOGGING,false)){
                String toLog = getLogOutput(output,context);
                if (toLog != null && !toLog.isBlank()) {
                    context.log(toLog);
                }
            }
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
