package perf.ssh;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SshSessionTest {

    @Rule
    public final TestServer testServer = new TestServer();

    @Test
    public void testConnect(){
        String userHome = System.getProperty("user.home");
        String currentDir = System.getProperty("user.dir");
        SshSession sshSession = new SshSession(
                testServer.getHost(),//testServer.getPort()),
                userHome+"/.ssh/known_hosts",
                userHome+"/.ssh/id_rsa",
                null

        );
        assertTrue("SshSession should be open after init",sshSession.isOpen());
        sshSession.sh("pwd");
        String pwdOutput = sshSession.getOutput().trim();
        assertEquals("pwd should be the current working directory",currentDir,pwdOutput);
        sshSession.close();
        assertFalse("SshSession should be closed",sshSession.isOpen());
    }

    @Test @Ignore
    public void ctrlC(){
        //TODO the TestServer cannot support ctrlC
    }
}
