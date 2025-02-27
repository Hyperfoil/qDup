package io.hyperfoil.tools.qdup.config.log4j;

import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.yaup.AsciiArt;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.*;
import org.apache.logging.log4j.core.layout.PatternLayout;

public class QdupConfiguration extends AbstractConfiguration {

    private final AbstractConfiguration configuration;
    protected QdupConfiguration(LoggerContext loggerContext, ConfigurationSource configurationSource, AbstractConfiguration configuration) {
        super(loggerContext, configurationSource);
        this.configuration = configuration;
    }

    @Override
    public void initialize() {
        configuration.initialize();
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
            config.addLogger("qdup",loggerConfig);
            loggerConfig.addAppender(consoleAppender,Level.ALL,null);
            config.getLoggerConfig("qdup").addAppender(consoleAppender,Level.ALL,null);
            ctx.updateLoggers();


        }else{
        }
    }
}
