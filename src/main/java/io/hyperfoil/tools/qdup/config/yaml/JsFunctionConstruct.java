package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.JsFunction;
import io.hyperfoil.tools.yaup.yaml.DeferableConstruct;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;

public class JsFunctionConstruct extends DeferableConstruct {
    @Override
    public Object construct(Node node) {
        JsFunction rtrn = null;

        if (node instanceof ScalarNode) {
            rtrn = new JsFunction(((ScalarNode) node).getValue());

        } else {
            throw new YAMLException("function must be loaded from a scalar " + node.getClass() + " " + node.getStartMark());
        }
        return rtrn;
    }
}
