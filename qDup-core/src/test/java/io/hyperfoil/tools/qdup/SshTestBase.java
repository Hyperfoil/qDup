package io.hyperfoil.tools.qdup;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.*;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.HostDefinition;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import io.hyperfoil.tools.qdup.shell.AbstractShell;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.MountableFile;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;

public class SshTestBase {

    private static volatile String detectedPlatform;

    public static String getContainerPlatform() {
        if (detectedPlatform == null) {
            synchronized (SshTestBase.class) {
                if (detectedPlatform == null) {
                    // Try podman first, then docker
                    for (String candidate : new String[]{"podman", "docker"}) {
                        try {
                            Process p = new ProcessBuilder("which", candidate)
                                    .redirectErrorStream(true)
                                    .start();
                            int exit = p.waitFor();
                            if (exit == 0) {
                                detectedPlatform = candidate;
                                break;
                            }
                        } catch (IOException | InterruptedException ignored) {
                        }
                    }
                    if (detectedPlatform == null) {
                        throw new IllegalStateException("Neither podman nor docker found on PATH");
                    }
                }
            }
        }
        return detectedPlatform;
    }

    public static List<String> getCreateConnectedContainerCmd() {
        return "podman".equals(getContainerPlatform())
                ? Host.PODMAN_CREATE_CONNECTED_CONTAINER
                : Host.DOCKER_CREATE_CONNECTED_CONTAINER;
    }

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

    private static final ScheduledThreadPoolExecutor SCHEDULED_THREAD_POOL_EXECUTOR = new ScheduledThreadPoolExecutor(4);

    //added to simplify exchanging files with test container that uses identity
    public Local getLocal(){
        return new Local(getBuilder().buildConfig(Parser.getInstance()));        
    }
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
    public static String readUrl(String url){
        return readUrl(url,0);
    }
    public static String readUrl(String url,int timeout){
        if(url == null || url.isBlank()){
            return "";
        }
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                StringBuilder contentBuffer = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if(!contentBuffer.isEmpty()){
                        contentBuffer.append(System.lineSeparator());
                    }
                    contentBuffer.append(line);

                }
                return contentBuffer.toString();
            }
        } catch (IOException e) {
            return e.getMessage();
        }


    }

    public String getIdentity() {
        return getPath("keys/qdup").toFile().getPath();
    }

    public static Path getPath(String subDir){
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
        try {
            setup(getPath("keys/qdup.pub"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static void setup(Path pubPath ) throws IOException {
        setup(pubPath,"Dockerfile");
    }
    public static void setup(Path pubPath, String dockerfile) throws IOException {
        String pub = "";
        try {
            pub = Files.readString(pubPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //MountableFile mountableFile = MountableFile.forClasspathResource("keys/qdup.pub",Integer.parseInt("644",8));
        String pubKey = pub;
        container = new GenericContainer(new ImageFromDockerfile("local/qdup-testcontainer-zsh",false)
                .withFileFromClasspath("Dockerfile",dockerfile))//"Dockerfile"))
                .withPrivilegedMode(true)
                .withImagePullPolicy(PullPolicy.defaultPolicy())
                .withCopyToContainer(Transferable.of(Files.readAllBytes(pubPath)),"/root/.ssh/authorized_keys")
                .withCreateContainerCmdModifier(cmd->{
                    ((CreateContainerCmd)cmd).getHostConfig().withSecurityOpts(List.of("label=disable","unmask=ALL"));
                    ((CreateContainerCmd) cmd).getHostConfig().withDevices(Device.parse("/dev/fuse"));
                })
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
        host = new Host("root",hostname,null,container.getMappedPort(22),null,true,false,null,null);
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
    public String readLocalFile(Path path) {
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
        return getSession(SCHEDULED_THREAD_POOL_EXECUTOR);
    }
    public AbstractShell getSession(ScheduledThreadPoolExecutor executor){
        String userHome = System.getProperty("user.home");
        String currentDir = System.getProperty("user.dir");
        String setupCommand = "export FOO=\"foo\"  BAR=\"bar\"";
        AbstractShell shell = AbstractShell.getShell(
                Thread.currentThread().getStackTrace()[1].getMethodName(),
                getHost(),
                executor,
                new SecretFilter(),
                null
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
