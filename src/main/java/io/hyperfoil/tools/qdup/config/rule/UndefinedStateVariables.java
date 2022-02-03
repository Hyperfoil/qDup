package io.hyperfoil.tools.qdup.config.rule;

import io.hyperfoil.tools.qdup.Coordinator;
import io.hyperfoil.tools.qdup.Global;
import io.hyperfoil.tools.qdup.SecretFilter;
import io.hyperfoil.tools.qdup.Stage;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.impl.ForEach;
import io.hyperfoil.tools.qdup.cmd.impl.Regex;
import io.hyperfoil.tools.qdup.cmd.impl.SetState;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.RunRule;
import io.hyperfoil.tools.qdup.config.RunSummary;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import io.hyperfoil.tools.yaup.HashedLists;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
 Find any ${{foo}} where foo is not previously set or available from state or withs
 */
public class UndefinedStateVariables implements RunRule {

    private HashedLists<String, RSSCRef> usedVariables;
    private HashedLists<String, RSSCRef> setVariables;
    private HashedLists<String, RSSCRef> neededVariables;
    private Parser parser;
    private Set<String> ignore;

    private List<String> jsFunctions;

    public UndefinedStateVariables(Parser parser){
        this(parser,new HashSet<>());
    }

    public UndefinedStateVariables(Parser parser, Collection<String> ignore) {
        this(parser, ignore, new ArrayList<>());
    }

    public UndefinedStateVariables(Parser parser, List<String> jsFunctions) {
        this(parser, new HashSet<>(), jsFunctions);
    }

    public UndefinedStateVariables(Parser parser, Collection<String> ignore, List<String> jsFunctions) {

        this.parser = parser;
        usedVariables = new HashedLists<>();
        setVariables = new HashedLists<>();
        neededVariables = new HashedLists<>();
        this.ignore = new HashSet<>(ignore);
        this.jsFunctions = jsFunctions;
    }

    public void addIgnore(String name){
        String trimmed = trim(name);
        ignore.add(trimmed);
    }
    public boolean hasIgnore(String name){
        String trimmed = trim(name);
        return
            ignore.contains(trimmed) ||
            ignore.stream()
                .filter(var -> trimmed.startsWith(var))
                .findAny().orElse(null) != null;
    }
    public Set<String> getIgnore(){return ignore;}
    public Set<String> getUsedVariables(){return usedVariables.keys();}
    public Set<String> getSetVariables(){return setVariables.keys();}
    private void addUsedVariable(String name, RSSCRef ref) {
        String trimmed = trim(name);
        usedVariables.put(trimmed, ref);
    }

    private void addSetVariable(String name, RSSCRef ref, RunSummary summary) {
        String trimmed = trim(name);
        if(!hasSetVariable(trimmed) && hasNeedVariable(trimmed)){
            neededVariables.get(trimmed).stream().filter(rssc->{
                return
                    (
                        ref.isSameScript(rssc) ||
                        rssc.isBeforeOrSequentiallyWith(ref)
                    )
                    &&
                    !(
                        RunConfigBuilder.SCRIPT_DIR.equals(trimmed) ||
                        RunConfigBuilder.TEMP_DIR.equals(trimmed) ||
                        trimmed.startsWith(SecretFilter.SECRET_NAME_PREFIX)
                    );
            }).forEach(rssc->{
                summary.addError(
                    rssc.getRole(),
                    rssc.getStage(),
                    rssc.getScript(),
                    rssc.getCommand().toString(),
                    trimmed+" used without default before it is set"
                );
            });
        }
        setVariables.put(trimmed, ref);
    }

    public String trim(String name){
        String rtrn = name;
        if(rtrn.startsWith(SecretFilter.SECRET_NAME_PREFIX)){
            rtrn = rtrn.substring(SecretFilter.SECRET_NAME_PREFIX.length());
        }
        rtrn = State.removeStatePrefix(rtrn);
        //if rtrn is a jsonpath we only concern ourselves with the start
        if(rtrn.contains("?(")){
            rtrn = rtrn.substring(0,rtrn.indexOf("?("));
        }
        if(rtrn.contains("..")){
            rtrn = rtrn.substring(0,rtrn.indexOf(".."));
        }
        if(rtrn.endsWith("[") || rtrn.endsWith(".")){
            rtrn = rtrn.substring(0,rtrn.length()-1);
        }

        return rtrn;
    }

