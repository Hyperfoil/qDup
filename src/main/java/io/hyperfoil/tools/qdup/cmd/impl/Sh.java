package io.hyperfoil.tools.qdup.cmd.impl;

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
    private boolean ignoreExitCode = false;
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
        String toLog = getLogOutput(output,context);
        if(context.getShell()!=null &&
                context.getShell().isOpen() &&
                /*SshSession.PROMPT.equals(getPreviousPrompt()) &&*/
                context.getShell().isPromptShell(getPreviousPrompt()) &&
                context.getShell().getHost().isShell())
        {
            String response = context.getShell().shSync("export __qdup_ec=$?; echo $__qdup_ec;");
            String pwd = context.getShell().shSync("pwd");
            context.setCwd(pwd);
            context.getCommandTimer().getJson().set("response",response);
            context.getCommandTimer().getJson().set("cwd",pwd);
            context.getShell().shSync("(exit $__qdup_ec);");
            context.getShell().flushAndResetBuffer();

            //not working in lab :(
            if(toLog != null && !toLog.isBlank()) {
                if ("0".equals(response)) {
                    context.log(toLog);
                } else {
                    context.error(toLog);
                    if ( shouldCheckExit(context) ) {
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
                }
            }
        }else{
            context.log(toLog);
        }
    }

    public boolean shouldCheckExit(Context context){
        return (context.checkExitCode() && !isIgnoreExitCode());
    }

    @Override
    public Cmd copy() {
        Sh rtrn = new Sh(this.getCommand(),super.isSilent(),prompt);
        rtrn.setIgnoreExitCode(isIgnoreExitCode());
        return rtrn;
    }

    @Override public String toString(){
        String toUse = populatedCommand!=null ? populatedCommand : command;
        return "sh: "+toUse;
    }
}
