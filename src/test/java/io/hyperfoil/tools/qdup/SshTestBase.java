package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import org.junit.After;
import org.junit.Before;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * This base test class provides and way to run the qDup scripts locally.
 * Easy for testing and debugging.
 * The side effect of this class is that the tests are going to change your local environment.
 */
public class SshTestBase {

    protected SshContainerTestBase.TmpDir tmpDir;

    @Before
    public void setup(){
        tmpDir = SshContainerTestBase.TmpDir.instance();
    }

    @After
    public void cleanUp(){
        //tmpDir.removeDir();
        tmpDir = null;
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

    public String getIdentity() {
        return getPath("keys/qdup").toFile().getPath();
    }

    public static Path getPath(String subDir){
        return  Paths.get(
                SshContainerTestBase.class.getProtectionDomain().getCodeSource().getLocation().getPath()
        ).resolve(
                Paths.get(subDir)
        );
    }

    public static InputStream stream(String...input){
        return new ByteArrayInputStream(
                String.join("\n", Arrays.asList(input)).getBytes()
        );
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
}
