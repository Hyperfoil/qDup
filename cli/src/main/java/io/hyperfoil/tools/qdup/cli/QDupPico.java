package io.hyperfoil.tools.qdup.cli;

import io.hyperfoil.tools.qdup.*;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.RunError;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import io.hyperfoil.tools.qdup.config.yaml.YamlFile;
import io.hyperfoil.tools.yaup.StringUtil;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jboss.logmanager.formatters.ColorPatternFormatter;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.jboss.logmanager.handlers.ConsoleHandler;
import picocli.AutoComplete;
import picocli.CommandLine;
import sun.misc.Signal;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

@QuarkusMain
@CommandLine.Command(name="qdup", description = "performance test automation", mixinStandardHelpOptions = true, subcommands={CommandLine.HelpCommand.class, AutoComplete.GenerateCompletion.class})
public class QDupPico implements Callable<Integer>, QuarkusApplication {

    private static final Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass());

    static class OutputModeGroup {

        @CommandLine.Option(names = {"-Y","--yaml"},description = "print the run yaml without running it", defaultValue = "false")
        boolean yeamlMode;
        @CommandLine.Option(names = {"-T","--test"},description = "test the yaml without running it", defaultValue = "false")
        boolean testMode;
        @CommandLine.Option(names = {"-b","--basePath"},description = "base path for the output folder, creates a new YYYYMMDD_HHmmss sub-folder", defaultValue = "")
        String basePath;
        @CommandLine.Option(names = {"-B","--fullPath"},description = "base path for the output folder, creates a new YYYYMMDD_HHmmss sub-folder", defaultValue = "")
        String fullPath;
    }

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
    OutputModeGroup outputModeGroup;

    @CommandLine.Option(names = {"-c","--commandPool"},description = "number of threads for executing commands", defaultValue = "24")
    int commandPool;
    @CommandLine.Option(names = {"-t","--timeout"},description = "session connection timeout in seconds", defaultValue = "5")
    int sessionTimeout;
    @CommandLine.Option(names = {"-C","--colorTerminal"},description = "flag to enable color formatted console output", defaultValue = "false")
    boolean colorTerminal;
    @CommandLine.Option(names = {"-K","--breakpoint"}, description = "break before starting commands matching the pattern")
    List<String> breakPoints;
    @CommandLine.Option(names = {"-s","--scheduledPool"}, description = "number of threads for executing scheduled tasks", defaultValue = "24")
    int schedulePool;
    @CommandLine.Option(names = {"-S","--state"}, description = "set state parameters")
    Map<String,String> stateParameters;
    @CommandLine.Option(names = {"SX"}, description = "remove a state parameter")
    Set<String> removeStateParameters;
    @CommandLine.Option(names = {"-k","--knownHosts"}, description = "path to known hosts file", defaultValue = "~/.ssh/known_hosts", converter = PathConverter.class)
    String knownHosts;
    @CommandLine.Option(names = {"-p","--passphrase"}, description = "passphrase for identify file", defaultValue = CommandLine.Option.NULL_VALUE)
    String identityPassphrase;
    @CommandLine.Option(names = {"-i","--identity"}, description = "path to identity private key", defaultValue = "~/.ssh/id_rsa", converter = PathConverter.class)
    String identityPath;
    @CommandLine.Option(names = {"-j","--jsonport"}, description = "preferred port for json server", defaultValue = "31337")
    int jsonPort;
    @CommandLine.Option(names = {"-R","--trace"}, description = "trace ssh sessions matching the pattern", defaultValue = "")
    String tracePattern;
    @CommandLine.Option(names = {"-RN","--traceName"}, description = "unique ID pattern for trace files", defaultValue = "")
    String traceNamePattern;
    @CommandLine.Option(names = {"-ix","--ignore-exit-code"}, description = "disable abort on non-zero exit code", defaultValue = "false")
    boolean ignoreExitCode;

    static enum SkipableStage { setup, run, cleanup}

    @CommandLine.Option(names = {"--skip-stages"}, description = "skip specified stages")
    Set<Stage> skipStages;
    @CommandLine.Option(names = {"--"+Globals.STREAM_LOGGING}, description = "log sh output as each line is available", defaultValue = "false")
    boolean streamLogging;

    @CommandLine.Parameters(description = "qdup automation configuration source(s)")
    List<String> yamlPaths;


    @Inject
    @ConfigProperty(name = "qdup.console.format")
    String consoleFormat;

    //@Inject
    Vertx vertx;

    @Override
    public int run(String... args) throws Exception {
        //characters for brail spinner
        //sout("\u2807\u280B\u2819\u2838\u2834\u2826");
        System.setProperty("polyglotimpl.DisableClassPathIsolation", "true");
        CommandLine cmd = new CommandLine(new QDupPico());
        CommandLine gen = cmd.getSubcommands().get("generate-completion");
        gen.getCommandSpec().usageMessage().hidden(true);
        return cmd.execute(args);
    }


    @Override
    public Integer call() throws Exception {
        DateTimeFormatter dt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String uid = dt.format(LocalDateTime.now());


        //colorTerminal = colorTerminal && ConfigProvider.getConfig().getValue("quarkus.console.color",Boolean.class);
        String qDupFormat = ConfigProvider.getConfig().getOptionalValue("qdup.console.format",String.class).orElse("%d{HH:mm:ss.SSS} %-5p %m%n");
        String qDupLevel = ConfigProvider.getConfig().getOptionalValue("qdup.console.level",String.class).orElse("INFO");
        org.jboss.logmanager.Logger qdupLogger = org.jboss.logmanager.Logger.getLogger("io.hyperfoil.tools.qdup");
        //qdupLogger.setUseParentHandlers(false);//to disable double console
        PatternFormatter formatter = colorTerminal ? new ColorPatternFormatter(qDupFormat) : new PatternFormatter(qDupFormat);
        ConsoleHandler consoleHandler = new ConsoleHandler(formatter);
        //consoleHandler.setLevel(Level.ALL);
        //consoleHandler.setLevel(Level.ALL);
        qdupLogger.addHandler(consoleHandler);

        qdupLogger.setLevel(Level.parse(qDupLevel));

        if(!tracePattern.isEmpty() && traceNamePattern.isEmpty()){
            traceNamePattern = uid;
        }


        String outputPath = null;
        if(!outputModeGroup.basePath.isEmpty()){
            outputPath = outputModeGroup.basePath + File.separator + uid;
        }else if (!outputModeGroup.fullPath.isEmpty()){
            outputPath = outputModeGroup.fullPath;
        }else{
            outputPath = "/tmp/"+uid;
        }

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
        String hash = properties.getProperty("hash", "unknown");

        if(yamlPaths.isEmpty()){
            logger.error("missing configuration files");
            return 1;
        }

        Parser yamlParser = Parser.getInstance();
        yamlParser.setAbortOnExitCode(true);
        RunConfigBuilder runConfigBuilder = new RunConfigBuilder();

        boolean ok = true;
        for (String yamlPath : yamlPaths){
            File yamlFile = new File(yamlPath);
            if(!yamlFile.exists()){
                if(yamlPath.startsWith("http")){
                    File tmp = null;
                    try {
                        tmp = File.createTempFile("qdup-",".yaml");
                    } catch (IOException e) {
                        logger.error("Error: cannot create tmp file to try and download " + yamlPath);
                        ok = false;
                    }
                    if(ok) {
                        try (ReadableByteChannel readableByteChannel = Channels.newChannel((new URL(yamlPath)).openStream());
                             FileOutputStream fileOutputStream = new FileOutputStream(tmp.getPath());
                             FileChannel fileChannel = fileOutputStream.getChannel();
                        ) {
                            tmp.deleteOnExit();

                            fileOutputStream.getChannel()
                                    .transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
                            fileChannel.close();
                            fileOutputStream.flush();
                            fileOutputStream.close();

                            logger.trace("loading: " + tmp);
                            YamlFile file = yamlParser.loadFile(tmp.getAbsolutePath());
                            if (file == null) {
                                logger.errorf("Aborting run due to error reading %s", yamlPath);
                                ok = false;
                            }
                            runConfigBuilder.loadYaml(file);
                        } catch (IOException e) {
                            logger.error("Error: failed to download " + yamlPath);
                            ok = false;
                        }
                    }
                } else {
                    logger.error("Error: cannot find " + yamlPath);
                    ok = false;
                }
            } else {
                if (yamlFile.isDirectory()){
                    logger.trace("loading directory: " + yamlPath);
                    for (File child : Objects.requireNonNull(yamlFile.listFiles())) {
                        if (child.getName().endsWith(".yaml") || child.getName().endsWith(".yml")) {
                            logger.trace("  loading: " + child.getPath());
                            //String content = FileUtility.readFile(child.getPath());
                            YamlFile file = yamlParser.loadFile(child.getPath());
                            if (file == null) {
                                logger.errorf("Aborting run due to error reading %s", child.getPath());
                                ok = false;
                            }
                            runConfigBuilder.loadYaml(file);
                        } else {
                            logger.trace("  skipping: " + child.getPath());
                        }
                    }
                } else {
                    logger.trace("loading: " + yamlPath);
                    YamlFile file = yamlParser.loadFile(yamlPath);
                    if (file == null) {
                        logger.errorf("Aborting run due to error reading %s", yamlPath);
                        ok = false;
                    }
                    runConfigBuilder.loadYaml(file);
                }
            }
        }

        if(!ok){
            return 1;
        }
        if(stateParameters!=null && !stateParameters.isEmpty()){
            stateParameters.forEach((k,v)->{
                if (v != null && !v.toString().trim().isEmpty()) {
                    runConfigBuilder.forceRunState(k.toString(), v.toString());
                }
            });
        }
        if(removeStateParameters!=null && !removeStateParameters.isEmpty()){
            removeStateParameters.forEach((k)->{
                if (k != null) {
                    runConfigBuilder.forceRunState(k, "");
                }
            });
        }
        if(!tracePattern.isEmpty()){
            runConfigBuilder.trace(tracePattern);
        }
        if(!RunConfigBuilder.DEFAULT_IDENTITY.equals(identityPath)){
            runConfigBuilder.setIdentity(identityPath);
        }
        if(RunConfigBuilder.DEFAULT_PASSPHRASE != identityPassphrase){
            runConfigBuilder.setPassphrase(identityPassphrase);
        }
        if(!RunConfigBuilder.DEFAULT_KNOWN_HOSTS.equals(knownHosts)){
            runConfigBuilder.setKnownHosts(knownHosts);
        }
        if(skipStages!=null){ skipStages.forEach(runConfigBuilder::addSkipStage); }
        runConfigBuilder.setStreamLogging(streamLogging);

        String runConsoleFormat = ConfigProvider.getConfig().getOptionalValue("qdup.run.console.format",String.class).orElse(RunConfigBuilder.DEFAULT_RUN_CONSOLE_FORMAT);
        runConfigBuilder.setConsoleFormatPattern(runConsoleFormat);

        RunConfig config = runConfigBuilder.buildConfig(yamlParser);
        if(outputModeGroup.testMode){
            System.out.printf("%s", config.debug(true));
            return config.hasErrors() ? 1 : 0;
        }else if (outputModeGroup.yeamlMode){
            YamlFile file = runConfigBuilder.toYamlFile();
            System.out.printf("%s",yamlParser.dump(file));
            return 0;
        }

        File outputFile = new File(outputPath);
        if(!outputFile.exists()){
            outputFile.mkdirs();
        }

        config.setColorTerminal(colorTerminal);


        if(config.hasErrors()){
            config.getErrors().stream().map(RunError::toString).forEach(error -> {
                System.out.printf("%s%n", error);
            });
            return 1;
        }

        if(!tracePattern.isEmpty()){
            config.getGlobals().addSetting(RunConfig.TRACE_NAME, traceNamePattern);
        }

        final AtomicInteger factoryCounter = new AtomicInteger(0);
        final AtomicInteger scheduledCounter = new AtomicInteger(0);
        final AtomicInteger callbackCounter = new AtomicInteger(0);

        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();

        final Thread.UncaughtExceptionHandler uncaughtExceptionHandler = (thread, throwable) -> {
            logger.error("UNCAUGHT:" + thread.getName() + " " + throwable.getMessage(), throwable);
        };

        ThreadFactory factory = r -> {
            Thread rtrn = new Thread(r, "qdup-command-" + factoryCounter.getAndIncrement());
            rtrn.setUncaughtExceptionHandler(uncaughtExceptionHandler);
            return rtrn;
        };

        ThreadPoolExecutor executor = new ThreadPoolExecutor(commandPool / 2, commandPool, 30, TimeUnit.MINUTES, workQueue, factory);

        ScheduledThreadPoolExecutor scheduled = new ScheduledThreadPoolExecutor(schedulePool, runnable -> new Thread(runnable, "qdup-scheduled-" + scheduledCounter.getAndIncrement()));
        ScheduledThreadPoolExecutor callback = new ScheduledThreadPoolExecutor(schedulePool, runnable -> new Thread(runnable, "qdup-callback-" + callbackCounter.getAndIncrement()));

        Dispatcher dispatcher = new Dispatcher(executor, scheduled, callback);

        if (System.console()== null){
            logger.info("running with detached console");
        }

        config.getGlobals().addSetting("check-exit-code", !ignoreExitCode);
        final Run run = new Run(outputPath, config, dispatcher);
        run.ensureConsoleLogging();
        logger.infof("Running qDup version %s @ %s", version, hash);
        logger.info("output path = " + run.getOutputPath());
        if(!ignoreExitCode){
            logger.info("shell exit code checks enabled");
        }
        if(config.isStreamLogging()){
            logger.info("stream logging enabled");
        }

        Signal.handle(new Signal("INT"),(signal)->{
            if (!run.isAborted() && dispatcher.isRunning() && !dispatcher.isStopping()) {
                run.abort(false);
                Optional<Thread> mainThread = Thread.getAllStackTraces().keySet().stream().filter(t -> t.getName().equals("main")).findFirst();
                if (mainThread.isPresent()) {
                    run.joinLatch(120,TimeUnit.SECONDS);
                }
            } else if (Stage.Cleanup.equals(run.getStage()) && dispatcher.isRunning() && !dispatcher.isStopping()){
                run.abort(true);
            }
        });

        //TODO this should NOT be a system property
        boolean startJsonServer = !Boolean.parseBoolean(System.getProperty("disableRestApi", "false"));
        JsonServer jsonServer = null;
        if (startJsonServer) {
            vertx = Arc.container().instance(Vertx.class).get(); // Programmatic lookup
            jsonServer = new JsonServer(vertx, run, jsonPort);
            if(breakPoints!=null){ breakPoints.forEach(jsonServer::addBreakpoint);}
            jsonServer.start();
        }

        long start = System.currentTimeMillis();

        run.run();
        long stop = System.currentTimeMillis();
        System.out.printf("Finished in %s at %s%n", StringUtil.durationToString(stop - start), run.getOutputPath());
        if (startJsonServer) {
            jsonServer.stop();
        }
        dispatcher.shutdown();
        executor.shutdownNow();
        scheduled.shutdownNow();
        callback.shutdownNow();
        return run.isAborted() ? 1 : 0;
    }

}
