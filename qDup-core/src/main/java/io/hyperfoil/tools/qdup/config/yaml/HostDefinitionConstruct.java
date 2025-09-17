package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.Host;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import io.hyperfoil.tools.yaup.Sets;
import io.hyperfoil.tools.yaup.json.Json;
import io.hyperfoil.tools.yaup.yaml.DeferableConstruct;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.hyperfoil.tools.yaup.yaml.OverloadConstructor.json;

public class HostDefinitionConstruct extends DeferableConstruct {

    public HostDefinitionConstruct(){
        super();
    }
    @Override
    public Object construct(Node node) {
        HostDefinition rtrn = null;
        if(node instanceof ScalarNode){
            rtrn = new HostDefinition(((ScalarNode)node).getValue());
        }else if (node instanceof MappingNode){
            Json json = json(node);
            List<String> unknown = HostDefinition.unknownKeys(json.keys().stream().map(Object::toString).collect(Collectors.toList()));
            if(!unknown.isEmpty()){
                throw new YAMLException("Unknown host attributes: "+unknown+node.getStartMark());
            }
            rtrn = new HostDefinition(json);
        }
        if(rtrn==null){
            throw new YAMLException("Failed to construct host from "+node.getStartMark());
        }
        return rtrn;

    }
}
