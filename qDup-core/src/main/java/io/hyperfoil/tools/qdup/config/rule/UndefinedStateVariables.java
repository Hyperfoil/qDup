package io.hyperfoil.tools.qdup.config.rule;

import io.hyperfoil.tools.qdup.Coordinator;
import io.hyperfoil.tools.qdup.Globals;
import io.hyperfoil.tools.qdup.JsSnippet;
import io.hyperfoil.tools.qdup.SecretFilter;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.PatternValuesMap;
import io.hyperfoil.tools.qdup.cmd.impl.ForEach;
import io.hyperfoil.tools.qdup.cmd.impl.Regex;
import io.hyperfoil.tools.qdup.cmd.impl.SetState;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.RunRule;
import io.hyperfoil.tools.qdup.config.RunSummary;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import io.hyperfoil.tools.yaup.AsciiArt;
import io.hyperfoil.tools.yaup.HashedLists;
import io.hyperfoil.tools.yaup.PopulatePatternException;
import io.hyperfoil.tools.yaup.StringUtil;
import io.hyperfoil.tools.yaup.json.JsonMap;

import java.util.*;
import java.util.stream.Collectors;

/*
 Find any ${{foo}} where foo is not previously set or available from state or withs
 */
public class UndefinedStateVariables implements RunRule {

    private HashedLists<String, RSSCRef> usedVariables;
    private HashedLists<String, RSSCRef> setVariables;
    private HashedLists<String, RSSCRef> neededVariables;

    private List<String> missingVariables;
    private Parser parser;
    private Set<String> ignore;

    private List<JsSnippet> jsSnippets;

    public UndefinedStateVariables(Parser parser){
        this(parser,new HashSet<>());
    }

    public UndefinedStateVariables(Parser parser, Collection<String> ignore) {
        this(parser, ignore, new ArrayList<>());
    }

    public UndefinedStateVariables(Parser parser, List<JsSnippet> jsSnippets) {
        this(parser, new HashSet<>(), jsSnippets);
    }

    public UndefinedStateVariables(Parser parser, Collection<String> ignore, List<JsSnippet> jsSnippets) {

        this.parser = parser;
        usedVariables = new HashedLists<>();
        setVariables = new HashedLists<>();
        neededVariables = new HashedLists<>();
        missingVariables = new ArrayList<>();
        this.ignore = new HashSet<>(ignore);
        this.jsSnippets = jsSnippets;
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
    public List<String> getMissingVariables(){return Collections.unmodifiableList(missingVariables);}
    public void clearMissingVariables(){missingVariables.clear();}
    private void addMissingVariable(String variable){
        missingVariables.add(variable);
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
    public void scan(CmdLocation location, Cmd command, Cmd.Ref ref, RunConfigBuilder config, RunSummary summary) {
        if(!command.isStateScan()){
            return;
        }
        String commandStr = parser != null ? parser.dump(parser.representCommand(command)) : command.toString();
        if (command instanceof Regex) {
            RSSCRef rssc = new RSSCRef(
                    location.getRoleName(),
                    location.getStage(),
                    location.getScriptName(),
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
                    location.getRoleName(),
                    location.getStage(),
                    location.getScriptName(),
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
                    location.getRoleName(),
                    location.getStage(),
                    location.getScriptName(),
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
        if (Cmd.hasStateReference(commandStr, command)) {
            RSSCRef rssc = new RSSCRef(
                    location.getRoleName(),
                    location.getStage(),
                    location.getScriptName(),
                    command
            );
            Cmd.getStateVariables(commandStr,command, config.getState(),null,null,ref).forEach(v->addUsedVariable(v,rssc));
            command.loadAllWithDefs(config.getState(),null);
            ref.loadAllWithDefs(config.getState(),null);
            Coordinator dummyCoordinator = new Coordinator(new Globals(this.jsSnippets));
            String populated = Cmd.populateStateVariables(commandStr, command, config.getState(), dummyCoordinator, null, ref, true);
            if (Cmd.hasStateReference(populated, command)) {

                List<String> cmdNeededVariables = Cmd.getStateVariables(populated, command, config.getState(), null, null,ref);
                cmdNeededVariables
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
                                location.getRoleName(),
                                location.getStage(),
                                location.getScriptName(),
                                command
                            )
                        );
                    });
            }
        }

    }

    @Override
    public void close(RunConfigBuilder config, RunSummary summary) {
        neededVariables.keys().stream()
            //remove needed variables that are already set or start with a set key (for json)
            .filter(key->!hasSetVariable(key))
            //remove entries that use
            .filter(key->{
                if(key.contains("qd_") && key.contains("_qd")){
                    PatternValuesMap map = new PatternValuesMap(null,config.getState(),null,null,null);
                    List<String> missing = null;
                    try {
                        List<String> found = StringUtil.getPatternNames(key,
                        map,
                                (o)->(o instanceof String) ? StringUtil.PATTERN_PREFIX+o.toString()+StringUtil.PATTERN_SUFFIX : o,
                                "qd_",
                        StringUtil.PATTERN_DEFAULT_SEPARATOR,
                        "_qd",
                        StringUtil.PATTERN_JAVASCRIPT_PREFIX,
                        true
                        );
                        missing = found.stream().filter(k->!hasSetVariable(k)).collect(Collectors.toList());
                    } catch (PopulatePatternException e) {
                        //what causes a PPE here?
                        e.printStackTrace();
                    }
                    //TODO qDup should warn this reference depends on the runtime values found above
                    return !missing.isEmpty();
                }
                return true;
            })
            .forEach(key->{
                neededVariables.get(key).forEach(ref->{
                    String newKey = key.replaceAll("qd_","\\$\\{\\{").replaceAll("_qd",StringUtil.PATTERN_SUFFIX);
                    addMissingVariable(newKey);
                    summary.addError(
                        ref.getRole(),
                        ref.getStage(),
                        ref.getScript(),
                        ref.getCommand().toString(),
                        newKey+" is used before it appears to be set"
                    );
                });
            });
    }
}
