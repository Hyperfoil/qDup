package io.hyperfoil.tools.qdup.shell;

import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.SecretFilter;
import io.hyperfoil.tools.qdup.SshTestBase;
import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.junit.Assert.*;

public class ContainerShellTest extends SshTestBase {
    /**
     * registry.access.redhat.com/ubi8/ubi container exits?
     * also test an invalid container (response is not a valid containerId)
     */
    @Test(timeout = 5_000)
    public void failure_cannot_connect_remote(){
        Host host = new Host("idk","doesnotexist.localhost",null,22,null,false,"podman","quay.io/wreicher/omb");
        AbstractShell shell = new ContainerShell(
            host,
            "",
            new ScheduledThreadPoolExecutor(2),
            new SecretFilter(),
            false
        );
        boolean connected = shell.connect();
        assertFalse("shell should not be connected",connected);
    }

    //@Test(timeout = 10_000)
    @Test
    public void failure_container_stops_before_connect(){
        Host host = Host.parse("registry.access.redhat.com/ubi8/ubi");
        host.setStartConnectedContainer(Collections.EMPTY_LIST);
        host.setCreateConnectedContainer(Collections.EMPTY_LIST);
        AbstractShell shell = new ContainerShell(
            host,
            "",
            new ScheduledThreadPoolExecutor(2),
            new SecretFilter(),
            false
        );
        boolean connected = shell.connect();
        String output = shell.shSync("date +%s");
        assertFalse("shell should not be connected",connected);
        assertNotNull("shSync should not return null even if not connected",output);
        assertEquals("shSync should return empty string when not connected","",output);
    }
    @Test
    public void container_start_also_connects(){
        Host host = Host.parse("quay.io/fedora/fedora");
        host.setStartContainer(Host.PODMAN_CREATE_CONNECTED_CONTAINER);
        AbstractShell shell = new ContainerShell(
                host,
                "",
                new ScheduledThreadPoolExecutor(2),
                new SecretFilter(),
                false
        );
        boolean connected = shell.connect();
        String output = shell.shSync("whoami");
        String systemUser = System.getProperty("user.name");
        assertTrue("shell should be connected",connected);
        assertTrue("host should have a containerId",host.hasContainerId());
        assertNotEquals("shell should be using a different userId",output,systemUser);
    }

    @Test
    public void remote_container_connect(){
        AbstractShell remoteShell = AbstractShell.getShell(
                getHost(),
                "",
                new ScheduledThreadPoolExecutor(2),
                new SecretFilter(),
                false
        );
        boolean remoteConnected = remoteShell.connect();
        assertTrue("remote shell should be connected",remoteConnected);
        String remoteHostname = remoteShell.shSync("uname -a");
        Host host = new Host(
                getHost().getUserName(),
                getHost().getHostName(),
                getHost().getPassword(),
                getHost().getPort(),
                getHost().getPrompt(),
                getHost().isLocal(),
                "docker",
                "registry.access.redhat.com/ubi8/ubi");
        host.setPassphrase(getHost().getPassphrase());
        host.setIdentity(getHost().getIdentity());
        //why were we setting start connected for docker?
        //host.setStartContainer(Host.DOCKER_START_CONNECTED_CONTAINER);
        AbstractShell shell = new ContainerShell(
                host,
                "",
                new ScheduledThreadPoolExecutor(2),
                new SecretFilter(),
                false
        );
        boolean connected = shell.connect();
        assertTrue("shell should be connected",connected);
        String containerHostname = shell.shSync("uname -a");
        assertNotEquals("container and remote shell should have different uname",remoteHostname,containerHostname);
        assertTrue("host should have a containerId",host.hasContainerId());
    }
    @Test
    public void container_connect_also_performs_start(){
        Host host = Host.parse("registry.access.redhat.com/ubi8/ubi");
        host.setConnectShell(Host.PODMAN_CREATE_CONNECTED_CONTAINER);
        AbstractShell shell = new ContainerShell(
                host,
                "",
                new ScheduledThreadPoolExecutor(2),
                new SecretFilter(),
                false
        );
        boolean connected = shell.connect();
        String output = shell.shSync("whoami");
        String systemUser = System.getProperty("user.name");
        assertTrue("shell should be connected",connected);
        assertTrue("host should have a containerId",host.hasContainerId());
        assertNotEquals("shell should be using a different userId",output,systemUser);
    }


