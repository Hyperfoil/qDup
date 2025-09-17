package io.hyperfoil.tools.qdup.cli;

import io.hyperfoil.tools.qdup.QDup;
import picocli.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class QDupVersionProvider implements CommandLine.IVersionProvider {
    @Override
    public String[] getVersion() throws Exception {
        Properties properties = new Properties();
        try (InputStream is = QDup.class.getResourceAsStream("/META-INF/MANIFEST.MF")) {
            if (is != null) {
                properties.load(is);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try (InputStream is = QDup.class.getResourceAsStream("/META-INF/maven/io.hyperfoil.tools/qDup/pom.properties")) {
            if (is != null) {
                properties.load(is);

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        String version = properties.getProperty("version", "unknown");

        return new String[]{version};
    }
}
