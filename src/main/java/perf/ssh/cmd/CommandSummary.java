package perf.ssh.cmd;

import perf.ssh.cmd.impl.*;
import perf.ssh.config.RunConfigBuilder;
import perf.yaup.StringUtil;

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


    private void processCommand(Cmd command,boolean isWatching,RunConfigBuilder config){
        String toString = command.toString();

        if(isWatching && command instanceof Sh){
            addWarning(command+" cannot be called while watching another command. Sh commands require a session that cannot be accesses while watching another command.");
        }

        if(StringUtil.countOccurances(Cmd.STATE_PREFIX,toString) != StringUtil.countOccurances(Cmd.STATE_SUFFIX,toString)){
            addWarning(command+" does not have the same number of ${{ and }} for state variable referencing");
        }

        if(command instanceof Signal){
            addSignal(((Signal)command).getName());
        }else if (command instanceof WaitFor){
            addWait(((WaitFor)command).getName());
        }else if (command instanceof ScriptCmd){
            String scriptName = ((ScriptCmd)command).getName();

            Script namedScript = config.getScript(scriptName,command);
            if(namedScript==null){
                //TODO is it an error if a script isn't found?
            }else{
                processCommand(namedScript,isWatching,config);
            }

        }else if (command instanceof InvokeCmd){
            Cmd invokedCmd = ((InvokeCmd)command).getCommand();
            processCommand(invokedCmd,isWatching,config);
        }else if (command instanceof Regex){
            String pattern = ((Regex)command).getPattern();
            Matcher matcher = Cmd.NAMED_CAPTURE.matcher(pattern);
            while(matcher.find()){
                String name = matcher.group(1);
                addRegexVariable(name);
            }
        }

        if(toString.indexOf(Cmd.STATE_PREFIX)>-1) {

            Matcher matcher = Cmd.STATE_PATTERN.matcher(toString);
            while (matcher.find()) {
                String name = matcher.group("name");
                addVariable(name);
            }
        }

        if(!command.getWatchers().isEmpty()){
            for(Cmd watcher : command.getWatchers()){
                processCommand(watcher,true,config);
            }
        }
        if(!command.getThens().isEmpty()){
            for(Cmd then : command.getThens()){
                processCommand(then,isWatching,config);
            }
        }
    }

    private String name;
    private List<String> warnings;
    private Set<String> signals;
    private Set<String> waits;
    private Set<String> variables;
    private Set<String> regexVariables;

    public CommandSummary(Cmd command, RunConfigBuilder config){
        this.name = command.toString();

        warnings = new LinkedList<>();
        signals = new HashSet<>();
        waits = new HashSet<>();
        variables = new HashSet<>();
        regexVariables = new HashSet<>();

        processCommand(command,false,config);
    }

    public String getName(){return name;}

    private void addWarning(String warning){
        warnings.add(warning);
    }
    private void addRegexVariable(String name){ regexVariables.add(name); }
    private void addVariable(String name){ variables.add(name); }
    private void addSignal(String name){
        signals.add(name);
    }
    private void addWait(String name){
        waits.add(name);
    }
    public List<String> getWarnings(){
        return warnings;
    }
    public Set<String> getSignals(){
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
