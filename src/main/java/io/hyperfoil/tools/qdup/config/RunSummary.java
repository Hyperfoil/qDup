package io.hyperfoil.tools.qdup.config;

import io.hyperfoil.tools.qdup.Stage;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Script;
import io.hyperfoil.tools.qdup.cmd.impl.InvokeCmd;
import io.hyperfoil.tools.qdup.cmd.impl.ScriptCmd;
import io.hyperfoil.tools.qdup.config.rule.CmdLocation;
import io.hyperfoil.tools.yaup.HashedLists;
import io.hyperfoil.tools.yaup.HashedSets;

import java.util.*;

public class RunSummary implements RunRule{
    private List<RunError> errors;
    private List<RunError> warnings;
    private Map<String,RunRule> rules;

    public RunSummary(){
        rules = new HashMap<>();
        errors = new LinkedList<>();
        warnings = new LinkedList<>();
    }

    public void addRule(String name,RunRule rule){
        rules.put(name,rule);
    }
    public boolean hasRule(String name){
        return rules.containsKey(name);
    }
    public RunRule getRule(String name){
        return rules.get(name);
    }
    public void removeRule(String name){
        rules.remove(name);
    }
    public void scan(Collection<Role> roles,RunConfigBuilder configBuilder){
        roles.forEach(role -> {
            role.getDeclaredHosts().forEach(host -> {
                role.getSetup().forEach(scriptCmd -> this.private_walk(new CmdLocation(role.getName(),Stage.Setup,scriptCmd.getName(),host.toString(), CmdLocation.Position.Child),scriptCmd,configBuilder,new Cmd.Ref(scriptCmd)));
            });
        });
        roles.forEach(role -> {
            role.getDeclaredHosts().forEach(host -> {
                role.getRun().forEach(scriptCmd -> this.private_walk(new CmdLocation(role.getName(),Stage.Run,scriptCmd.getName(),host.toString(), CmdLocation.Position.Child),scriptCmd,configBuilder,new Cmd.Ref(scriptCmd)));
            });
        });
        roles.forEach(role -> {
            role.getDeclaredHosts().forEach(host -> {
                role.getCleanup().forEach(scriptCmd -> this.private_walk(new CmdLocation(role.getName(),Stage.Cleanup,scriptCmd.getName(),host.toString(), CmdLocation.Position.Child),scriptCmd,configBuilder,new Cmd.Ref(scriptCmd)));
            });
        });
        close(configBuilder,this);
    }

    public void addWarning(String role, Stage stage,String script, String command, String message) {
        RunError error = new RunError(role,stage,script,command,message);
        warnings.add(error);
    }
    public void addError(String role, Stage stage,String script, String command, String message) {
        RunError error = new RunError(role,stage,script,command,message);
        errors.add(error);
    }
    public boolean hasWarnings(){return !warnings.isEmpty();}
    public List<RunError> getWarnings(){return warnings;}
    public boolean hasErrors(){return !errors.isEmpty();}
    public List<RunError> getErrors(){return errors;}
    private void private_walk(CmdLocation location, Cmd command, RunConfigBuilder config, Cmd.Ref ref){
        command.walk(location,(cmd, cmdLocation)->{
            scan(cmdLocation, cmd, ref,config,this);
            return true;
        });
    }
    @Override
    public void scan(CmdLocation location, Cmd command, Cmd.Ref ref, RunConfigBuilder config, RunSummary summary) {
        rules.values().forEach(rule->{
            rule.scan(location, command, ref,config,this);
        });
        if (command instanceof ScriptCmd) {
            ScriptCmd scriptCmd = (ScriptCmd)command;
            String scriptName = scriptCmd.getName();
            scriptName = Cmd.populateStateVariables(scriptName,command,config.getState(),null,null);
            if(Cmd.hasStateReference(scriptName,command)){
                //TODO warn that we cannot
                addWarning(location.getRoleName(),location.getStage(),location.getScriptName(),command.toString(),"could not fully populate script reference "+scriptName+". ");
            }else {
                Script namedScript = config.getScript(scriptName, command);
                if (namedScript == null) {
                    //assume is it an error if a script isn't found?
                    addError(
                            location.getRoleName(),
                            location.getStage(),
                            location.getScriptName(),
                            command.toString(),
                            "missing script: " + scriptName);
                } else {

                    //check if we've already scanned this namedScript.
                    Cmd parentWalk = command;
                    boolean isSelfReferencing = false;
                    CmdLocation childLocation = location.newPosition(CmdLocation.Position.Child);
                    do{
                        isSelfReferencing = parentWalk instanceof Script && ((Script)parentWalk).getName().equals(scriptName);
                    }while (!isSelfReferencing && (parentWalk = parentWalk.getStateParent())!= null);

                    if(isSelfReferencing ){
                        //TODO do we warn about self-referencing scripts?
                    } else {
                        Cmd target = namedScript.deepCopy();
                        target.setStateParent(command); //to maintain reference to with's from
                        CmdLocation scriptLocation = scriptCmd.isAsync() ? location.newPosition(CmdLocation.Position.Child) : location;
                        private_walk(scriptLocation, target, config, ref.add(command));
                    }
                }
            }
        } else if (command instanceof InvokeCmd) {
            Cmd invokedCmd = ((InvokeCmd) command).getCommand().deepCopy();
            invokedCmd.setStateParent(command);
            private_walk(location,invokedCmd,config,ref.add(command));
        }
    }

    @Override
    public void close(RunConfigBuilder config, RunSummary summary) {
        rules.values().forEach(rule->rule.close(config,summary));
    }
}
