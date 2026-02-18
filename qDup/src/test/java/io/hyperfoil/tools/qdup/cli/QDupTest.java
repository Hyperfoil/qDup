package io.hyperfoil.tools.qdup.cli;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Device;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusMainTest
class QDupTest {


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
        container = new GenericContainer(new ImageFromDockerfile("local/qdup-testcontainer-cli",false)
                .withFileFromClasspath("Dockerfile",dockerfile))//"Dockerfile"))
                .withPrivilegedMode(true)
                .withImagePullPolicy(PullPolicy.defaultPolicy())
                .withCopyToContainer(Transferable.of(Files.readAllBytes(pubPath)),"/root/.ssh/authorized_keys")
                .withCreateContainerCmdModifier(cmd->{
                    ((CreateContainerCmd)cmd).getHostConfig().withSecurityOpts(List.of("label=disable"));
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

    public static Path getPath(String subDir){
        return  Paths.get(
                QDupTest.class.getProtectionDomain().getCodeSource().getLocation().getPath()
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
        Path configPath = Files.writeString(File.createTempFile("qdup",".yaml").toPath(),
                """
                scripts:
                  whoami:
                  - sh: whoami
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
        assertEquals(0,result.exitCode());


    }


    @Test
    public void invalid_yaml(QuarkusMainLauncher launcher) throws IOException {
        Path configPath = Files.writeString(File.createTempFile("qdup",".yaml").toPath(),
                """
                scripts:
                  invalid:
                  - invalidCmd
                hosts:
                  target: HOST_TARGET
                roles:
                  test:
                    hosts:
                    - target
                    run-scripts:
                    - invalid
                """.replaceAll("HOST_TARGET",getHost().toString()));
        configPath.toFile().deleteOnExit();
        LaunchResult result = launcher.launch("--fullPath","/tmp","--identity",getIdentity(),configPath.toString());
        assertTrue(result.getOutput().contains("Failed to load"));
    }

    @Test
    public void main_exit_sh(QuarkusMainLauncher launcher) throws IOException {
        Path configPath = Files.writeString(File.createTempFile("qdup",".yaml").toPath(),
                """
                scripts:
                  doit:
                  - sh: whoami; (exit 42);
                  - set-state: RUN.foo true
                hosts:
                  target: HOST_TARGET
                roles:
                  test:
                    hosts:
                    - target
                    run-scripts:
                    - doit
                """.replaceAll("HOST_TARGET",getHost().toString()));
        configPath.toFile().deleteOnExit();
        LaunchResult result = launcher.launch("--fullPath","/tmp","--identity",getIdentity(),configPath.toString());
        assertEquals(1,result.exitCode());
    }
    @Test
    public void main_exit_sh_ignore(QuarkusMainLauncher launcher) throws IOException {
        Path configPath = Files.writeString(File.createTempFile("qdup",".yaml").toPath(),
                """
                scripts:
                  doit:
                  - sh: whoami; (exit 42);
                  - set-state: RUN.foo true
                hosts:
                  target: HOST_TARGET
                roles:
                  test:
                    hosts:
                    - target
                    run-scripts:
                    - doit
                """.replaceAll("HOST_TARGET",getHost().toString()));
        configPath.toFile().deleteOnExit();
        LaunchResult result = launcher.launch("--ignore-exit-code", "--fullPath","/tmp","--identity",getIdentity(),configPath.toString());
        assertEquals(0,result.exitCode());
    }
    @Test
    public void stream_logging(QuarkusMainLauncher launcher) throws IOException {
        Path configPath = Files.writeString(File.createTempFile("qdup",".yaml").toPath(),
                """
                scripts:
                  doit:
                  - sh: echo -e "one\\ntwo\\nthree"
                hosts:
                  target: HOST_TARGET
                roles:
                  test:
                    hosts:
                    - target
                    run-scripts:
                    - doit
                """.replaceAll("HOST_TARGET",getHost().toString()));
        configPath.toFile().deleteOnExit();
        LaunchResult result = launcher.launch("--stream-logging", "--fullPath","/tmp","--identity",getIdentity(),configPath.toString());
        assertEquals(0,result.exitCode());
        File runLog = new File("/tmp/run.log");
        assertTrue(runLog.exists());
        String content = Files.readString(runLog.toPath());

        assertTrue(content.contains("] one"),"expending one with log prefix:\n"+content);
        assertTrue(content.contains("] two"),"expending two with log prefix:\n"+content);
        assertTrue(content.contains("] three"),"expending three with log prefix:\n"+content);

    }

    @Test
    public void main_exit_invalid_yaml(QuarkusMainLauncher launcher) throws IOException {
        Path configPath = Files.writeString(File.createTempFile("qdup",".yaml").toPath(),
                """
                scripts:
                  doit
                  - sh: whoami
                    - set-state: RUN.foo true
                hosts:
                  target: HOST_TARGET
                roles:
                  test:
                    hosts:
                    - target
                    run-scripts:
                    - doit
                """.replaceAll("HOST_TARGET",getHost().toString()));
        configPath.toFile().deleteOnExit();
        LaunchResult result = launcher.launch("--fullPath","/tmp","--identity",getIdentity(),configPath.toString());
        assertEquals(1,result.exitCode());
    }

    @Test
    public void yaml_with_else(QuarkusMainLauncher launcher) throws IOException {
        Path configPath = Files.writeString(File.createTempFile("qdup",".yaml").toPath(),
                """
                scripts:
                  doit:
                  - sh: cat log
                  - regex: foo
                    then:
                    - sh: echo 'found'
                    else:
                    - sh: echo 'lost'
                hosts:
                  target: HOST_TARGET
                roles:
                  test:
                    hosts:
                    - target
                    run-scripts:
                    - doit
                """.replaceAll("HOST_TARGET",getHost().toString()));
        configPath.toFile().deleteOnExit();
        LaunchResult result = launcher.launch("-Y","--identity",getIdentity(),configPath.toString());
        assertEquals(0,result.exitCode());
        assertNotNull(result.getOutput());



    }
}
