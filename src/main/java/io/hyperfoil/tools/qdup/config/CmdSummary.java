package io.hyperfoil.tools.qdup.config;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Script;
import io.hyperfoil.tools.qdup.cmd.impl.*;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;

import io.hyperfoil.tools.qdup.config.yaml.Parser;
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
public class CmdSummary {

    private void processCommand(Cmd command, boolean isWatching, RunConfigBuilder config, Cmd.Ref ref) {
        String toString = command.toString();

        String commandStr = yamlParser!=null ? yamlParser.dump(yamlParser.representCommand(command)) : command.toString();
        if(Cmd.hasStateReference(commandStr,command)){
            List<String> references = Cmd.getStateVariables(commandStr,command,config.getState(),ref);
            references.forEach(this::addUseVariable);
            String populated = Cmd.populateStateVariables(commandStr, command, config.getState(), ref);
            if(Cmd.hasStateReference(populated,command)){
                addWarning("Failed to populate pattern",command);
            }
        }
        if (isWatching && command instanceof Sh) {
            addWarning(command + " cannot be called while watching another command. Sh commands require a session that cannot be accesses while watching another command.",command);
        }
        if (command instanceof Signal) {
            String populatedSignal = Cmd.populateStateVariables(((Signal) command).getName(), command, config.getState(), ref);
            addSignal(populatedSignal);
            if(Cmd.hasStateReference(populatedSignal,command)){
            }
        } else if (command instanceof WaitFor) {
            String populatedWait = Cmd.populateStateVariables(((WaitFor) command).getName(), command, config.getState(), ref);
            addWait(populatedWait);
            if(Cmd.hasStateReference(populatedWait,command)){
                if( ((WaitFor)command).hasInitial()){

                }else{
                    //TODO detect when a wait-for is not initialized
                }
            }
        } else if (command instanceof ScriptCmd) {
            String scriptName = ((ScriptCmd) command).getName();
            scriptName = Cmd.populateStateVariables(scriptName,command,config.getState());
            Script namedScript = config.getScript(scriptName, command);
            if (namedScript == null) {
                addWarning("missing script: "+scriptName,command);
                //TODO is it an error if a script isn't found?
            } else {
                walk(namedScript,isWatching,config,ref.add(command));
                //processCommand(namedScript, isWatching, config, ref.add(command));
            }
        } else if (command instanceof InvokeCmd) {
            Cmd invokedCmd = ((InvokeCmd) command).getCommand();
            walk(invokedCmd,isWatching,config,ref.add(command));
            //processCommand(invokedCmd, isWatching, config, ref.add(command));
        } else if (command instanceof Regex) {
            String pattern = ((Regex) command).getPattern();
            Matcher matcher = Cmd.NAMED_CAPTURE.matcher(pattern);
            while (matcher.find()) {
                String name = matcher.group(1);
                addSetVariable(name);
            }
        } else if (command instanceof SetState) {
            String str = Cmd.populateStateVariables(((SetState) command).getKey(), command, config.getState(), ref);
            addUseVariable(str);
            if (Cmd.hasStateReference(str, command)) {
                //we were unable to identify the exact value for a set-state, does that matter?
            }
        } else if (command instanceof SetSignal){
          String str = Cmd.populateStateVariables(((SetSignal) command).getName(),command,config.getState(),ref);
          addSignal(str);
          if (Cmd.hasStateReference(str,command)){
              //we were unable to identify the exact value for set-signal, does that matter?
          }
        }
    }

    private String name;
    private List<String> warnings;
    private Counters<String> signals;
    private Set<String> waits;
    private Set<String> useVariables;
    private Set<String> setVariables;

    private Parser yamlParser;
    private Set<String> globalSetVariables;

    private void walk(Cmd command, boolean isWatching, RunConfigBuilder config, Cmd.Ref ref){
        command.walk(false,(cmd,watching)->{
            processCommand(cmd,watching,config,ref);
            return true;
        });
    }

    public CmdSummary(Cmd command, RunConfigBuilder config, Parser yamlParser) {
        this.name = command.toString();
        warnings = new LinkedList<>();
        signals = new Counters<>();
        waits = new HashSet<>();
        useVariables = new HashSet<>();
        setVariables = new HashSet<>();
        this.yamlParser = yamlParser;
        Cmd.Ref ref = new Cmd.Ref(command);
        walk(command,false,config,ref);
    }

    public String getName() {
        return name;
    }

    private void addWarning(String warning,Cmd command){
        Cmd script = command;
        while (!(script instanceof Script) && script.getParent() != null) {
            script = script.getParent();
        }
        warnings.add("Error:"+warning+"\nScript: "+script+"\nCommand: "+command);
    }

    private void addSetVariable(String name) {
        setVariables.add(name);
    }

    private void addUseVariable(String name) {
        useVariables.add(name);
    }

    private void addSignal(String name) {
        if (!name.isEmpty()) {
            signals.add(name);
        }
    }

    private boolean hasWait(String name) {
        return waits.contains(name);
    }

    private void addWait(String name) {
        if (!name.isEmpty()) {
            waits.add(name);
        }
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public Counters<String> getSignals() {
        return signals;
    }

    public Set<String> getWaits() {
        return waits;
    }

    public Set<String> getUseVariables() {
        return useVariables;
    }

    public Set<String> getSetVariables() {
        return setVariables;
    }

    public Set<String> getStateDependentVariables() {
        Set<String> rtrn = new HashSet<>(useVariables);
        rtrn.removeAll(setVariables);
        return rtrn;
    }

    public String toString() {
        final StringBuffer rtrn = new StringBuffer();
        rtrn.append(name + " " + super.toString() + "\n");
        if (!warnings.isEmpty()) {
            rtrn.append("  warnings:\n");
            warnings.forEach(warning -> rtrn.append("    " + warning + "\n"));
        }
        if (!signals.isEmpty()) {
            rtrn.append("  signals:\n");
            signals.forEach(signal -> rtrn.append("    " + signal + "\n"));
        }
        if (!waits.isEmpty()) {
            rtrn.append("  waits:\n");
            waits.forEach(waiter -> rtrn.append("    " + waiter + "\n"));
        }
        if (!useVariables.isEmpty()) {
            rtrn.append("  variables:\n");
            useVariables.forEach(variable -> rtrn.append("    " + variable + "\n"));
        }
        if (!setVariables.isEmpty()) {
            rtrn.append("  regexVariables:\n");
            setVariables.forEach(autoVariable -> rtrn.append("    " + autoVariable + "\n"));
        }
        Set<String> stateDependent = getStateDependentVariables();
        if (!stateDependent.isEmpty()) {
            rtrn.append("  stateDependencies:\n");
            stateDependent.forEach(v -> rtrn.append("    " + v + "\n"));
        }
        return rtrn.toString();
    }
}
