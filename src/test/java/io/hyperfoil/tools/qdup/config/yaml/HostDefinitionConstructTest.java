package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.Host;

import io.hyperfoil.tools.qdup.State;
import org.junit.BeforeClass;
import org.junit.Test;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Tag;

import io.hyperfoil.tools.yaup.yaml.OverloadConstructor;

import static org.junit.Assert.*;


public class HostDefinitionConstructTest {

    static Yaml yaml = null;

    @BeforeClass
    public static void setupYaml(){
        OverloadConstructor constructor = new  OverloadConstructor();
        HostDefinitionConstruct hostConstruct = new HostDefinitionConstruct();
        constructor.addConstruct(new Tag("host"),hostConstruct);
        constructor.addConstruct(new Tag(HostDefinition.class),hostConstruct);
        yaml = new Yaml(constructor);
    }


    @Test
    public void string_host_user(){
        Host loaded = yaml.loadAs("userName@hostName",HostDefinition.class).toHost(new State(""));
        assertNotNull("should load from string",loaded);
        assertEquals("username","userName",loaded.getUserName());
        assertEquals("hostname","hostName",loaded.getHostName());
        assertEquals("port",22,loaded.getPort());
    }

    @Test
    public void string_local(){
        Host loaded = yaml.loadAs(Host.LOCAL,HostDefinition.class).toHost(new State(""));
        assertNotNull("should load from string",loaded);
        assertTrue("host should be local",loaded.isLocal());
    }

    @Test
    public void string_local_container(){
        String container = "localhost:31337/foo/bar";
        Host loaded = yaml.loadAs(Host.LOCAL+Host.CONTAINER_SEPARATOR+container,HostDefinition.class).toHost(new State(""));
        assertNotNull("should load from string",loaded);
        assertTrue("host should be in a container",loaded.isContainer());
        assertTrue("host should be local",loaded.isLocal());
        assertEquals("container incorrect",container,loaded.getDefinedContainer());
    }
    @Test
    public void string_remote_container(){
        String container = "localhost:31337/foo/bar";
        Host loaded = yaml.loadAs("user@host"+Host.CONTAINER_SEPARATOR+container,HostDefinition.class).toHost(new State(""));
        assertNotNull("should load from string",loaded);
        assertTrue("host should be in a container",loaded.isContainer());
        assertFalse("host should not be local",loaded.isLocal());
        assertEquals("incorrect hostname","host",loaded.getHostName());
        assertEquals("incorrect username","user",loaded.getUserName());
        assertEquals("incorrect container",container,loaded.getDefinedContainer());
    }
    @Test
    public void string_fulllyQualifiedHost_user(){
        Host loaded = yaml.loadAs("userName@hostName.subdomain.domain.com",HostDefinition.class).toHost(new State(""));
        assertNotNull("should load from string",loaded);
        assertEquals("username","userName",loaded.getUserName());
        assertEquals("hostname","hostName.subdomain.domain.com",loaded.getHostName());
        assertEquals("port",22,loaded.getPort());
    }
    @Test
    public void map_full(){
        Host loaded = yaml.loadAs("username: userName\nhostname: hostName.subdomain.domain.com\nport: 22\npassword: foo",HostDefinition.class).toHost(new State(""));
        assertNotNull("should load from map",loaded);
        assertEquals("username","userName",loaded.getUserName());
        assertEquals("hostname","hostName.subdomain.domain.com",loaded.getHostName());
        assertEquals("port",22,loaded.getPort());
    }
    @Test(expected = YAMLException.class)
    public void map_extra_keys(){
        Host loaded = yaml.loadAs("username: userName\nhostname: hostName.subdomain.domain.com\nFAKE: 22",HostDefinition.class).toHost(new State(""));
    }
}
