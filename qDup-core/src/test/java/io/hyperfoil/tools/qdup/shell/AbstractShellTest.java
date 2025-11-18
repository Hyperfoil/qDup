package io.hyperfoil.tools.qdup.shell;

import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.SecretFilter;
import io.hyperfoil.tools.qdup.SshTestBase;
import org.junit.Test;

import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.junit.Assert.*;

public class AbstractShellTest extends SshTestBase {

    @Test
    public void getShell_localShell(){
        Host host = Host.parse(Host.LOCAL);
        AbstractShell shell = AbstractShell.getShell("getShell_localShell",host,new ScheduledThreadPoolExecutor(2),new SecretFilter(), null);
        assertNotNull("shell should not be null",shell);
        assertTrue("shell should be open",shell.isOpen());
        assertTrue("shell should be ready",shell.isReady());
        assertTrue("shell should be LocalShell but was "+shell.getClass().getSimpleName(),shell instanceof LocalShell);
    }

    @Test
    public void getShell_podman_containerShell(){
        Host host = new Host("","",null,22,null,true,true,"podman","quay.io/fedora/fedora");
        AbstractShell shell = AbstractShell.getShell("getShell_podman_containerShell",host,new ScheduledThreadPoolExecutor(2),new SecretFilter(),null);
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
        AbstractShell shell = AbstractShell.getShell("getShell_sshShell",host,new ScheduledThreadPoolExecutor(2),new SecretFilter(),null);
        assertNotNull("shell should not be null",shell);
        assertTrue("shell should be open",shell.isOpen());
        assertTrue("shell should be ready",shell.isReady());
        assertTrue("shell should be SshShell but was "+shell.getClass().getSimpleName(),shell instanceof SshShell);
    }

    @Test
    public void isTracing(){
        Host host = getHost();
        AbstractShell shell = AbstractShell.getShell("tracingShell",host,new ScheduledThreadPoolExecutor(2),new SecretFilter(),System.getProperty("java.io.tmpdir"));

        boolean isTracing = shell.isTracing();
        assertTrue(isTracing);
    }

    @Test
    public void host_prompt(){
        Host host = new Host(getHost().getUserName(),getHost().getHostName(),getHost().getPassword(),getHost().getPort(),"FOO",true,false,"podman",getHost().getContainerId());
        host.setIdentity(getHost().getIdentity());
        host.setPassphrase(getHost().getPassphrase());
        AbstractShell shell = AbstractShell.getShell("host_prompt",host,new ScheduledThreadPoolExecutor(2),new SecretFilter(),null);
        assertNotNull("shell should not be null",shell);
        assertTrue("shell should be open",shell.isOpen());
        assertTrue("shell should be ready",shell.isReady());

        String response = shell.shSync("echo $PS1");
        assertEquals("response should be FOO but was: "+response,"FOO",response);

    }

}
