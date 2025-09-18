package io.hyperfoil.tools.qdup.cli;

import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import picocli.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class QDupVersionProvider implements CommandLine.IVersionProvider {

    @Override
    public String[] getVersion() throws Exception {
        Properties properties = new Properties();
        try (InputStream is = QDup.class.getResourceAsStream("/META-INF/MANIFEST.MF")) {//Implementation-Version
            if (is != null) {
                properties.load(is);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try (InputStream is = QDup.class.getResourceAsStream("/META-INF/maven/io.hyperfoil.tools/qDup-core/pom.properties")) {//version
            if (is != null) {
                properties.load(is);

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try (InputStream is = QDup.class.getResourceAsStream("/application.properties")) {
            if (is != null) {
                properties.load(is);

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        String version = properties.getProperty("version", "unknown");
        String hash = properties.getProperty("qdup.application.gitHash","unknown");

        return new String[]{ (version.contains("SNAPSHOT") ? version+" @ "+hash : version)};
    }
}
