package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Script;
import io.hyperfoil.tools.qdup.cmd.impl.ScriptCmd;
import io.hyperfoil.tools.qdup.config.Role;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.*;
import io.hyperfoil.tools.yaup.yaml.DeferableConstruct;
import io.hyperfoil.tools.yaup.yaml.Mapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class YamlFileConstruct extends DeferableConstruct {

    public static final Mapping<YamlFile> MAPPING = (yaml)->{
        Map<Object,Object> map = new LinkedHashMap<>();
        if(yaml.getName()!=null && !yaml.getName().isEmpty()) {
            map.put("name", yaml.getName());
        }
        if(!yaml.getScripts().isEmpty()){
            Map<Object,Object> scriptMap = new LinkedHashMap<>();
            yaml.getScripts().forEach((name,script)->{
                scriptMap.put(name,script.getThens());
            });
            map.put("scripts",scriptMap);
        }
        if(!yaml.getHosts().isEmpty()){
            Map<Object,Object> hostMap = new LinkedHashMap<>();
            yaml.getHosts().forEach((alias,host)->{
                hostMap.put(alias,host.toString());
            });
            map.put("hosts",hostMap);
        }
        Function<List<? extends Cmd>,List<Object>> toScriptMap = (scripts)->{
            return scripts.stream().map(cmd->{
                Map<Object,Object> cmdMap = new LinkedHashMap<>();
                CmdMapping cmdMapping = new CmdMapping("cmd",null);
                Map<Object,Object> mapped = cmdMapping.getMap(cmd);
                if(mapped.isEmpty()){
                    return ((ScriptCmd)cmd).getName();
                }else {
                    cmdMap.put(((ScriptCmd) cmd).getName(), mapped);
                    return cmdMap;
                }
            }).collect(Collectors.toList());
        };
        if(!yaml.getRoles().isEmpty()){
            Map<Object,Object> rolesMap = new LinkedHashMap<>();
            yaml.getRoles().forEach((name,role)->{
                Map<Object,Object> roleMap = new LinkedHashMap<>();
                rolesMap.put(name,roleMap);
                if(!role.getHostRefs().isEmpty()){
                    roleMap.put("hosts",new ArrayList<>(role.getHostRefs()));
                }
                if(!role.getSetup().isEmpty()){
                    roleMap.put("setup-scripts",new ArrayList<Object>(toScriptMap.apply(role.getSetup())));
                }
                if(!role.getRun().isEmpty()){
                    roleMap.put("run-scripts",new ArrayList<Object>(toScriptMap.apply(role.getRun())));
                }
                if(!role.getCleanup().isEmpty()){
                    roleMap.put("cleanup-scripts",new ArrayList<Object>(toScriptMap.apply(role.getCleanup())));
                }
            });
            map.put("roles",rolesMap);
        }
        if(!yaml.getState().allKeys().isEmpty()){
            map.put("states",yaml.getState());
        }
        return map;
    };
    private final RoleConstruct roleConstruct;

    public YamlFileConstruct(RoleConstruct roleConstruct){
        this.roleConstruct = roleConstruct;
    }
    @Override
    public Object construct(Node node) {
        if(node instanceof MappingNode) {
            MappingNode mappingNode = (MappingNode)node;
            YamlFile yamlFile = new YamlFile();

            mappingNode.getValue().forEach(nodeTuple -> {
                if(!( nodeTuple.getKeyNode() instanceof ScalarNode) ){
                    throw new YAMLException("YamlFile keys must be scalar "+nodeTuple.getKeyNode().getStartMark());
                }
                String key = ((ScalarNode)nodeTuple.getKeyNode()).getValue();
                Node valueNode = nodeTuple.getValueNode();
                switch (key){
                    case "name":
                        if(!(valueNode instanceof ScalarNode)){
                            throw new YAMLException("name must be scalar "+valueNode.getStartMark());
                        }
                        yamlFile.setName(((ScalarNode)valueNode).getValue());
                        break;
                    case "hosts":
                        if(valueNode instanceof MappingNode){
                            MappingNode valueMapping = (MappingNode)valueNode;
                            valueMapping.getValue().forEach(valueTuple->{
                                String hostAlias = ((ScalarNode)valueTuple.getKeyNode()).getValue();
                                Node hostNode = valueTuple.getValueNode();
                                hostNode.setTag(new Tag("host"));
                                Object hostObj = defer(hostNode);
                                if(hostObj instanceof Host){
                                    yamlFile.addHost(hostAlias,(Host)hostObj);
                                }
                            });
                        }else if( !(valueNode instanceof ScalarNode) || !((ScalarNode)valueNode).getValue().isEmpty() ){
                            //if it is not an empty scalar
                            throw new YAMLException("hosts cannot parse "+valueNode.getStartMark());
                        }
                        break;
                    case "states":
                        Object newState = deferAs(valueNode,new Tag("states"));
                        if(newState instanceof State){
                            yamlFile.getState().merge((State)newState);
                        }else{
                            throw new YAMLException("states created a "+newState.getClass().getSimpleName()+" from "+valueNode.getStartMark());
                        }
                        break;
                    case "scripts":
                        if(valueNode instanceof MappingNode){
                            ((MappingNode)valueNode).getValue().forEach(scriptTuple->{
                                if(scriptTuple.getKeyNode() instanceof ScalarNode){
                                    String scriptName = ((ScalarNode)scriptTuple.getKeyNode()).getValue();
                                    Script script = new Script(scriptName);
                                    if(scriptTuple.getValueNode() instanceof SequenceNode){
                                        ((SequenceNode)scriptTuple.getValueNode()).getValue().forEach(cmdNode->{
                                            Object loaded = cmdNode instanceof ScalarNode ? deferAs(cmdNode,new Tag("cmd")) : defer(cmdNode);
                                            if(loaded == null){
                                                throw new YAMLException("could not create a cmd from "+cmdNode.getClass().getSimpleName()+cmdNode.getStartMark());
                                            }
                                            if(loaded instanceof Cmd){
                                                script.then((Cmd)loaded);
                                            }else{
                                                throw new YAMLException("failed to create Cmd"+cmdNode.getStartMark());
                                            }
                                        });
                                    }else{
                                        throw new YAMLException("script '"+scriptName+"' value must be a sequence"+scriptTuple.getValueNode().getStartMark());
                                    }
                                    yamlFile.addScript(scriptName,script);
                                }else{
                                    throw new YAMLException("script key must be scalar"+scriptTuple.getKeyNode().getStartMark());
                                }
                            });
                        }
                        break;
                    case "roles":
                        if(valueNode instanceof MappingNode){
                            ((MappingNode)valueNode).getValue().forEach(roleTuple->{
                                if(roleTuple.getKeyNode() instanceof ScalarNode){
                                    String roleName = ((ScalarNode)roleTuple.getKeyNode()).getValue();
                                    Role role = new Role(roleName);
                                    roleConstruct.populate(role,roleTuple.getValueNode());
                                    yamlFile.addRole(roleName,role);
                                }else{
                                    throw new YAMLException("role names must be scalar"+roleTuple.getKeyNode().getStartMark());
                                }
                            });
                        }else if (valueNode instanceof SequenceNode){
                            ((SequenceNode)valueNode).getValue().forEach(roleNode->{
                                Object loaded = deferAs(roleNode,new Tag(Role.class));
                                if(loaded!=null && loaded instanceof Role){
                                    Role role = (Role)loaded;
                                    yamlFile.addRole(role.getName(),role);
                                }
                            });
                        } else {
                            throw new YAMLException("roles requires a mapping");
                        }
                        break;
                    default:
                        throw new YAMLException("unknown yaml tag"+((ScalarNode)nodeTuple.getKeyNode()).getStartMark());
                }
            });
            return yamlFile;
        }
        throw new YAMLException("YamlFile needs to be a map but encountered "+node.getStartMark());
    }
}
