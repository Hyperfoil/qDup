package perf.qdup;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.stream.Collectors;

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

    public Host getHost(){return host;}



    public static InputStream stream(String...input){
        return new ByteArrayInputStream(
                Arrays.asList(input).stream()
                        .collect(Collectors.joining("\n")).getBytes()
        );
    }

}
