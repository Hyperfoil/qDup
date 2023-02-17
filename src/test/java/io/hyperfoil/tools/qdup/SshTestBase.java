package io.hyperfoil.tools.qdup;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.HostDefinition;
import io.hyperfoil.tools.qdup.shell.AbstractShell;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;

public class SshTestBase {


    protected TmpDir tmpDir;

    @Before
    public void setup(){
        tmpDir = TmpDir.instance();
    }

    @After
    public void cleanUp(){
        //tmpDir.removeDir();
        tmpDir = null;
    }
    private static GenericContainer container;

    private static final ScheduledThreadPoolExecutor SCHEDULED_THREAD_POOL_EXECUTOR = new ScheduledThreadPoolExecutor(2);

    public RunConfigBuilder getBuilder(){
        return getBuilder("qdup");
    }
    public RunConfigBuilder getBuilder(String name){
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.setIdentity(getIdentity());

        setIdentityFilePerms(getPath("keys"), getKeyDirPerms());
        setIdentityFilePerms(getPath("keys/"+name), getPrivKeyPerms());

        //set perms
        return builder;
    }

    private static void setIdentityFilePerms(Path identityFilePath, Set<PosixFilePermission> perms){
        try {
            Files.setPosixFilePermissions(identityFilePath, perms);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Set<PosixFilePermission> getKeyDirPerms(){
        Set<PosixFilePermission> perms = new HashSet<>();
        //add owners permission
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        perms.add(PosixFilePermission.OWNER_EXECUTE);
        perms.add(PosixFilePermission.GROUP_READ);
        perms.add(PosixFilePermission.GROUP_WRITE);
        perms.add(PosixFilePermission.GROUP_EXECUTE);
        perms.add(PosixFilePermission.OTHERS_READ);
        perms.add(PosixFilePermission.OTHERS_EXECUTE);

        return perms;
    }

    private static Set<PosixFilePermission> getPrivKeyPerms(){
        Set<PosixFilePermission> perms = new HashSet<>();
        //add owners permission
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);

        return perms;
    }


    public String getIdentity() {
        return getPath("keys/qdup").toFile().getPath();
    }

    static Path getPath(String subDir){
        return  Paths.get(
                SshTestBase.class.getProtectionDomain().getCodeSource().getLocation().getPath()
        ).resolve(
                Paths.get(subDir)
        );
    }

    public static void restartContainer(){
        if(container!=null) {
            String identity = host.getIdentity();
            String passphrase = host.getPassphrase();
            int randomPort = container.getMappedPort(22);
            //Consumer<CreateContainerCmd> cmd = e -> e.withPortBindings(new PortBinding(Ports.Binding.bindPort(randomPort), new ExposedPort(22)));
            Consumer<CreateContainerCmd> cmd = e -> e.withHostConfig(new HostConfig().withPortBindings(new PortBinding(Ports.Binding.bindPort(randomPort), new ExposedPort(22))));

            container.stop();
            container.withCreateContainerCmdModifier(cmd);
            container.start();
            String hostname = container.getHost();
            host = new Host("root",hostname,null,container.getMappedPort(22));
            host.setIdentity(identity);
            host.setPassphrase(passphrase);
            hostDefinition = new HostDefinition(host.toString());


        }
    }



    @BeforeClass
    public static void createContainer() {
        setup(getPath("keys/qdup.pub"));

    }
    public static void setup(Path pubPath ){
        String pub = "";
        try {
            pub = Files.readString(pubPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        String pubKey = pub;
        container = new GenericContainer(new ImageFromDockerfile()
           .withDockerfileFromBuilder(builder ->
              builder
                 //.from("alpine:3.2")
                 .from("ubuntu:16.04")
//                 .from("fedora:35")
                 .run("apt-get update && apt-get install -y openssh-server openssh-client rsync && apt-get clean")
//                 .run("dnf install -y openssh-server openssh-clients rsync")
                 .run("mkdir /var/run/sshd")
                 .run("(umask 077 && test -d /root/.ssh || mkdir /root/.ssh)")
                 .run("(umask 077 && touch /root/.ssh/authorized_keys)")
                 .run(" echo \""+pubKey+"\" >> /root/.ssh/authorized_keys")
                 .run("chmod 700 /root/.ssh")
                 .run("chmod 600 /root/.ssh/authorized_keys")
                 //.run("ssh-keygen -A")
                 .run("echo 'root:password' | chpasswd")
                 .run("sed -i 's/PermitRootLogin prohibit-password/PermitRootLogin yes/' /etc/ssh/sshd_config")
                 .run("sed -i 's/#AuthorizedKeysFile.*/AuthorizedKeysFile .ssh\\/authorized_keys/g' /etc/ssh/sshd_config")
                 .run("sed 's@session\\s*required\\s*pam_loginuid.so@session optional pam_loginuid.so@g' -i /etc/pam.d/sshd")
                 .expose(22)
                 .entryPoint("/usr/sbin/sshd -D")
                 .build()))
           .withExposedPorts(22);
        container.start();
        String hostname=container.getHost();
        host = new Host("root",hostname,null,container.getMappedPort(22),null,false,null,null);
        host.setIdentity(getPath("keys/qdup").toFile().getPath());
        hostDefinition = new HostDefinition(host.toString());


    }
    private static Host host;
    private static HostDefinition hostDefinition;

    public SshTestBase(){}

    /**
     * Reads a file on the remote file system
     * @param path
     * @return
     */
    public String readFile(String path){
        return exec("/bin/sh","-c","cat "+path);
    }

    /**
     * Reads a file on the local file system
     * @param path
     * @return
     */
    public String readFile(Path path) {
        StringBuilder contents = new StringBuilder();
        try (Stream<String> lines = Files.lines(path)){
            lines.forEach(line -> {
                if(contents.length()>0){
                    contents.append(System.lineSeparator());
                }
                contents.append(line);
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        return contents.toString();

    }
        public boolean exists(String path){
        String response = exec("/bin/sh","-c","test -f "+path+" && echo \"exists\"").trim();
        return response.contains("exists");
    }
    public String exec(String...commands){
        try {
            return container.execInContainer(commands).getStdout();
        } catch (IOException | InterruptedException e) {
            //e.printStackTrace();
            return e.getMessage();
        }
    }

    public AbstractShell getSession(){
        return getSession(SCHEDULED_THREAD_POOL_EXECUTOR,false);
    }
    public AbstractShell getSession(boolean trace){
        return getSession(SCHEDULED_THREAD_POOL_EXECUTOR,trace);
    }
    public AbstractShell getSession(ScheduledThreadPoolExecutor executor, boolean trace){
        String userHome = System.getProperty("user.home");
        String currentDir = System.getProperty("user.dir");
        String setupCommand = "export FOO=\"foo\"  BAR=\"bar\"";
        AbstractShell shell = AbstractShell.getShell(
                getHost(),
                executor,
                trace
        );
        assertTrue("local ssh session failed to connect",shell.isOpen());
        return shell;
    }

    public static Host getHost(){return host;}
    public static HostDefinition getHostDefinition(){return hostDefinition;}
    public static Host getLocalHost(){
        Host rtrn = new Host();
        return rtrn;
    }
    public static Host getPasswordHost(){
        return new Host(host.getUserName(),host.getHostName(),"password",host.getPort());
    }



    public static InputStream stream(String...input){
        return new ByteArrayInputStream(
                String.join("\n", Arrays.asList(input)).getBytes()
        );
    }

    protected static class TmpDir{
        final Path tempDirWithPrefix;

        private TmpDir() throws IOException {
            tempDirWithPrefix = Files.createTempDirectory("qdup");
        }

        public static TmpDir instance(){
            try {
                return  new TmpDir();
            } catch (IOException e) {
                return null;
            }
        }

        public Path getPath(){
            return this.tempDirWithPrefix;
        }

        public void removeDir(){
            try (Stream<Path> stream = Files.walk(tempDirWithPrefix)){
                stream
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public String toString() {
            return tempDirWithPrefix.toString();
        }
    }
}
