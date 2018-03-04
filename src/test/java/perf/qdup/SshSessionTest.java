package perf.qdup;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SshSessionTest extends SshTestBase{

//    @Rule
//    public final TestServer testServer = new TestServer();

    @Test
    public void testConnect(){
        String userHome = System.getProperty("user.home");
        String currentDir = System.getProperty("user.dir");
        SshSession sshSession = new SshSession(
                getHost(),
                userHome+"/.ssh/known_hosts",
                userHome+"/.ssh/id_rsa",
                null
        );
        assertTrue("SshSession should be open after init",sshSession.isOpen());
        sshSession.sh("pwd");
        String pwdOutput = sshSession.getOutput().trim();

        //NOTE: expect userHome when using sshd but expect currentDir if using a TestServer
        //Test server is not working at the moment so we test for userHome
        assertEquals("pwd should be the current working directory",userHome,pwdOutput);
        sshSession.close();
        assertFalse("SshSession should be closed",sshSession.isOpen());
    }

    @Test @Ignore
    public void ctrlC(){
        //TODO the TestServer cannot support ctrlC
    }
}
