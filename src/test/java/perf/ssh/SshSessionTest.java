package perf.ssh;

import org.junit.Rule;
import org.junit.Test;

public class SshSessionTest {

    @Rule
    public final TestServer testServer = new TestServer();

    @Test
    public void testConnect(){

        SshSession sshSession = new SshSession(
                new Host("fakeUser","localhost",testServer.getPort()),
                "/home/wreicher/.ssh/known_hosts",
                "/home/wreicher/.ssh/id_rsa",
                null

        );


        System.out.println(sshSession.isOpen());
    }
}
