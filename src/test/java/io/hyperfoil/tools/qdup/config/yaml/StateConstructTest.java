package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.State;

import io.hyperfoil.tools.yaup.json.Json;
import org.junit.BeforeClass;
import org.junit.Test;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;

import io.hyperfoil.tools.yaup.yaml.OverloadConstructor;

import static io.hyperfoil.tools.qdup.State.RUN_PREFIX;
import static org.junit.Assert.*;

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

    @Test
    public void key_value_object(){
        State loaded = yaml.loadAs("key: {}",State.class);
        assertNotNull("should load states",loaded);
        assertEquals("run prefix",RUN_PREFIX,loaded.getPrefix());
        Object key = loaded.get("key");
        assertNotNull("state[key] should exist",key);
        assertTrue("state[key] should be json",key instanceof Json);
        Json json = (Json)key;
        assertFalse("state[key] should be an object "+json,json.isArray());
        assertEquals("expect 0 children",0,loaded.getChildNames().size());
    }



}
