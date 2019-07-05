package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.cmd.impl.ScriptCmd;

import org.junit.BeforeClass;
import org.junit.Test;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;

import io.hyperfoil.tools.yaup.yaml.OverloadConstructor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ScriptCmdConstructTest {

    static Yaml yaml = null;

    @BeforeClass
    public static void setupYaml(){
        OverloadConstructor constructor = new  OverloadConstructor();
        ScriptCmdConstruct scriptCmdConstruct = new ScriptCmdConstruct();
        constructor.addConstruct(new Tag("script"),scriptCmdConstruct);
        constructor.addConstruct(new Tag(ScriptCmd.class),scriptCmdConstruct);
        yaml = new Yaml(constructor);
    }


    @Test
    public void name_only(){
        ScriptCmd loaded = yaml.loadAs("scriptName",ScriptCmd.class);
        assertNotNull("should load  role",loaded);
        assertEquals("name","scriptName",loaded.getName());
    }
    @Test
    public void name_and_withs(){
        ScriptCmd loaded = yaml.loadAs("scriptName:\n"+
                "  with: { FOO : bar }",ScriptCmd.class);
        assertNotNull("should load scriptCmd",loaded);
        assertEquals("name","scriptName",loaded.getName());
        assertEquals("with[FOO]=bar","bar",loaded.getWith().get("FOO"));
    }
}
