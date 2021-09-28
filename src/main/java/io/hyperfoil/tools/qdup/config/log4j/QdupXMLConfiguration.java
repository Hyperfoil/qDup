package io.hyperfoil.tools.qdup.config.log4j;

import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.yaup.AsciiArt;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;
import org.apache.logging.log4j.core.layout.PatternLayout;

public class QdupXMLConfiguration extends XmlConfiguration {
    public QdupXMLConfiguration(LoggerContext loggerContext, ConfigurationSource configSource) {
        super(loggerContext, configSource);
    }

    @Override
    protected void doConfigure() {
        super.doConfigure();
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        if( !config.getLoggers().containsKey("qdup") ){
            ConsoleAppender consoleAppender = ConsoleAppender.newBuilder()
                    .setName("qdup-console")
                    .setLayout(
                            PatternLayout.newBuilder()
                                    .withPattern("%d{HH:mm:ss.SSS} %msg%n%throwable")
                                    .build()
                    ).build();
            consoleAppender.start();
            LoggerConfig loggerConfig = LoggerConfig.createLogger(true, Level.ALL, "qdup","false",new AppenderRef[0],null,config,null);
            addLogger("qdup", loggerConfig);
            loggerConfig.addAppender(consoleAppender,Level.ALL,null);
            ctx.updateLoggers();
        }
    }
}
