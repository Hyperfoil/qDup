package io.hyperfoil.tools.qdup;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;

public class SshTestBase {

    private final Host host;

    public SshTestBase(){
        String hostname="localhost";
        try {
            hostname = java.net.InetAddress.getLocalHost().getHostName();

        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        host = new Host(System.getProperty("user.name"),hostname);
    }

    public SshSession getSession(){
        return getSession(null);
    }
    public SshSession getSession(ScheduledThreadPoolExecutor executor){
        String userHome = System.getProperty("user.home");
        String currentDir = System.getProperty("user.dir");
        String setupCommand = "export FOO=\"foo\"  BAR=\"bar\"";
        SshSession sshSession = new SshSession(
                getHost(),
                userHome+"/.ssh/known_hosts",
                userHome+"/.ssh/id_rsa",
                null,
                5,
                setupCommand,
                executor,
           false
        );
        assertTrue("local ssh session failed to connect",sshSession.isOpen());
        return sshSession;
    }

    public Host getHost(){return host;}



    public static InputStream stream(String...input){
        return new ByteArrayInputStream(
                Arrays.asList(input).stream()
                        .collect(Collectors.joining("\n")).getBytes()
        );
    }

}
