package io.hyperfoil.tools.qdup.shell;

import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.SecretFilter;
import io.hyperfoil.tools.qdup.SshTestBase;
import org.junit.Test;

import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AbstractShellTest extends SshTestBase {

    @Test
    public void getShell_localShell(){
        Host host = Host.parse(Host.LOCAL);
        AbstractShell shell = AbstractShell.getShell("getShell_localShell",host,new ScheduledThreadPoolExecutor(2),new SecretFilter(), false);
        assertNotNull("shell should not be null",shell);
        assertTrue("shell should be open",shell.isOpen());
        assertTrue("shell should be ready",shell.isReady());
        assertTrue("shell should be LocalShell but was "+shell.getClass().getSimpleName(),shell instanceof LocalShell);
    }

    @Test
    public void getShell_podman_containerShell(){
        Host host = new Host("","",null,22,null,true,"podman","quay.io/fedora/fedora");
        AbstractShell shell = AbstractShell.getShell("getShell_podman_containerShell",host,new ScheduledThreadPoolExecutor(2),new SecretFilter(),false);
        try{
            assertNotNull("shell should not be null",shell);
            assertTrue("shell should be open",shell.isOpen());
            assertTrue("shell should be ready",shell.isReady());
            assertTrue("shell should be ContainerShell but was "+shell.getClass().getSimpleName(),shell instanceof ContainerShell);
        }finally{
            ContainerShell containerShell = (ContainerShell)shell;
            containerShell.stopContainerIfStarted();
        }
    }

    @Test
    public void getShell_sshShell(){
        Host host = getHost();
        AbstractShell shell = AbstractShell.getShell("getShell_sshShell",host,new ScheduledThreadPoolExecutor(2),new SecretFilter(),false);
        assertNotNull("shell should not be null",shell);
        assertTrue("shell should be open",shell.isOpen());
        assertTrue("shell should be ready",shell.isReady());
        assertTrue("shell should be SshShell but was "+shell.getClass().getSimpleName(),shell instanceof SshShell);
    }

    @Test
    public void isTracing(){
        Host host = getHost();
        AbstractShell shell = AbstractShell.getShell("tracingShell",host,new ScheduledThreadPoolExecutor(2),new SecretFilter(),true);

        boolean isTracing = shell.isTracing();
        assertTrue(isTracing);
        String path = shell.getSessionStreams().getTraceName();
        shell.shSync("env");
    }
}
