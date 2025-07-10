package io.hyperfoil.tools.qdup.cli;

import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.config.yaml.HostDefinition;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.images.builder.Transferable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusMainTest
class QDupPicoTest {


    private static GenericContainer container;

    @BeforeAll
    public static void createContainer() {
        try {
            setup(getPath("keys/qdup.pub"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setup(Path pubPath ) throws IOException {
        String pub = "";
        try {
            pub = Files.readString(pubPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //MountableFile mountableFile = MountableFile.forClasspathResource("keys/qdup.pub",Integer.parseInt("644",8));
        String pubKey = pub;
        container = new GenericContainer(new ImageFromDockerfile("local/qdup-testcontainer",false)
                .withDockerfileFromBuilder(builder ->
                        builder
                                //.from("alpine:3.2")
                                .from("mirror.gcr.io/library/ubuntu:24.10")
//                 .from("fedora:35")
                                .run("apt-get update && apt-get install -y openssh-server openssh-client rsync sudo curl && apt-get clean")
//                      .run("apt-get install -y apt-transport-https")
//                      .run("apt-get install -y openssh-server openssh-client rsync sudo curl && apt-get clean")
//                      .run("curl -fsSL https://get.docker.com -o get-docker.sh")
//                      .run("ulimit -n 1048576")
//                      .run("sh ./get-docker.sh")

//                      .volume("/var/run/docker.sock:/var/run/docker.sock")
//                      .volume("/bin/docker:/bin/docker")

                                //.run("service docker start")///etc/init.d/docker: 61: ulimit: error setting limit (Operation not permitted)
//                 .run("dnf install -y openssh-server openssh-clients rsync")

                                .run("mkdir -p /var/run/sshd")
                                .run("(umask 077 && test -d /root/.ssh || mkdir /root/.ssh)")
////                 .run("(umask 077 && touch /root/.ssh/authorized_keys)")
////                 .run(" echo \""+pubKey+"\" >> /root/.ssh/authorized_keys")
                                .run("chmod 700 /root/.ssh")

//                      .run("chown root /root/.ssh/authorized_keys")

//                 .run("chmod 600 /root/.ssh/authorized_keys")
                                //.run("ssh-keygen -A")
                                .run("ls -al /root/.ssh")
//                      .run("cat /root/.ssh/authorized_keys")
                                .run("echo 'root:password' | chpasswd")
                                .run("sed -i 's/PermitRootLogin prohibit-password/PermitRootLogin yes/' /etc/ssh/sshd_config")
                                .run("sed -i 's/#AuthorizedKeysFile.*/AuthorizedKeysFile .ssh\\/authorized_keys/g' /etc/ssh/sshd_config")
                                .run("sed 's@session\\s*required\\s*pam_loginuid.so@session optional pam_loginuid.so@g' -i /etc/pam.d/sshd")
                                .expose(22)
                                .entryPoint("/usr/sbin/sshd -D")
                                .build()))
                .withImagePullPolicy(PullPolicy.defaultPolicy())
                .withCopyToContainer(Transferable.of(Files.readAllBytes(pubPath)),"/root/.ssh/authorized_keys")
                .withFileSystemBind("/var/run/docker.sock","/var/run/docker.sock", BindMode.READ_WRITE)
                .withFileSystemBind("/bin/docker","/bin/docker", BindMode.READ_ONLY)
//           .withCopyFileToContainer(mountableFile,"/root/.ssh/authorized_keys")
                .withExposedPorts(22);
        container.start();
        try {
            Container.ExecResult response = container.execInContainer("ls","-al","/root/.ssh");
        }catch(IOException | InterruptedException e){
            e.printStackTrace();
        }
        try {
            Container.ExecResult response = container.execInContainer("cat","/root/.ssh/authorized_keys");
        }catch(IOException | InterruptedException e){
            e.printStackTrace();
        }
        String hostname=container.getHost();
        host = new Host("root",hostname,null,container.getMappedPort(22),null,false,null,null);
        host.setIdentity(getPath("keys/qdup").toFile().getPath());
        hostDefinition = new HostDefinition(host.toString());


    }

    public static Path getPath(String subDir){
        return  Paths.get(
                QDupPicoTest.class.getProtectionDomain().getCodeSource().getLocation().getPath()
        ).resolve(
                Paths.get(subDir)
        );
    }

    private static Host host;
    private static HostDefinition hostDefinition;

    public static Host getHost(){return host;}
    public static HostDefinition getHostDefinition(){return hostDefinition;}

    public String getIdentity() {
        return getPath("keys/qdup").toFile().getPath();
    }

    /**
     * No arg execution should be an error that displays the usage hint
     * @param launcher
     */
    @Test
    public void no_arg_help(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch();
        assertNotEquals(0,result.exitCode());
        assertTrue(result.getErrorOutput().contains("Usage:"),result.getErrorOutput());
    }

    @Test
    public void whoami(QuarkusMainLauncher launcher) throws IOException {
        Path configPath = Files.writeString(File.createTempFile("parse",".yaml").toPath(),
                """
                ---
                scripts:
                  whoami:
                  - sh: whoami
                  - sh: hostname
                hosts:
                  target: HOST_TARGET
                roles:
                  doit:
                    hosts:
                    - target
                    run-scripts:
                    - whoami
                """.replaceAll("HOST_TARGET",getHost().toString()));
        configPath.toFile().deleteOnExit();
        LaunchResult result = launcher.launch("--fullPath","/tmp","--identity",getIdentity(),configPath.toString());


    }
}
