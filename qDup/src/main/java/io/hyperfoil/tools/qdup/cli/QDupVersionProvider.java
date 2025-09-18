package io.hyperfoil.tools.qdup.cli;

import io.hyperfoil.tools.qdup.QDup;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import picocli.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class QDupVersionProvider implements CommandLine.IVersionProvider {

    //This was not injection :(
    @Inject
    @ConfigProperty(name = "quarkus.application.gitHash",defaultValue = "deadbeef")
    private String gitHash;

    @Override
    public String[] getVersion() throws Exception {
        Properties properties = new Properties();
        try (InputStream is = QDupPico.class.getResourceAsStream("/META-INF/MANIFEST.MF")) {//Implementation-Version
            if (is != null) {
                properties.load(is);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try (InputStream is = QDupPico.class.getResourceAsStream("/META-INF/maven/io.hyperfoil.tools/qDup-core/pom.properties")) {//version
            if (is != null) {
                properties.load(is);

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try (InputStream is = QDupPico.class.getResourceAsStream("/application.properties")) {
            if (is != null) {
                properties.load(is);

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        String version = properties.getProperty("version", "unknown");
        String hash = properties.getProperty("quarkus.application.gitHash","unknown");

        return new String[]{ (version.contains("SNAPSHOT") ? version+" @ "+hash : version)};
    }
}