    private void addNeededVariable(String name, RSSCRef ref) {
        String trimmed = trim(name);
        if(
            RunConfigBuilder.TEMP_DIR.equals(trimmed) ||
            RunConfigBuilder.SCRIPT_DIR.equals(trimmed) ||
            hasSetVariable(trimmed)
        )
        {

        } else {
            neededVariables.put(trimmed, ref);
        }
    }
    private boolean hasNeedVariable(String name){
        String trimmed = trim(name);
        return neededVariables.containsKey(trimmed);
    }
    private boolean hasSetVariable(String name) {
        String trimmed = trim(name);
        return
            setVariables.containsKey(trimmed) ||
                setVariables.keys().stream()
                    .filter(var -> {
                        return trimmed.equals(var) ||
                        (trimmed.startsWith(var)  && trimmed.length() > var.length() && ".[".contains(""+trimmed.charAt(var.length()))) ||
                        (var.startsWith(trimmed) && var.length() > trimmed.length() && ".[".contains(""+var.charAt(trimmed.length())) );
                    })
                    .findAny().orElse(null) != null;
    }

    @Override
    public void scan(String role, Stage stage, String script, String host, Cmd command, Location location, Cmd.Ref ref, RunConfigBuilder config, RunSummary summary) {

        if(!command.isStateScan()){
            return;
        }
        String commandStr = parser != null ? parser.dump(parser.representCommand(command)) : command.toString();
        if (Cmd.hasStateReference(commandStr, command)) {
            RSSCRef rssc = new RSSCRef(
                    role,
                    stage,
                    script,
                    command
            );
            Cmd.getStateVariables(commandStr,command, config.getState(),null,null,ref).forEach(v->addUsedVariable(v,rssc));
            command.loadAllWithDefs(config.getState(),null);
            ref.loadAllWithDefs(config.getState(),null);
            Coordinator dummyCoordinator = new Coordinator(new Global(this.jsFunctions));
            String populated = Cmd.populateStateVariables(commandStr, command, config.getState(), dummyCoordinator, null, ref);
            if (Cmd.hasStateReference(populated, command)) {

                List<String> neededVariables = Cmd.getStateVariables(populated, command, config.getState(), null, null,ref);
                neededVariables
                    .stream()
                    .filter(var->{boolean rtrn = !(
                        command.hasWith(var) ||
                        config.getState().has(var)
                    );
                    return rtrn;
                    })
                    .forEach(neededVariable -> {
                        addNeededVariable(
                            neededVariable,
                            new RSSCRef(
                                role,
                                stage,
                                script,
                                command
                            )
                        );
                    });
            }
        }
        if (command instanceof Regex) {
            RSSCRef rssc = new RSSCRef(
                role,
                stage,
                script,
                command
            );
            ((Regex) command).getCaptureNames().forEach(captureName->{
                String toUse = captureName;
                if(Cmd.hasStateReference(toUse,command)){
                    toUse = Cmd.populateStateVariables(captureName,command, config.getState(), null, null, ref);
                    if(Cmd.hasStateReference(toUse,command)){
                        List<String> references = Cmd.getStateVariables(toUse,command, config.getState(), null, null, ref);
                    }
                }
                addSetVariable(toUse,rssc,summary);
            });
        } else if (command instanceof SetState) {
            RSSCRef rssc = new RSSCRef(
                role,
                stage,
                script,
                command
            );
            String key = ((SetState) command).getKey();
            String value = ((SetState) command).getValue();
            if(Cmd.hasStateReference(key,command)){
                key = Cmd.populateStateVariables(((SetState) command).getKey(), command, config.getState(), null, null, ref);
            }
            addSetVariable(key,rssc,summary);
        } else if (command instanceof ForEach){
            RSSCRef rssc = new RSSCRef(
                    role,
                    stage,
                    script,
                    command
            );
            String key = ((ForEach)command).getName();
            String value = ((ForEach)command).getDeclaredInput();
            if(Cmd.hasStateReference(key,command)){
                Cmd.getStateVariables(key,command, config.getState(), null, null, ref).forEach(v->addUsedVariable(v,rssc));
                key = Cmd.populateStateVariables(((SetState) command).getKey(), command, config.getState(), null, null, ref);
            }
            if(Cmd.hasStateReference(value,command)){
                Cmd.getStateVariables(value,command, config.getState(), null, null, ref).forEach(v->addUsedVariable(v,rssc));
            }
            addSetVariable(key,rssc,summary);
        }
    }

    @Override
    public void close(RunConfigBuilder config, RunSummary summary) {
        neededVariables.keys().stream()
            .filter(key->!hasSetVariable(key))
            .forEach(key->{
                neededVariables.get(key).forEach(ref->{
                    summary.addError(
                        ref.getRole(),
                        ref.getStage(),
                        ref.getScript(),
                        ref.getCommand().toString(),
                        key+" is used before it appears to be set"
                    );
                });
            });
    }
}
