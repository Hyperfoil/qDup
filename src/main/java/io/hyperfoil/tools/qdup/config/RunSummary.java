package io.hyperfoil.tools.qdup.config;

import io.hyperfoil.tools.qdup.Stage;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Script;
import io.hyperfoil.tools.qdup.cmd.impl.InvokeCmd;
import io.hyperfoil.tools.qdup.cmd.impl.ScriptCmd;

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
                role.getSetup().forEach(c -> this.walk(role.getName(), Stage.Setup,c.getName(),host.toString(),c,Location.Normal,configBuilder,new Cmd.Ref(c)));
                role.getRun().forEach(c -> this.walk(role.getName(), Stage.Run,c.getName(),host.toString(),c,Location.Normal,configBuilder,new Cmd.Ref(c)));
                role.getCleanup().forEach(c -> this.walk(role.getName(), Stage.Cleanup,c.getName(),host.toString(),c,Location.Normal,configBuilder,new Cmd.Ref(c)));
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
    private void walk(String role, Stage stage, String script, String host, Cmd command, Location location, RunConfigBuilder config, Cmd.Ref ref){
        command.walk(location,(cmd, watching)->{
            scan(role,stage,script,host,cmd,watching,ref,config,this);
            return true;
        });
    }
    @Override
    public void scan(String role, Stage stage, String script, String host, Cmd command, Location location, Cmd.Ref ref, RunConfigBuilder config, RunSummary summary) {
        rules.values().forEach(rule->{
            rule.scan(role,stage,script,host,command, location,ref,config,this);
        });
        if (command instanceof ScriptCmd) {
            String scriptName = ((ScriptCmd) command).getName();
            scriptName = Cmd.populateStateVariables(scriptName,command,config.getState(),null,null);
            if(Cmd.hasStateReference(scriptName,command)){
                //TODO warn that we cannot
            }else {
                Script namedScript = config.getScript(scriptName, command);
                if (namedScript == null) {
                    addError(
                            role,
                            stage,
                            script,
                            command.toString(),
                            "missing script: " + scriptName);
                    //TODO is it an error if a script isn't found?
                } else {
                    Cmd target = namedScript.deepCopy();
                    target.setStateParent(command); //to maintain reference to with's from
                    walk(role, stage, script, host, target, location, config, ref.add(command));
                }
            }
        } else if (command instanceof InvokeCmd) {
            Cmd invokedCmd = ((InvokeCmd) command).getCommand().deepCopy();
            invokedCmd.setStateParent(command);

            walk(role,stage,script,host,invokedCmd, location,config,ref.add(command));
        }
    }

    @Override
    public void close(RunConfigBuilder config, RunSummary summary) {
        rules.values().forEach(rule->rule.close(config,summary));
    }
}
