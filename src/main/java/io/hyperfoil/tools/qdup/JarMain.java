package io.hyperfoil.tools.qdup;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.qdup.cmd.ContextObserver;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.RunError;
import org.apache.commons.cli.*;
import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import io.hyperfoil.tools.qdup.config.yaml.YamlFile;
import io.hyperfoil.tools.yaup.AsciiArt;
import io.hyperfoil.tools.yaup.StringUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class JarMain {

    private static final XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());
    private final String knownHosts;
    private final String identity;
    private final String passphrase;
    private final int timeout;
    private final Properties stateProps;
    private final Properties removeStateProperties;
    private final String trace;

    private String outputPath;
    private String version;
    private String hash;

    private List<String> yamlPaths;
    private int scheduledThreads;
    private int commandThreads;
    private boolean test;
    private List<String> breakpoints;
    private boolean colorTerminal;
    private int jsonPort;


    private boolean exitCode = false;

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

    public boolean isTest() {
        return test;
    }

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

    public JarMain(String... args) {
        Options options = new Options();

        OptionGroup basePathGroup = new OptionGroup();
        basePathGroup.addOption(Option.builder("W")
                .longOpt("waml")
                .required()
                .desc("convert waml to qd.yaml files")
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

        basePathGroup.setRequired(true);

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
                        .hasArgs()
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

        //logging
        options.addOption(
                Option.builder("l")
                        .longOpt("logback")
                        .argName("path")
                        .hasArg()
                        .desc("logback configuration path")
                        .type(String.class)
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

        //exit code checking
        options.addOption(
                Option.builder("x")
                        .longOpt("exitCode")
                        .hasArg(false)
                        .desc("flag to enable exit code checking")
                        .build()
        );

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine commandLine = null;

        String cmdLineSyntax = "[options] [yamlFiles]";

        cmdLineSyntax =
                "java -jar " +
                        (new File(JarMain.class
                                .getProtectionDomain()
                                .getCodeSource()
                                .getLocation()
                                .getPath()
                        )).getName() +
                        " " +
                        cmdLineSyntax;

        try {
            commandLine = parser.parse(options, args);
        } catch (ParseException e) {
            logger.error(e.getMessage(), e);
            formatter.printHelp(cmdLineSyntax, options);
            System.exit(1);
        }

        //load a custom logback configuration
        if (commandLine.hasOption("logback")) {
            String configPath = commandLine.getOptionValue("logback");
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

            try {
                JoranConfigurator configurator = new JoranConfigurator();
                configurator.setContext(context);
                context.reset();
                configurator.doConfigure(configPath);
            } catch (JoranException je) {
                // StatusPrinter will handle this
            }
            StatusPrinter.printInCaseOfErrorsOrWarnings(context);
        }

        knownHosts = commandLine.getOptionValue("knownHosts", RunConfigBuilder.DEFAULT_KNOWN_HOSTS);
        identity = commandLine.getOptionValue("identity", RunConfigBuilder.DEFAULT_IDENTITY);
        passphrase = commandLine.getOptionValue("passphrase", RunConfigBuilder.DEFAULT_PASSPHRASE);
        timeout = Integer.parseInt(commandLine.getOptionValue("timeout", "" + RunConfigBuilder.DEFAULT_SSH_TIMEOUT));
        commandThreads = Integer.parseInt(commandLine.getOptionValue("commandPool", "24"));
        scheduledThreads = Integer.parseInt(commandLine.getOptionValue("scheduledPool", "24"));
        yamlPaths = commandLine.getArgList();
        stateProps = commandLine.getOptionProperties("S");
        removeStateProperties = commandLine.getOptionProperties("SX");
        trace = commandLine.getOptionValue("trace", "");
        test = commandLine.hasOption("test");
        breakpoints = commandLine.hasOption("breakpoint") ? Arrays.asList(commandLine.getOptionValues("breakpoint")) : Collections.EMPTY_LIST;
        colorTerminal = commandLine.hasOption("colorTerminal");
        jsonPort = Integer.parseInt(commandLine.getOptionValue("jsonport", "" + JsonServer.DEFAULT_PORT));
        exitCode = commandLine.hasOption("exitCode");

        outputPath = null;
        DateTimeFormatter dt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String uid = dt.format(LocalDateTime.now());
        if (commandLine.hasOption("basePath")) {
            outputPath = commandLine.getOptionValue("basePath") + "/" + uid;
        } else if (commandLine.hasOption("fullPath")) {
            outputPath = commandLine.getOptionValue("fullPath");
        } else {
            outputPath = "/tmp";
        }

        Properties properties = new Properties();
        try (InputStream is = JarMain.class.getResourceAsStream("/META-INF/MANIFEST.MF")) {
            if (is != null) {
                properties.load(is);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try (InputStream is = JarMain.class.getResourceAsStream("/META-INF/maven/io.hyperfoil.tools/qDup/pom.properties")) {
            if (is != null) {
                properties.load(is);

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        version = properties.getProperty("version", "unkonown");
        hash = properties.getProperty("hash", "unkonown");

        if (yamlPaths.isEmpty()) {
            logger.error("Missing required yaml file(s)");
            formatter.printHelp(cmdLineSyntax, options);
            System.exit(1);
            return;
        }
    }

    public static void main(String[] args) {
        JarMain jarMain = new JarMain(args);

        Parser yamlParser = Parser.getInstance();
        yamlParser.setAbortOnExitCode(jarMain.checkExitCode());
        RunConfigBuilder runConfigBuilder = new RunConfigBuilder();

        for (String yamlPath : jarMain.getYamlPaths()) {
            File yamlFile = new File(yamlPath);
            if (!yamlFile.exists()) {
                logger.error("Error: cannot find " + yamlPath);
                System.exit(1);//return error to shell / jenkins
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
                                System.exit(1);
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
                        System.exit(1);
                    }
                    runConfigBuilder.loadYaml(file);

                }
            }
        }


        if (!jarMain.getStateProps().isEmpty()) {
            jarMain.getStateProps().forEach((k, v) -> {
                if (v != null && !v.toString().trim().isEmpty()) {
                    runConfigBuilder.forceRunState(k.toString(), v.toString());
                }
            });
        }


        if (!jarMain.getRemoveStateProperties().isEmpty()) {
            jarMain.getRemoveStateProperties().forEach((k, v) -> {
                if (k != null) {
                    runConfigBuilder.forceRunState(k.toString(), "");
                }
            });
        }
        if (jarMain.hasTrace()) {
            runConfigBuilder.trace(jarMain.getTrace());
        }

        RunConfig config = runConfigBuilder.buildConfig(yamlParser);
        if (jarMain.isTest()) {
            //logger.info(config.debug());
            System.out.printf("%s", config.debug(true));
            System.exit(0);
        }

        File outputFile = new File(jarMain.getOutputPath());
        if (!outputFile.exists()) {
            outputFile.mkdirs();
        }

        //TODO RunConfig should be immutable and terminal color is probably better stored in Run
        //TODO should we separte yaml config from environment config (identity, knownHosts, threads, color terminal)
        config.setColorTerminal(jarMain.isColorTerminal());

        if (config.hasErrors()) {
            config.getErrors().stream().map(RunError::toString).forEach(error -> {
                System.out.printf("%s%n", error);
            });
            System.exit(1);
            return;
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
        ThreadPoolExecutor executor = new ThreadPoolExecutor(jarMain.getCommandThreads() / 2, jarMain.getCommandThreads(), 30, TimeUnit.MINUTES, workQueue, factory);

        ScheduledThreadPoolExecutor scheduled = new ScheduledThreadPoolExecutor(jarMain.getScheduledThreads(), runnable -> new Thread(runnable, "qdup-scheduled-" + scheduledCounter.getAndIncrement()));

        Dispatcher dispatcher = new Dispatcher(executor, scheduled);

        if (!jarMain.getBreakpoints().isEmpty()) {
            Scanner scanner = new Scanner(System.in);
            jarMain.getBreakpoints().forEach(breakpoint -> {
                dispatcher.addContextObserver(new ContextObserver() {
                    @Override
                    public void preStart(Context context, Cmd command) {
                        String commandString = command.toString();
                        boolean matches = commandString.contains(breakpoint) || commandString.matches(breakpoint);
                        if (matches) {
                            System.out.printf(
                                    "%sBREAKPOINT%s%n" +
                                            "  breakpoint: %s%n" +
                                            "  command: %s%n" +
                                            "  script: %s%n" +
                                            "  host: %s%n" +
                                            "Press enter to continue:",
                                    config.isColorTerminal() ? AsciiArt.ANSI_RED : "",
                                    config.isColorTerminal() ? AsciiArt.ANSI_RESET : "",
                                    breakpoint,
                                    command.toString(),
                                    command.getHead().toString(),
                                    context.getHost().toString()
                            );
                            String line = scanner.nextLine();
                        }
                    }
                });

            });
        }

        config.getSettings().set("check-exit-code",jarMain.checkExitCode());

        final Run run = new Run(jarMain.getOutputPath(), config, dispatcher);

        logger.info("Starting with output path = " + run.getOutputPath());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!run.isAborted()) {
                run.abort(false);
                run.writeRunJson();
            }
        }, "shutdown-abort"));

        JsonServer jsonServer = new JsonServer(run, jarMain.getJsonPort());

        jsonServer.start();

        long start = System.currentTimeMillis();
        run.getRunLogger().info("Running qDup version {} @ {}", jarMain.getVersion(), jarMain.getHash());
        run.run();
        long stop = System.currentTimeMillis();
        System.out.printf("Finished in %s at %s%n", StringUtil.durationToString(stop - start), run.getOutputPath());
        jsonServer.stop();
        dispatcher.shutdown();
        executor.shutdownNow();
        scheduled.shutdownNow();
        if (run.isAborted()) {
            System.exit(1);
        }
    }
}
