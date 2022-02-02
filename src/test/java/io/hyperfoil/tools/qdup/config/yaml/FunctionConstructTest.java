package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.JsFunction;
import io.hyperfoil.tools.yaup.yaml.OverloadConstructor;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;

import static org.junit.Assert.assertNotNull;

public class FunctionConstructTest {

    static Yaml yaml = null;

    @BeforeClass
    public static void setupYaml(){
        OverloadConstructor constructor = new  OverloadConstructor();
        JsFunctionConstruct jsFunctionConstruct = new JsFunctionConstruct();
        constructor.addConstruct(new Tag(JsFunction.class),jsFunctionConstruct);
        yaml = new Yaml(constructor);
    }

    @Test
    public void key_value(){
        JsFunction loaded = yaml.loadAs(
                "function argsMapper(args) {\n" +
                "  return args.split(' ').map(optionsFilter).filter(nullFilter);\n" +
                "}\n"
                , JsFunction.class);
        assertNotNull("should load states",loaded);
    }


}
