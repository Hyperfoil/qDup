package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.impl.ScriptCmd;

import io.hyperfoil.tools.qdup.config.Role;
import org.junit.BeforeClass;
import org.junit.Test;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Tag;

import io.hyperfoil.tools.yaup.yaml.OverloadConstructor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class RoleConstructTest {

    static Yaml yaml = null;

    @BeforeClass
    public static void setupYaml(){
        OverloadConstructor constructor = new  OverloadConstructor();
        RoleConstruct roleConstruct = new RoleConstruct();
        constructor.addConstruct(new Tag("role"),roleConstruct);
        constructor.addConstruct(new Tag(Role.class),roleConstruct);
        yaml = new Yaml(constructor);
    }

    @Test(expected = YAMLException.class)
    public void empty_role(){
        yaml.loadAs("roleName:", Role.class);
        fail("should not be able to create an empty role");
    }
    @Test
    public void host_only(){
        Role loaded = yaml.loadAs(
                "roleName:\n"+
                "  hosts: [hostName]", Role.class);
        assertNotNull("should load  role",loaded);
        assertEquals("role name","roleName",loaded.getName());
        assertEquals("0 scripts\nrun:"+ loaded.getRun()+"\nsetup:"+loaded.getSetup()+"\ncleanup:"+loaded.getCleanup(),0,loaded.getSetup().size()+loaded.getRun().size()+loaded.getCleanup().size());
        assertEquals("expect 0 hosts",0,loaded.getDeclaredHosts().size());
        assertEquals("expect 1 hostRef",1,loaded.getHostRefs().size());
        assertEquals("hostRef[0]=hostName","hostName",loaded.getHostRefs().iterator().next());
    }
    @Test
    public void setup_only(){
        Role loaded = yaml.loadAs(
                "roleName:\n"+
                "  setup-scripts: [scriptName]", Role.class);
        assertNotNull("should load  role",loaded);
        assertEquals("role name","roleName",loaded.getName());
        assertEquals("expect 1 setup-scripts",1,loaded.getSetup().size());
        assertEquals("expect 0 run-scripts",0,loaded.getRun().size());
        assertEquals("expect 0 cleanup-scripts",0,loaded.getCleanup().size());
        Cmd scriptCmd = loaded.getSetup().get(0);
        assertTrue("setup[0] is a ScriptCmd",scriptCmd instanceof ScriptCmd);
        assertEquals("setup[0]=scriptName","scriptName",((ScriptCmd)scriptCmd).getName());
    }
    @Test
    public void run_only(){
        Role loaded = yaml.loadAs(
                "roleName:\n"+
                        "  run-scripts: [scriptName]", Role.class);
        assertNotNull("should load  role",loaded);
        assertEquals("role name","roleName",loaded.getName());
        assertEquals("expect 0 setup-scripts",0,loaded.getSetup().size());
        assertEquals("expect 1 run-scripts",1,loaded.getRun().size());
        assertEquals("expect 0 cleanup-scripts",0,loaded.getCleanup().size());
        Cmd scriptCmd = loaded.getRun().get(0);
        assertTrue("run[0] is a ScriptCmd",scriptCmd instanceof ScriptCmd);
        assertEquals("run[0]=scriptName","scriptName",((ScriptCmd)scriptCmd).getName());
    }
    @Test
    public void run_with_options(){
        Role loaded = yaml.loadAs(
                "roleName:\n"+
                        "  run-scripts:\n" +
                        "  - scriptName:\n"+
                        "      with: { FOO: bar }", Role.class);
        assertNotNull("should load role",loaded);
        assertEquals("role name","roleName",loaded.getName());
        assertEquals("expect 0 setup-scripts",0,loaded.getSetup().size());
        assertEquals("expect 1 run-scripts",1,loaded.getRun().size());
        assertEquals("expect 0 cleanup-scripts",0,loaded.getCleanup().size());
        Cmd scriptCmd = loaded.getRun().get(0);
        assertTrue("run[0] is a ScriptCmd",scriptCmd instanceof ScriptCmd);
        assertEquals("run[0]=scriptName","scriptName",((ScriptCmd)scriptCmd).getName());
        assertEquals("run[0].FOO=bar","bar",scriptCmd.getWith().get("FOO"));
    }
    @Test
    public void cleanup_only(){
        Role loaded = yaml.loadAs(
                "roleName:\n"+
                        "  cleanup-scripts: [scriptName]", Role.class);
        assertNotNull("should lod  role",loaded);
        assertEquals("role name","roleName",loaded.getName());
        assertEquals("expect 0 setup-scripts",0,loaded.getSetup().size());
        assertEquals("expect 0 run-scripts",0,loaded.getRun().size());
        assertEquals("expect 1 cleanup-scripts",1,loaded.getCleanup().size());
        Cmd scriptCmd = loaded.getCleanup().get(0);
        assertTrue("cleanup[0] is a ScriptCmd",scriptCmd instanceof ScriptCmd);
        assertEquals("cleanup[0]=scriptName","scriptName",((ScriptCmd)scriptCmd).getName());
    }

}
