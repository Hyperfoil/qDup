package perf.ssh.cmd;

import perf.ssh.ScriptRepo;
import perf.util.StringUtil;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * Created by wreicher
 * Reads through the command tree looking for waitFor, signal, and sh that are under a watcher
 */
public class CommandSummary {

    public static CommandSummary apply(Cmd command,ScriptRepo repo){
        CommandSummary rtrn = new CommandSummary(command.toString());
        processCommand(command,false,rtrn,repo);

        return rtrn;
    }

    private static void processCommand(Cmd command,boolean isWatching,CommandSummary summary,ScriptRepo repo){
        String toString = command.toString();

        if(isWatching && command instanceof Cmd.Sh){
            summary.addWarning(command+" cannot be called while watching another command. Sh commands require a session that cannot be accesses while watching another command.");
        }

        if(StringUtil.countOccurances(Cmd.ENV_PREFIX,toString) != StringUtil.countOccurances(Cmd.ENV_SUFFIX,toString)){
            summary.addWarning(command+" does not have the same number of ${{ and }} for state variable referencing");
        }

        if(command instanceof Cmd.Signal){
            summary.addSignal(((Cmd.Signal)command).getName());
        }else if (command instanceof Cmd.WaitFor){
            summary.addWait(((Cmd.WaitFor)command).getName());
        }else if (command instanceof Cmd.ScriptCmd){
            Script namedScript = repo.script(((Cmd.ScriptCmd)command).getName());
            processCommand(namedScript,isWatching,summary,repo);
        }else if (command instanceof Cmd.InvokeCmd){
            Cmd invokedCmd = ((Cmd.InvokeCmd)command).getCommand();
            processCommand(invokedCmd,isWatching,summary,repo);
        }else if (command instanceof Cmd.Filter){
            String pattern = ((Cmd.Filter)command).getPattern();
            Matcher matcher = Cmd.NAMED_CAPTURE.matcher(pattern);
            while(matcher.find()){
                String name = matcher.group(1);
                summary.addAutoVariable(name);
            }
        }

        if(toString.indexOf(Cmd.ENV_PREFIX)>-1) {

            Matcher matcher = Cmd.ENV_PATTERN.matcher(toString);
            while (matcher.find()) {
                String name = matcher.group("name");
                summary.addVariable(name);
            }
        }

        if(!command.getWatchers().isEmpty()){
            for(Cmd watcher : command.getWatchers()){
                processCommand(watcher,true,summary,repo);
            }
        }
        if(!command.getThens().isEmpty()){
            for(Cmd then : command.getThens()){
                processCommand(then,isWatching,summary,repo);
            }
        }
    }

    private String name;
    private List<String> warnings;
    private Set<String> signals;
    private Set<String> waits;
    private Set<String> variables;
    private Set<String> autoVariables;

    private CommandSummary(String name){
        this.name = name;

        warnings = new LinkedList<>();
        signals = new HashSet<>();
        waits = new HashSet<>();
        variables = new HashSet<>();
        autoVariables = new HashSet<>();
    }

    public String getName(){return name;}

    private void addWarning(String warning){
        warnings.add(warning);
    }
    private void addAutoVariable(String name){ autoVariables.add(name); }
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
    public Set<String> getAutoVariables(){return autoVariables;}
    public Set<String> getStateDependentVariables(){
        Set<String> rtrn = new HashSet<>(variables);
        rtrn.removeAll(autoVariables);
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
        if(!autoVariables.isEmpty()){
            rtrn.append("  regexVariables:\n");
            autoVariables.forEach(autoVariable -> rtrn.append("    "+autoVariable+"\n"));
        }
        Set<String> stateDependent = getStateDependentVariables();
        if(!stateDependent.isEmpty()){
            rtrn.append("  stateDependencies:\n");
            stateDependent.forEach(v->rtrn.append("    "+v+"\n"));
        }
        return rtrn.toString();
    }
}
