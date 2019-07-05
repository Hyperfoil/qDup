package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.Host;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import io.hyperfoil.tools.yaup.Sets;
import io.hyperfoil.tools.yaup.json.Json;
import io.hyperfoil.tools.yaup.yaml.DeferableConstruct;

import java.util.Set;

import static io.hyperfoil.tools.yaup.yaml.OverloadConstructor.json;

public class HostConstruct extends DeferableConstruct {
    @Override
    public Object construct(Node node) {
        Host rtrn = null;
        if(node instanceof ScalarNode){
            rtrn = Host.parse(((ScalarNode)node).getValue());
        }else if (node instanceof MappingNode){
            Json json = json(node);
            if(json.has("username") && json.has("hostname")){
                rtrn = new Host(
                        json.getString("username"),
                        json.getString("hostname"),
                        (int)json.getLong("port",Host.DEFAULT_PORT)
                );
            }
            Set<Object> extra = Sets.unique(json.keys(),Sets.of("hostname","username","port"));
            if(!extra.isEmpty()){
                throw new YAMLException("unexpected "+extra+" keys for host "+node.getStartMark());
            }
        }
        if(rtrn==null){
            throw new YAMLException("Failed to construct host from "+node.getStartMark());
        }
        return rtrn;

    }
}
