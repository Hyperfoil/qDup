package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.State;

import org.junit.BeforeClass;
import org.junit.Test;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;

import io.hyperfoil.tools.yaup.yaml.OverloadConstructor;

import static io.hyperfoil.tools.qdup.State.RUN_PREFIX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class StateConstructTest {

    static Yaml yaml = null;

    @BeforeClass
    public static void setupYaml(){
        OverloadConstructor constructor = new  OverloadConstructor();
        StateConstruct stateConstruct = new StateConstruct();
        constructor.addConstruct(new Tag(State.class),stateConstruct);
        yaml = new Yaml(constructor);
    }

    @Test
    public void key_value(){
        State loaded = yaml.loadAs("key: value\nfoo: bar",State.class);
        assertNotNull("should load states",loaded);
        assertEquals("run prefix",RUN_PREFIX,loaded.getPrefix());
        assertEquals("state[key]=value","value",loaded.get("key"));
        assertEquals("expect 0 children",0,loaded.getChildNames().size());
    }


}
