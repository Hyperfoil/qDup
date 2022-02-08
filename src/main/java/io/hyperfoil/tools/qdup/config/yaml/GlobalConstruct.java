package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.Global;
import io.hyperfoil.tools.qdup.JsSnippet;
import io.hyperfoil.tools.yaup.yaml.DeferableConstruct;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;

public class GlobalConstruct extends DeferableConstruct {
    @Override
    public Object construct(Node node) {

        Global global = new Global();
        if(node instanceof MappingNode){
            ((MappingNode)node).getValue().forEach(globalTuple->{
                if(globalTuple.getKeyNode() instanceof ScalarNode){
                    switch (((ScalarNode) globalTuple.getKeyNode()).getValue()){
                        case "javascript":
                            global.addSnippet(new JsSnippet(((ScalarNode) globalTuple.getValueNode()).getValue()));
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

        return global;
    }
}
