package io.hyperfoil.tools.qdup.config.yaml;

import com.google.common.collect.Lists;
import io.hyperfoil.tools.qdup.Global;
import io.hyperfoil.tools.qdup.JsSnippet;
import io.hyperfoil.tools.yaup.yaml.OverloadConstructor;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class JsSnippetConstructTest {

    static Yaml yaml = null;

    @BeforeClass
    public static void setupYaml(){
        OverloadConstructor constructor = new  OverloadConstructor();
        JsSnippetConstruct jsFunctionConstruct = new JsSnippetConstruct();
        constructor.addConstruct(new Tag(JsSnippet.class),jsFunctionConstruct);
        yaml = new Yaml(constructor);
    }

    @Test
    public void key_value(){
        JsSnippet loaded = yaml.loadAs(
                "function argsMapper(args) {\n" +
                        "  return args.split(' ').map(optionsFilter).filter(nullFilter);\n" +
                        "}\n" +
                        "function retConst() {\n" +
                        "  return 3;\n" +
                        "}\n"
                , JsSnippet.class);
        assertNotNull("should load states",loaded);
        assertEquals("expecting 2 functions", 2, loaded.getNames().size());
    }

    @Test
    public void name_collision(){
        JsSnippet loaded = yaml.loadAs(
                "function argsMapper(args) {\n" +
                        "  return args.split(' ').map(optionsFilter).filter(nullFilter);\n" +
                        "}\n" +
                        "function argsMapper() {\n" +
                        "  return 3;\n" +
                        "}\n"
                , JsSnippet.class);
        Global global = new Global(Lists.newArrayList(loaded));
        assertNotNull("should load states",loaded);
        assertEquals("expecting 2 functions", 2, loaded.getNames().size());
    }

}
