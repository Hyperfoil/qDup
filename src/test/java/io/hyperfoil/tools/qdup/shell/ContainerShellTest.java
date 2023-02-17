package io.hyperfoil.tools.qdup.shell;

import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.SshTestBase;
import io.hyperfoil.tools.yaup.AsciiArt;
import org.junit.Test;

import java.io.File;
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
            false
        );
        boolean connected = shell.connect();
        assertFalse("shell should not be connected",connected);
    }

    @Test(timeout = 10_000)
    public void failure_container_stops_before_connect(){
        Host host = Host.parse("registry.access.redhat.com/ubi8/ubi");
        //host = Host.parse("quay.io/wreicher/omb");
        AbstractShell shell = new ContainerShell(
            host,
            "",
            new ScheduledThreadPoolExecutor(2),
            false
        );
        boolean connected = shell.connect();
        assertFalse("shell should not be connected",connected);
    }

    @Test
    public void connect_to_containerId_after_first_connect(){
        Host host = new Host("","",null,22,null,true,"podman","quay.io/wreicher/omb");
        AbstractShell shell = new ContainerShell(
            host,
            "",
            new ScheduledThreadPoolExecutor(2),
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