    @Test
    public void container_stops_before_connect_then_starts_connected(){
        Host host = Host.parse("registry.access.redhat.com/ubi8/ubi");
        AbstractShell shell = new ContainerShell(
                host,
                "",
                new ScheduledThreadPoolExecutor(2),
                new SecretFilter(),
                false
        );
        boolean connected = shell.connect();
        String output = shell.shSync("whoami");
        String systemUser = System.getProperty("user.name");
        assertTrue("shell should be connected",connected);
        assertTrue("host should have a containerId",host.hasContainerId());
        assertNotEquals("shell should be using a different userId",output,systemUser);
    }

    //This ensures we can connect to the same container for setup, run, cleanup
    @Test
    public void connect_to_containerId_after_first_connect(){
        Host host = new Host("","",null,22,null,true,"podman","quay.io/wreicher/omb");
        AbstractShell shell = new ContainerShell(
            host,
            "",
            new ScheduledThreadPoolExecutor(2),
            new SecretFilter(),
            false
        );
        boolean connected = shell.connect();
        assertTrue("shell should be connected",connected);
        assertTrue("host should have a containerId",host.hasContainerId());
        String containerId = host.getContainerId();
        shell.close();
        shell = new ContainerShell(
            host,
            "",
            new ScheduledThreadPoolExecutor(2),
            new SecretFilter(),
            false
        );
        connected = shell.connect();
        assertTrue("shell should be connected",connected);
        assertTrue("host should have a containerId",host.hasContainerId());
        String newContainerId = host.getContainerId();
        assertEquals("should have same containerId",containerId,newContainerId);
        String response = shell.shSync("mktemp");
        File f = new File(response);
        assertFalse("File should not exist on local filesystem",f.exists());
    }

    @Test
    public void connect_sets_containerId(){
        Host host = new Host("","",null,22,null,true,"podman","quay.io/wreicher/omb");
        AbstractShell shell = new ContainerShell(
            host,
            "",
            new ScheduledThreadPoolExecutor(2),
            new SecretFilter(),
            false
        );
        assertFalse("host should not have a containerId",host.hasContainerId());
        boolean connected = shell.connect();
        assertTrue("host should have a containerId",host.hasContainerId());
        assertTrue("shell should be connected",connected);
        assertTrue("shell should be open",shell.isOpen());
        assertTrue("shell should be ready",shell.isReady());
    }
    @Test
    public void start_that_connects_still_sets_containerId(){
        Host host = new Host("","",null,22,null,true,"podman","quay.io/wreicher/omb");
        host.setStartContainer(Host.PODMAN_CREATE_CONNECTED_CONTAINER);
        AbstractShell shell = new ContainerShell(
                host,
                "",
                new ScheduledThreadPoolExecutor(2),
                new SecretFilter(),
                false
        );
        assertFalse("host should not have a containerId",host.hasContainerId());
        boolean connected = shell.connect();
        assertTrue("host should have a containerId",host.hasContainerId());
        String containerId = host.getContainerId();
        assertTrue("shell should be connected",connected);
        assertTrue("shell should be open",shell.isOpen());
        assertTrue("shell should be ready",shell.isReady());
    }
    @Test
    public void connect_replaces_sub_shell(){
        Host host = new Host("","",null,22,null,true,"podman","quay.io/wreicher/omb");
        AbstractShell shell = new ContainerShell(
            host,
            "",
            new ScheduledThreadPoolExecutor(2),
            new SecretFilter(),
            false
        );
        assertFalse("host should not have a containerId",host.hasContainerId());
        boolean connected = shell.connect();
        assertTrue("host should have a containerId",host.hasContainerId());
        assertTrue("shell should be connected",connected);
        assertTrue("shell should be open",shell.isOpen());
        assertTrue("shell should be ready",shell.isReady());
        String response = shell.shSync("mktemp");
        File f = new File(response);
        assertFalse("File should not exist on local filesystem",f.exists());
    }
}
