package perf.qdup.config.yaml;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;
import perf.qdup.State;
import perf.yaup.yaml.OverloadConstructor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static perf.qdup.State.RUN_PREFIX;

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
