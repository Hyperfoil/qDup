package io.hyperfoil.tools.qdup.cmd;

import io.hyperfoil.tools.qdup.cmd.impl.InvokeCmd;
import io.hyperfoil.tools.qdup.cmd.impl.Regex;
import io.hyperfoil.tools.qdup.cmd.impl.ScriptCmd;
import io.hyperfoil.tools.qdup.cmd.impl.Sh;
import io.hyperfoil.tools.qdup.cmd.impl.Signal;
import io.hyperfoil.tools.qdup.cmd.impl.WaitFor;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;

import io.hyperfoil.tools.yaup.Counters;
import io.hyperfoil.tools.yaup.StringUtil;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * Created by wreicher
 * Reads through the command tree looking for waitFor, signal, and sh that are under a watcher
 * Identifies variable name references and regex variable names
 */
public class CommandSummary {

    private void processCommand(Cmd command, boolean isWatching, RunConfigBuilder config, Cmd.Ref ref){
        String toString = command.toString();

        if(isWatching && command instanceof Sh){
            addWarning(command+" cannot be called while watching another command. Sh commands require a session that cannot be accesses while watching another command.");
        }

        if(command instanceof Signal){
            String populatedSignal = Cmd.populateStateVariables(((Signal)command).getName(),command,config.getState(), ref);

            addSignal(populatedSignal);
            //TODO how to detect when a signal has an initialized value
//            if(populatedSignal.contains(Cmd.STATE_PREFIX) && !hasWait(populatedSignal)) {
//                addWarning("signal: " + populatedSignal + " does not have a known value for state variable and cannot calculate expected signal count. waits:"+waits);
//            } else {
//                addSignal(populatedSignal);
//            }
        }else if (command instanceof WaitFor){
            String populatedWait = Cmd.populateStateVariables(((WaitFor)command).getName(),command,config.getState(), ref);
            if(populatedWait.contains(StringUtil.PATTERN_PREFIX) && !((WaitFor)command).hasInitial()) {
                // TODO better detection of populatedStateVariables
                //                addWarning("wait-for: " + populatedWait + " does not have a known value for state variable and will likely not be signalled");
            } else {

                addWait(populatedWait);
            }
        }else if (command instanceof ScriptCmd){
            String scriptName = ((ScriptCmd)command).getName();
            Script namedScript = config.getScript(scriptName,command);
            if(namedScript==null){
                //TODO is it an error if a script isn't found?
            }else{
                processCommand(namedScript,isWatching,config,ref.add(command));
            }

        }else if (command instanceof InvokeCmd){
            Cmd invokedCmd = ((InvokeCmd)command).getCommand();
            processCommand(invokedCmd,isWatching,config,ref.add(command));
        }else if (command instanceof Regex){
            String pattern = ((Regex)command).getPattern();
            Matcher matcher = Cmd.NAMED_CAPTURE.matcher(pattern);
            while(matcher.find()){
                String name = matcher.group(1);
                addRegexVariable(name);
            }
        }else{
        }

        if(toString.indexOf(StringUtil.PATTERN_PREFIX)>-1) {

            Matcher matcher = Cmd.STATE_PATTERN.matcher(toString);
            while (matcher.find()) {
                String name = matcher.group("name");
                addVariable(name);
            }
        }

        if(!command.getWatchers().isEmpty()){
            for(Cmd watcher : command.getWatchers()){
                processCommand(watcher,true,config,ref);
            }
        }
        if(!command.getThens().isEmpty()){
            for(Cmd then : command.getThens()){
                processCommand(then,isWatching,config,ref);
            }
        }
        if(command.hasTimers()){
            for(long timeout: command.getTimeouts()){
                for(Cmd timer : command.getTimers(timeout)){
                    processCommand(timer,true,config,ref);
                }
            }
        }
        if(command.hasSignalWatchers()){
            for(String name : command.getSignalNames()){
                for(Cmd onSignal : command.getSignal(name)){
                    processCommand(onSignal,true,config,ref);
                }
            }
        }
    }

    private String name;
    private List<String> warnings;
    private Counters<String> signals;
    private Set<String> waits;
    private Set<String> variables;
    private Set<String> regexVariables;

    public CommandSummary(Cmd command, RunConfigBuilder config){
        this.name = command.toString();

        warnings = new LinkedList<>();
        signals = new Counters<>();
        waits = new HashSet<>();
        variables = new HashSet<>();
        regexVariables = new HashSet<>();

        processCommand(command,false,config,new Cmd.Ref(command));
    }

    public String getName(){return name;}

    private void addWarning(String warning){
        warnings.add(warning);
    }
    private void addRegexVariable(String name){ regexVariables.add(name); }
    private void addVariable(String name){ variables.add(name); }
    private void addSignal(String name){
        if(!name.isEmpty()){
            signals.add(name);
        }
    }
    private boolean hasWait(String name){
        return waits.contains(name);
    }
    private void addWait(String name){
        if(!name.isEmpty()) {
            waits.add(name);
        }
    }
    public List<String> getWarnings(){
        return warnings;
    }
    public Counters<String> getSignals(){
        return signals;
    }
    public Set<String> getWaits(){
        return waits;
    }
    public Set<String> getVariables(){return variables;}
    public Set<String> getRegexVariables(){return regexVariables;}
    public Set<String> getStateDependentVariables(){
        Set<String> rtrn = new HashSet<>(variables);
        rtrn.removeAll(regexVariables);
        return rtrn;
    }
    public String toString(){
        final StringBuffer rtrn= new StringBuffer();
        rtrn.append(name+" "+super.toString()+"\n");
        if(!warnings.isEmpty()){
            rtrn.append("  warnings:\n");
            warnings.forEach(warning -> rtrn.append("    "+warning+"\n"));
        }
        if(!signals.isEmpty()){
            rtrn.append("  signals:\n");
            signals.forEach(signal -> rtrn.append("    "+signal+"\n"));
        }
        if(!waits.isEmpty()){
            rtrn.append("  waits:\n");
            waits.forEach(waiter -> rtrn.append("    "+waiter+"\n"));
        }
        if(!variables.isEmpty()){
            rtrn.append("  variables:\n");
            variables.forEach(variable -> rtrn.append("    "+variable+"\n"));
        }
        if(!regexVariables.isEmpty()){
            rtrn.append("  regexVariables:\n");
            regexVariables.forEach(autoVariable -> rtrn.append("    "+autoVariable+"\n"));
        }
        Set<String> stateDependent = getStateDependentVariables();
        if(!stateDependent.isEmpty()){
            rtrn.append("  stateDependencies:\n");
            stateDependent.forEach(v->rtrn.append("    "+v+"\n"));
        }
        return rtrn.toString();
    }
}
