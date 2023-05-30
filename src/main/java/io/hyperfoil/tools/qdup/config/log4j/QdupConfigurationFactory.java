package io.hyperfoil.tools.qdup.config.log4j;

import io.hyperfoil.tools.yaup.AsciiArt;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.*;
import org.apache.logging.log4j.core.config.builder.api.Component;
import org.apache.logging.log4j.core.config.json.JsonConfiguration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.properties.PropertiesConfiguration;
import org.apache.logging.log4j.core.config.properties.PropertiesConfigurationBuilder;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;
import org.apache.logging.log4j.core.config.yaml.YamlConfiguration;

import java.util.Properties;

//@Plugin(name = "QdupConfigurationFactory", category = "ConfigurationFactory")
//@Order(99_999)
public class QdupConfigurationFactory extends ConfigurationFactory {

    /**
     * Valid file extensions for XML files.
     */
    public static final String[] SUFFIXES = new String[] {".xml",".properties"};

    public QdupConfigurationFactory(){
    }

    @Override
    protected String[] getSupportedTypes() {
        return SUFFIXES;
    }

    @Override
    public Configuration getConfiguration(LoggerContext loggerContext, ConfigurationSource source) {
        String sourceStr = source.getLocation();
        AbstractConfiguration configuration = null;
        if(sourceStr.endsWith("xml")) {

            //configuration = new XmlConfiguration(loggerContext,source);
            configuration = new QdupXMLConfiguration(loggerContext,source);
        }else if (sourceStr.endsWith("properties") || sourceStr.endsWith("prop")){
            Properties properties = new Properties();
            configuration = (new PropertiesConfigurationBuilder()).setConfigurationSource(source).setRootProperties(properties).setLoggerContext(loggerContext).build();
        }else if (sourceStr.endsWith("json") || sourceStr.endsWith("jsn")){
            configuration = new JsonConfiguration(loggerContext,source);
        }else if (sourceStr.endsWith("yaml") || sourceStr.endsWith("yml")){
            configuration = new YamlConfiguration(loggerContext,source);
        }else{
            configuration = new DefaultConfiguration();
        }
        return new QdupConfiguration(loggerContext, source, configuration);
    }


}
