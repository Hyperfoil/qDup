package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.Host;

import io.hyperfoil.tools.qdup.State;
import org.junit.BeforeClass;
import org.junit.Test;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Tag;

import io.hyperfoil.tools.yaup.yaml.OverloadConstructor;

import java.util.List;

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
    public void map_with_download(){
        HostDefinition definition = yaml.loadAs("username: userName\nhostname: hostName.subdomain.domain.com\ndownload:\n - scp\n - \"-r\"\n - ${{source}}\n - ${{destination}}",HostDefinition.class);
        assertFalse(definition.isOneLine());
        Host host = definition.toHost(new State(""));
        List<String> download = host.getDownload();
        assertEquals("expect two entries on download",4,download.size());
        assertEquals("download[0]: "+download,"scp",download.get(0));
        assertEquals("download[1]: "+download,"-r",download.get(1));
        assertEquals("download[2]: "+download,"${{source}}",download.get(2));
        assertEquals("download[3]: "+download,"${{destination}}",download.get(3));
    }
    @Test
    public void map_username_hostname_identity(){
        Host loaded = yaml.loadAs("username: userName\nhostname: hostName.subdomain.domain.com\nidentity: foo",HostDefinition.class).toHost(new State(""));
        assertNotNull("should load from map",loaded);
        assertEquals("username","userName",loaded.getUserName());
        assertEquals("hostname","hostName.subdomain.domain.com",loaded.getHostName());
        assertTrue("host has identity",loaded.hasIdentity());
        assertEquals("identity","foo",loaded.getIdentity());
    }
    @Test
    public void map_username_hostname_port_password(){
        Host loaded = yaml.loadAs("username: userName\nhostname: hostName.subdomain.domain.com\nport: 2222\npassword: foo",HostDefinition.class).toHost(new State(""));
        assertNotNull("should load from map",loaded);
        assertEquals("username","userName",loaded.getUserName());
        assertEquals("hostname","hostName.subdomain.domain.com",loaded.getHostName());
        assertEquals("port",2222,loaded.getPort());
    }
    @Test(expected = YAMLException.class)
    public void map_extra_keys(){
        Host loaded = yaml.loadAs("username: userName\nhostname: hostName.subdomain.domain.com\nFAKE: 22",HostDefinition.class).toHost(new State(""));
    }
}
