package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.qdup.cmd.*;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.RunError;
import io.hyperfoil.tools.qdup.config.log4j.QdupConfigurationFactory;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.impl.Log4jContextFactory;
import org.apache.logging.log4j.core.util.DefaultShutdownCallbackRegistry;
import org.apache.logging.log4j.spi.LoggerContextFactory;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import io.hyperfoil.tools.qdup.config.yaml.YamlFile;
import io.hyperfoil.tools.yaup.AsciiArt;
import io.hyperfoil.tools.yaup.StringUtil;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class QDup {

    private static final XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());
    private final String knownHosts;
    private final String identity;
    private final String passphrase;
    private final int timeout;
    private final Properties stateProps;
    private final Properties removeStateProperties;
    private final String trace;
    private final String traceName;

    private String outputPath;
    private String version;
    private String hash;

    private List<String> yamlPaths;
    private int scheduledThreads;
    private int commandThreads;
    private boolean test;
    private boolean yaml;
    private List<String> breakpoints;
    private boolean colorTerminal;
    private int jsonPort;

    private List<Stage> skipStages;

    private boolean exitCode = false;

    private RunConfig config;

    private Parser yamlParser;


    public boolean checkExitCode(){return exitCode;}

    public boolean isColorTerminal() {
        return colorTerminal;
    }

    public List<String> getBreakpoints() {
        return breakpoints;
    }

    public List<String> getYamlPaths() {
        return yamlPaths;
    }

    public int getScheduledThreads() {
        return scheduledThreads;
    }

    public int getCommandThreads() {
        return commandThreads;
    }

    public String getKnownHosts() {
        return knownHosts;
    }

    public String getIdentity() {
        return identity;
    }

    public String getPassphrase() {
        return passphrase;
    }

    public int getTimeout() {
        return timeout;
    }

    public Properties getStateProps() {
        return stateProps;
    }

    public Properties getRemoveStateProperties() {
        return removeStateProperties;
    }

    public String getTrace() {
        return trace;
    }

    public boolean hasTrace() {
        return trace != null && !trace.isBlank();
    }

    public boolean isTest() { return test; }

    public boolean isYaml() { return yaml; }

    public String getOutputPath() {
        return outputPath;
    }

    public int getJsonPort() {
        return jsonPort;
    }

    public String getVersion() {
        return version;
    }

    public String getHash() {
        return hash;
    }

    public boolean hasSkipStages(){
        return !skipStages.isEmpty();
    }

    public List<Stage> getSkipStages(){
        return skipStages;
    }

    public String getRunDebug(){ return config == null ? null : config.debug(true); };

    public Parser getYamlParser() {
        return yamlParser;
    }

    public QDup(String... args) {
        Options options = new Options();

        OptionGroup basePathGroup = new OptionGroup();
        basePathGroup.addOption(Option.builder("Y")
                .longOpt("yaml")
                .required()
                .desc("print the run yaml without running it")
                .build()
        );
        basePathGroup.addOption(Option.builder("T")
                .longOpt("test")
                .required()
                .desc("test the yaml without running it")
                .build()
        );
        basePathGroup.addOption(Option.builder("b")
                .longOpt("basePath")
                .required()
                .hasArg()
                .argName("path")
                .desc("base path for the output folder, creates a new YYYYMMDD_HHmmss sub-folder")
                .build()
        );
        basePathGroup.addOption(Option.builder("B")
                .longOpt("fullPath")
                .required()
                .hasArg()
                .argName("path")
                .desc("full path for the output folder, does not create a sub-folder")
                .build()
        );

        basePathGroup.setRequired(false);

        options.addOptionGroup(basePathGroup);

        options.addOption(
                Option.builder("c")
                        .longOpt("commandPool")
                        .hasArg()
                        .argName("count")
                        .type(Integer.TYPE)
                        .desc("number of threads for executing commands [24]")
                        .build()
        );

        options.addOption(
                Option.builder("t")
                        .longOpt("timeout")
                        .hasArg()
                        .argName("seconds")
                        .type(Integer.TYPE)
                        .desc("session connection timeout in seconds, default 5s")
                        .build()
        );

        options.addOption(
                Option.builder("C")
                        .longOpt("colorTerminal")
                        .hasArg(false)
                        .desc("flag to enable color formatted terminal")
                        .build()
        );
        options.addOption(
                Option.builder("K")
                        .longOpt("breakpoint")
                        .hasArg(true)
                        .argName("breakpoint")
                        .desc("break before starting commands matching the pattern")
                        .build()
        );

        options.addOption(
                Option.builder("s")
                        .longOpt("scheduledPool")
                        .hasArg()
                        .argName("count")
                        .type(Integer.TYPE)
                        .desc("number of threads for executing scheduled tasks [4]")
                        .build()
        );

        options.addOption(
                Option.builder("S")
                        .argName("KEY=VALUE")
                        .desc("set a state parameter")
                        .hasArgs()
                        .numberOfArgs(2)
                        .valueSeparator()
                        .build()
        );

        options.addOption(
                Option.builder("SX")
                        .argName("KEY")
                        .desc("remove a state parameter")
                        .hasArg()
                        .build()
        );

        options.addOption(
                Option.builder("k")
                        .longOpt("knownHosts")
                        .desc("qdup known hosts path [~/.ssh/known_hosts]")
                        .hasArg()
                        .argName("path")
                        .type(String.class)
                        .build()
        );

        options.addOption(
                Option.builder("p")
                        .longOpt("passphrase")
                        .desc("qdup passphrase for identify file [null]")
                        .hasArg()
                        .optionalArg(true)
                        .argName("password")
                        .type(String.class)
                        .build()
        );
        options.addOption(
                Option.builder("i")
                        .longOpt("identity")
                        .argName("path")
                        .hasArg()
                        .desc("qdup identity path [~/.ssh/id_rsa]")
                        .type(String.class)
                        .build()
        );

        options.addOption(
                Option.builder("j")
                        .longOpt("jsonport")
                        .argName("port")
                        .hasArg()
                        .desc("preferred port for json server [31337]")
                        .type(Integer.class)
                        .build()
        );
        options.addOption(
                Option.builder("R")
                        .longOpt("trace")
                        .argName("pattern")
                        .hasArg()
                        .desc("trace ssh sessions matching the pattern")
                        .type(String.class)
                        .build()
        );
        options.addOption(
                Option.builder("RN")
                        .longOpt("traceName")
                        .argName("pattern")
                        .hasArg()
                        .desc("unique ID pattern for trace files")
                        .type(String.class)
                        .build()
        );

        //exit code checking
        options.addOption(
                Option.builder("x")
                        .longOpt("exitCode")
                        .desc("flag to enable exit code checking")
                        .build()
        );

        //only run specific stages
        options.addOption(
                Option.builder()
                    .longOpt("skip-stages")
                    .hasArgs()
                    .argName("stage")
                    .valueSeparator(',')
                    .desc("only perform specific stages")
                    .build()
        );

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine commandLine = null;

        String cmdLineSyntax = "[options] [yamlFiles]";

        cmdLineSyntax =
                "java -jar " +
                        (new File(QDup.class
                                .getProtectionDomain()
                                .getCodeSource()
                                .getLocation()
                                .getPath()
                        )).getName() +
                        " " +
                        cmdLineSyntax;

        if (args.length == 0) {
            formatter.printHelp(cmdLineSyntax, options);
        }

        try {
            commandLine = parser.parse(options, args);
        } catch (ParseException e) {
            logger.error(e.getMessage(), e);
            formatter.printHelp(cmdLineSyntax, options);
        }

        //load a custom logback configuration
//        if (commandLine.hasOption("logback")) {
//            String configPath = commandLine.getOptionValue("logback");
//            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
//
//            try {
//                JoranConfigurator configurator = new JoranConfigurator();
//                configurator.setContext(context);
//                context.reset();
//                configurator.doConfigure(configPath);
//            } catch (JoranException je) {
//                // StatusPrinter will handle this
//            }
//            StatusPrinter.printInCaseOfErrorsOrWarnings(context);
//        }

        knownHosts = commandLine.getOptionValue("knownHosts", RunConfigBuilder.DEFAULT_KNOWN_HOSTS);
        identity = commandLine.getOptionValue("identity", RunConfigBuilder.DEFAULT_IDENTITY);
        passphrase = commandLine.getOptionValue("passphrase", RunConfigBuilder.DEFAULT_PASSPHRASE);
        timeout = Integer.parseInt(commandLine.getOptionValue("timeout", "" + RunConfigBuilder.DEFAULT_SSH_TIMEOUT));
        commandThreads = Integer.parseInt(commandLine.getOptionValue("commandPool", "24"));
        scheduledThreads = Integer.parseInt(commandLine.getOptionValue("scheduledPool", "24"));
        yamlPaths = commandLine.getArgList();
        stateProps = commandLine.getOptionProperties("S");
        removeStateProperties = commandLine.getOptionProperties("SX");
        test = commandLine.hasOption("test");
        yaml = commandLine.hasOption("yaml");
        breakpoints = commandLine.hasOption("breakpoint") ? Arrays.asList(commandLine.getOptionValues("breakpoint")) : Collections.EMPTY_LIST;
        colorTerminal = commandLine.hasOption("colorTerminal");
        jsonPort = Integer.parseInt(commandLine.getOptionValue("jsonport", "" + JsonServer.DEFAULT_PORT));

        exitCode = commandLine.hasOption("exitCode");

        outputPath = null;
        DateTimeFormatter dt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String uid = dt.format(LocalDateTime.now());

        trace = commandLine.getOptionValue("trace", "");
        traceName = commandLine.getOptionValue("traceName",""+uid);

        skipStages = commandLine.hasOption("skip-stages") ? Arrays.asList(commandLine.getOptionValues("skip-stages")).stream().map(str->StringUtil.getEnum(str,Stage.class,Stage.Invalid)).collect(Collectors.toList()) : Collections.EMPTY_LIST;

        if (commandLine.hasOption("basePath")) {
            outputPath = commandLine.getOptionValue("basePath") + "/" + uid;
        } else if (commandLine.hasOption("fullPath")) {
            outputPath = commandLine.getOptionValue("fullPath");
        } else {
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
        version = properties.getProperty("version", "unknown");
        hash = properties.getProperty("hash", "unknown");

        if (yamlPaths.isEmpty()) {
            logger.error("Missing required yaml file(s)");
            formatter.printHelp(cmdLineSyntax, options);
        }
        yamlParser = Parser.getInstance();
    }

    private static void disableLoggerShutdownHook(){
        final LoggerContextFactory factory = LogManager.getFactory();
        if (factory instanceof Log4jContextFactory) {
            Log4jContextFactory contextFactory = (Log4jContextFactory) factory;
            ((DefaultShutdownCallbackRegistry) contextFactory.getShutdownCallbackRegistry()).stop();
        }
    }

    public static void main(String[] args) {
        disableLoggerShutdownHook();
        ConfigurationFactory.setConfigurationFactory(new QdupConfigurationFactory());
        QDup toRun = new QDup(args);
        toRun.run();
        LogManager.shutdown();
    }

    public boolean run() {
        if(yamlPaths.isEmpty()){
            return false;
        }

        yamlParser.setAbortOnExitCode(checkExitCode());
        RunConfigBuilder runConfigBuilder = new RunConfigBuilder();

        boolean ok = true;
        for (String yamlPath : getYamlPaths()) {
            File yamlFile = new File(yamlPath);
            if (!yamlFile.exists()) {
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
                                logger.error("Aborting run due to error reading {}", yamlPath);
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
                if (yamlFile.isDirectory()) {
                    logger.trace("loading directory: " + yamlPath);
                    for (File child : yamlFile.listFiles()) {
                        if (child.getName().endsWith(".yaml") || child.getName().endsWith(".yml")) {
                            logger.trace("  loading: " + child.getPath());
                            //String content = FileUtility.readFile(child.getPath());
                            YamlFile file = yamlParser.loadFile(child.getPath());
                            if (file == null) {
                                logger.error("Aborting run due to error reading {}", child.getPath());
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
                        logger.error("Aborting run due to error reading {}", yamlPath);
                        ok = false;
                    }
                    runConfigBuilder.loadYaml(file);
                }
            }
        }
        if(!ok){
            return ok;
        }

        if (!getStateProps().isEmpty()) {
            getStateProps().forEach((k, v) -> {
                if (v != null && !v.toString().trim().isEmpty()) {
                    runConfigBuilder.forceRunState(k.toString(), v.toString());
                }
            });
        }


        if (!getRemoveStateProperties().isEmpty()) {
            getRemoveStateProperties().forEach((k, v) -> {
                if (k != null) {
                    runConfigBuilder.forceRunState(k.toString(), "");
                }
            });
        }
        if (hasTrace()) {
            runConfigBuilder.trace(getTrace());
        }

        if (getIdentity() != RunConfigBuilder.DEFAULT_IDENTITY) {
            runConfigBuilder.setIdentity(getIdentity());
        }

        if (getPassphrase() != RunConfigBuilder.DEFAULT_PASSPHRASE) {
            runConfigBuilder.setPassphrase(getPassphrase());
        }

        if (getKnownHosts() != RunConfigBuilder.DEFAULT_KNOWN_HOSTS) {
            runConfigBuilder.setKnownHosts(getKnownHosts());
        }

        if (hasSkipStages()){
            getSkipStages().forEach(runConfigBuilder::addSkipStage);
        }

        config = runConfigBuilder.buildConfig(yamlParser);
        if (isTest()) {
            //logger.info(config.debug());
            System.out.printf("%s", getRunDebug());
            if( this.config.hasErrors() ) {
                ok = false;
            } else {
                ok = true;
            }
            return ok;
        }else if (isYaml()){
            YamlFile file = runConfigBuilder.toYamlFile();
            System.out.printf("%s",yamlParser.dump(file));
            return ok;
        }else{
            File outputFile = new File(getOutputPath());
            if (!outputFile.exists()) {
                outputFile.mkdirs();
            }

            //TODO RunConfig should be immutable and terminal color is probably better stored in Run
            //TODO should we separte yaml config from environment config (identity, knownHosts, threads, color terminal)
            config.setColorTerminal(isColorTerminal());

            if (config.hasErrors()) {
                config.getErrors().stream().map(RunError::toString).forEach(error -> {
                    System.out.printf("%s%n", error);
                });
                return false;
            }else{
                if(hasTrace()){
                    config.getSettings().set(RunConfig.TRACE_NAME, traceName);
                }

                final AtomicInteger factoryCounter = new AtomicInteger(0);
                final AtomicInteger scheduledCounter = new AtomicInteger(0);

                BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();

                final Thread.UncaughtExceptionHandler uncaughtExceptionHandler = (thread, throwable) -> {
                    logger.error("UNCAUGHT:" + thread.getName() + " " + throwable.getMessage(), throwable);
                };

                ThreadFactory factory = r -> {
                    Thread rtrn = new Thread(r, "qdup-command-" + factoryCounter.getAndIncrement());
                    rtrn.setUncaughtExceptionHandler(uncaughtExceptionHandler);
                    return rtrn;
                };
                ThreadPoolExecutor executor = new ThreadPoolExecutor(getCommandThreads() / 2, getCommandThreads(), 30, TimeUnit.MINUTES, workQueue, factory);

                ScheduledThreadPoolExecutor scheduled = new ScheduledThreadPoolExecutor(getScheduledThreads(), runnable -> new Thread(runnable, "qdup-scheduled-" + scheduledCounter.getAndIncrement()));

                Dispatcher dispatcher = new Dispatcher(executor, scheduled);


                if (System.console()== null){
                    logger.info("running with detached console");
                }

                config.getSettings().set("check-exit-code", checkExitCode());

                final Run run = new Run(getOutputPath(), config, dispatcher);
                run.getRunLogger().info("Running qDup version {} @ {}", getVersion(), getHash());
                logger.info("output path = " + run.getOutputPath());
                if(checkExitCode()){
                    logger.info("checking sh exit codes");
                }

                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    if (!run.isAborted() && dispatcher.isRunning() && !dispatcher.isStopping()) {
                        run.abort(false);
                        Optional<Thread> mainThread = Thread.getAllStackTraces().keySet().stream().filter(t -> t.getName().equals("main")).findFirst();
                        if (mainThread.isPresent()) {
                            try {
                                mainThread.get().join();
                            } catch (InterruptedException e) {
                                logger.warn("Error waiting the termination of main thread", e);
                            }
                        }
                    }
                }, "shutdown-abort"));

                Boolean startJsonServer = !Boolean.parseBoolean(System.getProperty("disableRestApi", "false"));

                JsonServer jsonServer = null;
                if (startJsonServer) {
                    jsonServer = new JsonServer(run, getJsonPort());
                    getBreakpoints().forEach(jsonServer::addBreakpoint);
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
                if(run.isAborted()){
                    ok = false;
                }
            }
        }
        return ok;
    }
}
