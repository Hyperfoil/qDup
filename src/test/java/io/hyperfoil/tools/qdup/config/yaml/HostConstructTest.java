package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.Host;

import org.junit.BeforeClass;
import org.junit.Test;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Tag;

import io.hyperfoil.tools.yaup.yaml.OverloadConstructor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


public class HostConstructTest {

    static Yaml yaml = null;


    @BeforeClass
    public static void setupYaml(){
        OverloadConstructor constructor = new  OverloadConstructor();
        HostConstruct hostConstruct = new HostConstruct();
        constructor.addConstruct(new Tag("host"),hostConstruct);
        constructor.addConstruct(new Tag(Host.class),hostConstruct);
        yaml = new Yaml(constructor);
    }


    @Test
    public void string_host_user(){
        Host loaded = yaml.loadAs("userName@hostName",Host.class);
        assertNotNull("should load from string",loaded);
        assertEquals("username","userName",loaded.getUserName());
        assertEquals("hostname","hostName",loaded.getHostName());
        assertEquals("port",22,loaded.getPort());
    }
    @Test
    public void string_fulllyQualifiedHost_user(){
        Host loaded = yaml.loadAs("userName@hostName.subdomain.domain.com",Host.class);
        assertNotNull("should load from string",loaded);
        assertEquals("username","userName",loaded.getUserName());
        assertEquals("hostname","hostName.subdomain.domain.com",loaded.getHostName());
        assertEquals("port",22,loaded.getPort());
    }
    @Test
    public void map_full(){
        Host loaded = yaml.loadAs("username: userName\nhostname: hostName.subdomain.domain.com\nport: 22",Host.class);
        assertNotNull("should load from map",loaded);
        assertEquals("username","userName",loaded.getUserName());
        assertEquals("hostname","hostName.subdomain.domain.com",loaded.getHostName());
        assertEquals("port",22,loaded.getPort());
    }
    @Test(expected = YAMLException.class)
    public void map_extra_keys(){
        Host loaded = yaml.loadAs("username: userName\nhostname: hostName.subdomain.domain.com\nFAKE: 22",Host.class);
    }
}
