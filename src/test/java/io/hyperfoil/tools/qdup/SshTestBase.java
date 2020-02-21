package io.hyperfoil.tools.qdup;

import com.github.dockerjava.api.exception.DockerException;
import io.hyperfoil.tools.qdup.config.CmdBuilder;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.yaup.AsciiArt;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;

public class SshTestBase {

//    static {
//        try {
//            DockerClientFactory.instance().client();
//        } catch (DockerException e) {
//            if (!e.getMessage().contains("BITBUCKET_CLONE_DIR")) {
//                throw new IllegalStateException(e);
//            }
//            // Ignore exception related to reach outside of BITBUCKET_CLONE_DIR in ResourceReaper
//        }
//    }
    private static GenericContainer container;


    public RunConfigBuilder getBuilder(){
        RunConfigBuilder builder = new RunConfigBuilder(CmdBuilder.getBuilder());
        builder.setIdentity(Paths.get(
           getClass().getProtectionDomain().getCodeSource().getLocation().getPath()
        ).resolve(
           Paths.get("qdup")
        ).toFile().getPath());
        return builder;
    }
    public String getIdentity(){
        return Paths.get(
           getClass().getProtectionDomain().getCodeSource().getLocation().getPath()
        ).resolve(
           Paths.get("qdup")
        ).toFile().getPath();
    }
    @BeforeClass
    public static void createContainer() {

        String pub = "";
        try {
            pub = Files.readString(Paths.get(
               SshTestBase.class.getProtectionDomain().getCodeSource().getLocation().getPath()
            ).resolve(
               Paths.get("qdup.pub")
            ));
        } catch (IOException e) {
            e.printStackTrace();
        }
        String pubKey = pub;
        container = new GenericContainer(new ImageFromDockerfile()
           .withDockerfileFromBuilder(builder ->
              builder
                 //.from("alpine:3.2")
                 .from("ubuntu:16.04")
                 .run("apt-get update && apt-get install -y openssh-server openssh-client rsync && apt-get clean")
                 .run("mkdir /var/run/sshd")
                 .run("(umask 077 && test -d /root/.ssh || mkdir /root/.ssh)")
                 .run("(umask 077 && touch /root/.ssh/authorized_keys)")
                 .run(" echo \""+pubKey+"\" >> /root/.ssh/authorized_keys")
                 .run("chmod 700 /root/.ssh")
                 .run("chmod 600 /root/.ssh/authorized_keys")
                 .run("echo 'root:password' | chpasswd")
                 .run("sed -i 's/PermitRootLogin prohibit-password/PermitRootLogin yes/' /etc/ssh/sshd_config")
                 .run("sed -i 's/#AuthorizedKeysFile.*/AuthorizedKeysFile .ssh\\/authorized_keys/g' /etc/ssh/sshd_config")
                 .run("sed 's@session\\s*required\\s*pam_loginuid.so@session optional pam_loginuid.so@g' -i /etc/pam.d/sshd")
                 .expose(22)
                 .entryPoint("/usr/sbin/sshd -D")
                 .build()))
           .withExposedPorts(22);
        container.start();
        String hostname="localhost";
        try {
            hostname = java.net.InetAddress.getLocalHost().getHostName();

        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        hostname = container.getContainerIpAddress();
        //host = new Host(System.getProperty("user.name"),hostname);
        //host = new Host("root",hostname,"password",container.getMappedPort(22));
        host = new Host("root",hostname,null,container.getMappedPort(22));
    }
    private static Host host;

    public SshTestBase(){}

    public String readFile(String path){
        String response = exec("/bin/sh","-c","cat "+path);
        return response;
    }
    public boolean exists(String path){
        String response = exec("/bin/sh","-c","test -f "+path+" && echo \"exists\"").trim();
        return response != null && response.contains("exists");
    }
    public String exec(String...commands){
        try {
            return container.execInContainer(commands).getStdout();
        } catch (IOException e) {
            //e.printStackTrace();
            return e.getMessage();
        } catch (InterruptedException e) {
            //e.printStackTrace();
            return e.getMessage();
        }
    }

    public SshSession getSession(){
        return getSession(null,false);
    }
    public SshSession getSession(boolean trace){
        return getSession(null,trace);
    }
    public SshSession getSession(ScheduledThreadPoolExecutor executor, boolean trace){
        String userHome = System.getProperty("user.home");
        String currentDir = System.getProperty("user.dir");
        String setupCommand = "export FOO=\"foo\"  BAR=\"bar\"";
        SshSession sshSession = new SshSession(
                getHost(),
                userHome+"/.ssh/known_hosts",
                getIdentity(),
                null,
                5,
                setupCommand,
                executor,
           trace
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
