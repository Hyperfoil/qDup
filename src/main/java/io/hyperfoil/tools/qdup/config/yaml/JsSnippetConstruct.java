package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.JsSnippet;
import io.hyperfoil.tools.yaup.yaml.DeferableConstruct;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;

public class JsSnippetConstruct extends DeferableConstruct {
    @Override
    public Object construct(Node node) {
        JsSnippet rtrn = null;

        if (node instanceof ScalarNode) {
            rtrn = new JsSnippet(((ScalarNode) node).getValue());

        } else {
            throw new YAMLException("Js snippet must be loaded from a scalar " + node.getClass() + " " + node.getStartMark());
        }
        return rtrn;
    }
}
