package perf.qdup;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.ContextObserver;
import perf.qdup.cmd.Dispatcher;
import perf.qdup.cmd.ScriptContext;
import perf.qdup.config.CmdBuilder;
import perf.qdup.config.RunConfig;
import perf.qdup.config.RunConfigBuilder;
import perf.qdup.config.YamlParser;
import perf.yaup.AsciiArt;
import perf.yaup.StringUtil;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public class JarMain {

    private static final XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

    public static void main(String[] args) {

        Options options = new Options();

        OptionGroup basePathGroup = new OptionGroup();
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
                .valueSeparator()
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


        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine commandLine;

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

        try{
            commandLine = parser.parse(options,args);
        } catch (ParseException e) {
            logger.error(e.getMessage(),e);
            formatter.printHelp(cmdLineSyntax,options);
            System.exit(1);
            return;
        }

        int commandThreads = Integer.parseInt(commandLine.getOptionValue("commandPool","24"));
        int scheduledThreads = Integer.parseInt(commandLine.getOptionValue("scheduledPool","4"));

        List<String> yamlPaths = commandLine.getArgList();

        //load a custom logback configuration
        if(commandLine.hasOption("logback")){
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

        if(yamlPaths.isEmpty()){
            logger.error("Missing required yaml file(s)");
            formatter.printHelp(cmdLineSyntax,options);
            System.exit(1);
            return;
        }

        String outputPath=null;
        if(commandLine.hasOption("basePath")){
            DateTimeFormatter dt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
            outputPath = commandLine.getOptionValue("basePath") + "/" + dt.format(LocalDateTime.now());
        }else if (commandLine.hasOption("fullPath")){
            outputPath = commandLine.getOptionValue("fullPath");
        }else{
            outputPath = "/tmp";
        }

        YamlParser yamlParser = new YamlParser();
        for(String yamlPath : yamlPaths){
            File yamlFile = new File(yamlPath);
            if(!yamlFile.exists()){
                logger.error("Error: cannot find "+yamlPath);
                System.exit(1);//return error to shell / jenkins
            }else{
                if(yamlFile.isDirectory()){
                    logger.trace("loading directory: "+yamlPath);
                    for(File child : yamlFile.listFiles()){
                        logger.trace("  loading: "+child.getPath());
                        yamlParser.load(child.getPath());
                    }
                }else{
                    logger.trace("loading: "+yamlPath);
                    yamlParser.load(yamlPath);

                }
            }
        }

        CmdBuilder cmdBuilder = CmdBuilder.getBuilder();
        RunConfigBuilder runConfigBuilder = new RunConfigBuilder(cmdBuilder);

        if(yamlParser.hasErrors()){
            for(String error : yamlParser.getErrors()){
                logger.error("Error: "+error);
            }
            System.exit(1);
            return;
        }

        runConfigBuilder.loadYaml(yamlParser);

        if (commandLine.hasOption("knownHosts") ){
            runConfigBuilder.setKnownHosts(commandLine.getOptionValue("knownHosts"));
        }
        if (commandLine.hasOption("identity") ){
            runConfigBuilder.setIdentity(commandLine.getOptionValue("identity"));
        }
        if (commandLine.hasOption("passphrase") && !commandLine.getOptionValue("passphrase").equals( RunConfigBuilder.DEFAULT_PASSPHRASE) ){
            runConfigBuilder.setPassphrase(commandLine.getOptionValue("passphrase"));
        }

        if(commandLine.hasOption("timeout")){
            runConfigBuilder.setTimeout(Integer.parseInt(commandLine.getOptionValue("timeout")));
        }

        Properties stateProps = commandLine.getOptionProperties("S");
        if(!stateProps.isEmpty()){
            stateProps.forEach((k,v)->{
                runConfigBuilder.forceRunState(k.toString(),v.toString());
            });
        }

        RunConfig config = runConfigBuilder.buildConfig();

        if(commandLine.hasOption("test")){
            //logger.info(config.debug());
            System.out.println(config.debug());
            System.exit(0);
        }

        File outputFile = new File(outputPath);
        if(!outputFile.exists()){
            outputFile.mkdirs();
        }
        File yamlJson = new File(new File(outputPath),"yaml.json");
        try {
            yamlJson.createNewFile();
            Files.write(yamlJson.toPath(),yamlParser.getJson().toString(2).getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }


        //TODO RunConfig should be immutable and terminal color is probably better stored in Run
        if (commandLine.hasOption("colorTerminal") ){
            config.setColorTerminal( true );
        }

        if(config.hasErrors()){
            for(String error: config.getErrors()){
                System.out.println("Error: "+error);
            }
            System.exit(1);
            return;
        }

        final AtomicInteger factoryCounter = new AtomicInteger(0);
        final AtomicInteger scheduledCounter = new AtomicInteger(0);

        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();

        final Thread.UncaughtExceptionHandler uncaughtExceptionHandler = (thread, throwable) ->{

            logger.error("UNCAUGHT:"+thread.getName()+" "+throwable.getMessage(),throwable);
        };

        ThreadFactory factory = r -> {
            Thread rtrn =new Thread(r,"command-"+factoryCounter.getAndIncrement());
            rtrn.setUncaughtExceptionHandler(uncaughtExceptionHandler);
            return rtrn;
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(commandThreads/2,commandThreads,30, TimeUnit.MINUTES,workQueue,factory);
        ScheduledThreadPoolExecutor scheduled = new ScheduledThreadPoolExecutor(scheduledThreads, runnable -> new Thread(runnable,"scheduled-"+scheduledCounter.getAndIncrement()));

        Dispatcher dispatcher = new Dispatcher(executor,scheduled);

        if(commandLine.hasOption("breakpoint")){
            Scanner scanner = new Scanner(System.in);
            Arrays.asList(commandLine.getOptionValues("breakpoint")).forEach(breakpoint->{
                dispatcher.addContextObserver(new ContextObserver() {
                    @Override
                    public void preStart(ScriptContext context, Cmd command) {
                        String commandString = command.toString();
                        boolean matches = commandString.contains(breakpoint) || commandString.matches(breakpoint);
                        if(matches){
                            System.out.printf(
                                    "%sBREAKPOINT%s%n"+
                                    "  breakpoint: %s%n"+
                                    "  command: %s%n"+
                                    "  script: %s%n" +
                                    "  host: %s%n"+
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



        final Run run = new Run(outputPath,config,dispatcher);

        logger.info("Starting with output path = "+run.getOutputPath());

        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            if(!run.isAborted()) {
                run.abort(false);
                run.writeRunJson();
            }
        },"shutdown-abort"));

        JsonServer jsonServer = new JsonServer(run);

        jsonServer.start();

        long start = System.currentTimeMillis();

        run.run();
        run.writeRunJson();

        long stop = System.currentTimeMillis();

        System.out.println("Finished in "+ StringUtil.durationToString(stop-start)+" at "+run.getOutputPath());

        jsonServer.stop();

        dispatcher.shutdown();
        executor.shutdownNow();
        scheduled.shutdownNow();

        if(run.isAborted()){
            System.exit(-1);
        }
    }
}
