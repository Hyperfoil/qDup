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
        AbstractShell shell = AbstractShell.getShell(host,new ScheduledThreadPoolExecutor(2),new SecretFilter(), false);
        assertNotNull("shell should not be null",shell);
        assertTrue("shell should be open",shell.isOpen());
        assertTrue("shell should be ready",shell.isReady());
        assertTrue("shell should be LocalShell but was "+shell.getClass().getSimpleName(),shell instanceof LocalShell);
    }

    @Test
    public void getShell_containerShell(){
        Host host = new Host("","",null,22,null,true,"podman","quay.io/wreicher/omb");
        AbstractShell shell = AbstractShell.getShell(host,new ScheduledThreadPoolExecutor(2),new SecretFilter(),false);
        assertNotNull("shell should not be null",shell);
        assertTrue("shell should be open",shell.isOpen());
        assertTrue("shell should be ready",shell.isReady());
        assertTrue("shell should be ContainerShell but was "+shell.getClass().getSimpleName(),shell instanceof ContainerShell);
        ContainerShell containerShell = (ContainerShell)shell;
        containerShell.stopContainerIfStarted();
    }

    @Test
    public void getShell_sshShell(){
        Host host = getHost();
        AbstractShell shell = AbstractShell.getShell(host,new ScheduledThreadPoolExecutor(2),new SecretFilter(),false);
        assertNotNull("shell should not be null",shell);
        assertTrue("shell should be open",shell.isOpen());
        assertTrue("shell should be ready",shell.isReady());
        assertTrue("shell should be SshShell but was "+shell.getClass().getSimpleName(),shell instanceof SshShell);
    }
}
