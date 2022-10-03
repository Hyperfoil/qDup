package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.State;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import io.hyperfoil.tools.yaup.json.Json;
import io.hyperfoil.tools.yaup.yaml.DeferableConstruct;

import static io.hyperfoil.tools.yaup.yaml.OverloadConstructor.json;

public class StateConstruct extends DeferableConstruct {
    @Override
    public Object construct(Node node) {
        State rtrn = new State(State.RUN_PREFIX);
        if(node instanceof ScalarNode && ((ScalarNode)node).getValue().trim().isEmpty()){
            //empty state is fine
        }else if(node instanceof MappingNode){
            Json json = json(node);
            rtrn.load(json,false);
            return rtrn;
        }else {
            throw new YAMLException("state must be loaded from a map " + node.getClass()+" "+node.getStartMark());
        }
        return rtrn;
    }
}
