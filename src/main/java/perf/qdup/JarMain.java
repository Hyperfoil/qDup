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
import perf.qdup.cmd.CommandDispatcher;
import perf.qdup.config.CmdBuilder;
import perf.qdup.config.RunConfig;
import perf.qdup.config.RunConfigBuilder;
import perf.qdup.config.YamlParser;
import perf.yaup.StringUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class JarMain {

    public static void main(String[] args) {

        Options options = new Options();

        OptionGroup basePathGroup = new OptionGroup();
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
            Option.builder("C")
                .longOpt("colorTerminal")
                .hasArg(false)
                .desc("flag to enable color formatted terminal")
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
                .desc("qdup known hosts path [~/.qdup/known_hosts]")
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
                .desc("qdup identity path [~/.qdup/id_rsa]")
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
        CommandLine cmd;

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
            cmd = parser.parse(options,args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp(cmdLineSyntax,options);
            System.exit(1);
            return;
        }

        int commandThreads = Integer.parseInt(cmd.getOptionValue("commandPool","24"));
        int scheduledThreads = Integer.parseInt(cmd.getOptionValue("scheduledPool","4"));

        List<String> yamlPaths = cmd.getArgList();

        //load a custom logback configuration
        if(cmd.hasOption("logback")){
            String configPath = cmd.getOptionValue("logback");
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
            System.out.println("Missing required yaml file(s)");
            formatter.printHelp(cmdLineSyntax,options);
            System.exit(1);
            return;
        }

        String outputPath=null;
        if(cmd.hasOption("basePath")){
            DateTimeFormatter dt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
            outputPath = cmd.getOptionValue("basePath") + "/" + dt.format(LocalDateTime.now());
        }else if (cmd.hasOption("fullPath")){
            outputPath = cmd.getOptionValue("fullPath");
        }

        File outputFile = new File(outputPath);
        if(!outputFile.exists()){
            outputFile.mkdirs();
        }
        File yamlJson = new File(new File(outputPath),"yaml.json");

        YamlParser yamlParser = new YamlParser();
        for(String yamlPath : yamlPaths){
            File yamlFile = new File(yamlPath);
            if(!yamlFile.exists()){
                System.out.println("Error: cannot find "+yamlPath);
                System.exit(1);//return error to shell / jenkins
            }else{
                if(yamlFile.isDirectory()){
                    System.out.println("loading directory: "+yamlPath);
                    for(File child : yamlFile.listFiles()){
                        System.out.println("  loading: "+child.getPath());
                        yamlParser.load(child.getPath());
                    }
                }else{
                    System.out.println("loading: "+yamlPath);
                    yamlParser.load(yamlPath);

                }
            }
        }

        try {
            yamlJson.createNewFile();
            Files.write(yamlJson.toPath(),yamlParser.getJson().toString(2).getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }

        CmdBuilder cmdBuilder = CmdBuilder.getBuilder();
        RunConfigBuilder runConfigBuilder = new RunConfigBuilder(cmdBuilder);

        if(yamlParser.hasErrors()){
            for(String error : yamlParser.getErrors()){
                System.out.println("Error: "+error);
            }
            System.exit(1);
            return;
        }

        runConfigBuilder.loadYaml(yamlParser);

        if (cmd.hasOption("knownHosts") ){
            runConfigBuilder.setKnownHosts(cmd.getOptionValue("knownHosts"));
        }
        if (cmd.hasOption("identity") ){
            runConfigBuilder.setIdentity(cmd.getOptionValue("identity"));
        }
        if (cmd.hasOption("passphrase") && !cmd.getOptionValue("passphrase").equals( RunConfigBuilder.DEFAULT_PASSPHRASE) ){
            runConfigBuilder.setPassphrase(cmd.getOptionValue("passphrase"));
        }

        Properties stateProps = cmd.getOptionProperties("S");
        if(!stateProps.isEmpty()){
            stateProps.forEach((k,v)->{
                runConfigBuilder.forceRunState(k.toString(),v.toString());
            });
        }

        RunConfig config = runConfigBuilder.buildConfig();

        //TODO RunConfig should be immutable and terminal color is probably better stored in Run
        if (cmd.hasOption("colorTerminal") ){
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

            System.out.println("UNCAUGHT:"+thread.getName()+" "+throwable.getMessage());
            throwable.printStackTrace(System.out);
        };

        ThreadFactory factory = r -> {
            Thread rtrn =new Thread(r,"command-"+factoryCounter.getAndIncrement());
            rtrn.setUncaughtExceptionHandler(uncaughtExceptionHandler);
            return rtrn;
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(commandThreads/2,commandThreads,30, TimeUnit.MINUTES,workQueue,factory);
        ScheduledThreadPoolExecutor scheduled = new ScheduledThreadPoolExecutor(scheduledThreads, runnable -> new Thread(runnable,"scheduled-"+scheduledCounter.getAndIncrement()));

        CommandDispatcher dispatcher = new CommandDispatcher(executor,scheduled);



        final Run run = new Run(outputPath,config,dispatcher);

        System.out.println("Starting with output path = "+run.getOutputPath());

        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            if(!run.isAborted()) {
                run.abort();
                run.writeRunJson();
            }
        },"shutdown-abort"));

        JsonServer jsonServer = new JsonServer(run.getDispatcher(),run.getCoordinator());

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
