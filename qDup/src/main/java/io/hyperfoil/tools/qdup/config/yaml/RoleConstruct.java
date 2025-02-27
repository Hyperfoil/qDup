package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.cmd.impl.ScriptCmd;
import io.hyperfoil.tools.qdup.config.HostExpression;
import io.hyperfoil.tools.qdup.config.Role;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.*;
import io.hyperfoil.tools.yaup.yaml.DeferableConstruct;
import io.hyperfoil.tools.yaup.yaml.Mapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class RoleConstruct extends DeferableConstruct {

    public static Mapping<Role> MAPPING = (role)->{
        Map<Object,Object> rtrn = new LinkedHashMap<>();
        if(!role.getName().isEmpty()){
            rtrn.put("name",role.getName());
        }
        if(role.hasHostExpression()){
            rtrn.put("hosts",role.getHostExpression().getExpression());
        }else{
            rtrn.put("hosts",role.getHostRefs());
        }
        rtrn.put("setup-scripts",role.getSetup());
        rtrn.put("run-scripts",role.getRun());
        rtrn.put("cleanup-scripts",role.getCleanup());
        return rtrn;
    };

    ScriptCmdConstruct scriptCmdConstruct = new ScriptCmdConstruct();
    BiFunction<String,Node, List<ScriptCmd>> parseScript = (section, tupleValue)->{
        List<ScriptCmd> scriptCmds = new ArrayList<>();
        if (tupleValue instanceof SequenceNode) {
            ((SequenceNode) tupleValue).getValue().forEach(scriptNode -> {
                Object loaded = scriptCmdConstruct.construct(scriptNode);
                if (loaded != null && loaded instanceof ScriptCmd) {
                    scriptCmds.add((ScriptCmd) loaded);
                } else {
                    //this should only happen after a YAMLException from scriptCmdConstruct
                    throw new YAMLException("failed to parse " + section + " script reference" + scriptNode.getStartMark());
                }
            });
        }else if (tupleValue instanceof ScalarNode && ((ScalarNode)tupleValue).getValue().trim().isBlank()){
            //this is ok, an empty scalar can be treated as an empty list
        }else{
            throw new YAMLException(section+" must be a sequence"+tupleValue.getStartMark());
        }
        return scriptCmds;
    };


    public void populate(Role role,Node node){
        if(node instanceof MappingNode){
            MappingNode mappingNode = (MappingNode)node;
            mappingNode.getValue().forEach(nodeTuple -> {
                if(nodeTuple.getKeyNode() instanceof ScalarNode){
                    String key = ((ScalarNode)nodeTuple.getKeyNode()).getValue();
                    Node tupleValue = nodeTuple.getValueNode();
                    switch (key){
                        case "hosts":
                            if(tupleValue instanceof ScalarNode){
                                //TODO parse RoleHostExpression
                                HostExpression hostExpression = new HostExpression(((ScalarNode)tupleValue).getValue());
                                role.setHostExpression(hostExpression);
                            }else if(tupleValue instanceof SequenceNode){
                                ((SequenceNode)tupleValue).getValue().forEach(hostNode-> {
                                    if(hostNode instanceof ScalarNode) {
                                        role.addHostRef(((ScalarNode) hostNode).getValue());
                                    }else if (hostNode instanceof MappingNode){
                                        Object loaded = deferAs(hostNode,new Tag(Host.class));
                                        if(loaded instanceof Host){
                                            role.addHost((Host)loaded);
                                        }else{
                                            throw new YAMLException("role '"+role.getName()+"' failed to load host"+hostNode.getStartMark());
                                        }
                                    }else{
                                        throw new YAMLException("role '"+role.getName()+"' host needs to be scalar or mapping"+hostNode.getStartMark());
                                    }
                                });
                            }else{
                                throw new YAMLException("role '"+role.getName()+"' hosts must be a sequence"+tupleValue.getStartMark());
                            }
                            break;
                        case "run-scripts":
                            parseScript.apply("run-scripts",tupleValue).forEach(role::addRun);
                            break;
                        case "setup-scripts":
                            parseScript.apply("setup-scripts",tupleValue).forEach(role::addSetup);
                            break;
                        case "cleanup-scripts":
                            parseScript.apply("cleanup-scripts",tupleValue).forEach(role::addCleanup);
                            break;
                        default:
                            throw new YAMLException("unknown role key '"+key+"' "+nodeTuple.getKeyNode().getStartMark());

                    }
                }else{
                    throw new YAMLException("role '"+role.getName()+"' keys must be scalar"+nodeTuple.getKeyNode().getStartMark());
                }
            });
        }else{
            throw new YAMLException("role '"+role.getName()+"' value must be a mapping"+node.getStartMark());
        }

    }
    @Override
    public Object construct(Node node) {
        if(node instanceof MappingNode && ((MappingNode)node).getValue().size()==1){
            NodeTuple tuple = ((MappingNode)node).getValue().get(0);
            if(tuple.getKeyNode() instanceof ScalarNode){
                String roleName = ((ScalarNode)tuple.getKeyNode()).getValue();
                Role role = new Role(roleName);
                populate(role,tuple.getValueNode());
                return role;
            }else{
                throw new YAMLException("roles require scalar name key"+tuple.getKeyNode().getStartMark());
            }
        }else{
            throw new YAMLException("roles requires mapping node"+node.getStartMark());
        }
        //throw new YAMLException("failed to create role"+node.getStartMark());
    }
}
