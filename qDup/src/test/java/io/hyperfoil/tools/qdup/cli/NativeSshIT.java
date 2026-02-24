package io.hyperfoil.tools.qdup.cli;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Device;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainIntegrationTest;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusMainIntegrationTest
class NativeSshIT {

    private static GenericContainer<?> container;
    private static String hostString;

    @BeforeAll
    public static void startContainer() throws IOException {
        Path rsaPub = getPath("keys/qdup.pub");
        Path ed25519Pub = getPath("keys/qdup_ed25519.pub");
        Path passwordPub = getPath("keys/qdup.password.pub");

        String authorizedKeys = Files.readString(rsaPub) + "\n"
                + Files.readString(ed25519Pub) + "\n"
                + Files.readString(passwordPub) + "\n";

        container = new GenericContainer<>(new ImageFromDockerfile("local/qdup-testcontainer-native", false)
                .withFileFromClasspath("Dockerfile", "Dockerfile"))
                .withPrivilegedMode(true)
                .withImagePullPolicy(PullPolicy.defaultPolicy())
                .withCopyToContainer(Transferable.of(authorizedKeys.getBytes()), "/root/.ssh/authorized_keys")
                .withCreateContainerCmdModifier(cmd -> {
                    ((CreateContainerCmd) cmd).getHostConfig().withSecurityOpts(List.of("label=disable"));
                    ((CreateContainerCmd) cmd).getHostConfig().withDevices(Device.parse("/dev/fuse"));
                })
                .withExposedPorts(22);
        container.start();

        hostString = "root@" + container.getHost() + ":" + container.getMappedPort(22);
    }

    @AfterAll
    public static void stopContainer() {
        if (container != null) {
            container.stop();
        }
    }

    private static Path getPath(String subDir) {
        return Paths.get(
                NativeSshIT.class.getProtectionDomain().getCodeSource().getLocation().getPath()
        ).resolve(Paths.get(subDir));
    }

    private Path writeYaml(String hostTarget) throws IOException {
        Path configPath = Files.writeString(File.createTempFile("qdup-native", ".yaml").toPath(),
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
                """.replace("HOST_TARGET", hostTarget));
        configPath.toFile().deleteOnExit();
        return configPath;
    }

    private void assertKeyConnect(QuarkusMainLauncher launcher, String keyName, String passphrase) throws IOException {
        Path configPath = writeYaml(hostString);
        String identity = getPath("keys/" + keyName).toFile().getPath();
        if (passphrase != null) {
            LaunchResult result = launcher.launch("--fullPath", "/tmp", "--identity", identity,
                    "--passphrase", passphrase, configPath.toString());
            assertEquals(0, result.exitCode(), keyName + " connection failed:\n" + result.getErrorOutput());
        } else {
            LaunchResult result = launcher.launch("--fullPath", "/tmp", "--identity", identity, configPath.toString());
            assertEquals(0, result.exitCode(), keyName + " connection failed:\n" + result.getErrorOutput());
        }
    }

    @Test
    public void rsa_key_connect(QuarkusMainLauncher launcher) throws IOException {
        assertKeyConnect(launcher, "qdup", null);
    }

    @Test
    public void ed25519_key_connect(QuarkusMainLauncher launcher) throws IOException {
        assertKeyConnect(launcher, "qdup_ed25519", null);
    }

    @Test
    public void passphrase_key_connect(QuarkusMainLauncher launcher) throws IOException {
        assertKeyConnect(launcher, "qdup.password", "password");
    }
}
