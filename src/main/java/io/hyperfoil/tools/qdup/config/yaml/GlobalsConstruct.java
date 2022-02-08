package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.Globals;
import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.JsSnippet;
import io.hyperfoil.tools.yaup.json.Json;
import io.hyperfoil.tools.yaup.yaml.DeferableConstruct;
import io.hyperfoil.tools.yaup.yaml.OverloadConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;

public class GlobalsConstruct extends DeferableConstruct {
    @Override
    public Object construct(Node node) {

        Globals globals = new Globals();
        if(node instanceof MappingNode){
            ((MappingNode)node).getValue().forEach(globalTuple->{
                if(globalTuple.getKeyNode() instanceof ScalarNode){
                    Node valueNode = globalTuple.getValueNode();
                    switch (((ScalarNode) globalTuple.getKeyNode()).getValue()){
                        case "javascript":
                            globals.addSnippet((JsSnippet) deferAs(valueNode,new Tag(JsSnippet.class)));
                            break;
                        case "settings":
                            if(valueNode instanceof MappingNode){
                                Json settings = OverloadConstructor.json(valueNode);
                                settings.forEach((k,v)->{
                                    globals.addSetting(k.toString(),v);
                                });
                            }else{
                                throw new YAMLException("settings must be a mapping"+valueNode.getStartMark());
                            }
                            break;

                        default:
                            throw new YAMLException("unknown yaml tag"+((ScalarNode)globalTuple.getKeyNode()).getStartMark());
                    }
                }else{
                    throw new YAMLException("role names must be scalar"+globalTuple.getKeyNode().getStartMark());
                }
            });
        } else {
            throw new YAMLException("globals requires a mapping");
        }

        return globals;
    }
}
