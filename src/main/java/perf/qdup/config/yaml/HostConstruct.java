package perf.qdup.config.yaml;

import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import perf.qdup.Host;
import perf.yaup.Sets;
import perf.yaup.json.Json;
import perf.yaup.yaml.DeferableConstruct;

import java.util.Set;

import static perf.yaup.yaml.OverloadConstructor.json;

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
            Set<Object> extra = Sets.unique(json.keys(),Sets.of("hostnme","username","port"));
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
