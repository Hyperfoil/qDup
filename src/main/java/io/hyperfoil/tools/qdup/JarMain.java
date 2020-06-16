package io.hyperfoil.tools.qdup;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.qdup.cmd.ContextObserver;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.cmd.ScriptContext;
import io.hyperfoil.tools.qdup.config.CmdBuilder;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import org.apache.commons.cli.*;
import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.waml.WamlParser;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import io.hyperfoil.tools.qdup.config.yaml.YamlFile;
import io.hyperfoil.tools.qdup.config.yaml.YamlFileConstruct;
import io.hyperfoil.tools.yaup.AsciiArt;
import io.hyperfoil.tools.yaup.StringUtil;
import io.hyperfoil.tools.yaup.file.FileUtility;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class JarMain {

   private static final XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

   public static void main(String[] args) {
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
         Option.builder(null)
            .longOpt("enableWaml")
            .hasArg(false)
            .desc("do not accept waml configuration")
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

      try {
         commandLine = parser.parse(options, args);
      } catch (ParseException e) {
         logger.error(e.getMessage(), e);
         formatter.printHelp(cmdLineSyntax, options);
         System.exit(1);
         return;
      }

      int commandThreads = Integer.parseInt(commandLine.getOptionValue("commandPool", "24"));
      int scheduledThreads = Integer.parseInt(commandLine.getOptionValue("scheduledPool", "4"));

      List<String> yamlPaths = commandLine.getArgList();

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

      if (yamlPaths.isEmpty()) {
         logger.error("Missing required yaml file(s)");
         formatter.printHelp(cmdLineSyntax, options);
         System.exit(1);
         return;
      }

      if (commandLine.hasOption("waml")) { //convert waml to yaml
         Queue<String> todo = new LinkedBlockingQueue<>();
         todo.addAll(yamlPaths);
         Parser p = Parser.getInstance();
         while (!todo.isEmpty()) {
            String path = todo.poll();
            File yamlFile = new File(path);
            if (!yamlFile.exists()) {

            } else {
               if (yamlFile.isDirectory()) {
                  for (File child : yamlFile.listFiles()) {
                     String childPath = child.getAbsolutePath();
                     if (!childPath.endsWith("qd.yaml") && (childPath.endsWith(".yaml") || childPath.endsWith(".waml"))) {
                        todo.add(child.getAbsolutePath());
                     }
                  }
               } else {
                  String destPath = path;
                  if ((destPath.endsWith(".yaml") || destPath.endsWith(".waml")) && !destPath.endsWith("qd.yaml")) {
                     destPath = destPath.substring(0, destPath.length() - ".yaml".length()) + ".qd.yaml";
                  }
                  CmdBuilder cmdBuilder = CmdBuilder.getBuilder();
                  RunConfigBuilder runConfigBuilder = new RunConfigBuilder(cmdBuilder);
                  WamlParser wamlParser = new WamlParser();
                  logger.info("converting {} to qd.yaml format", path);
                  wamlParser.load(path);
                  if (wamlParser.hasErrors()) {
                     logger.error("Errors parsing {}\n{}", path, wamlParser.getErrors().stream().collect(Collectors.joining("\n")));
                  } else {
                     runConfigBuilder.loadWaml(wamlParser);
                     String toWrite = p.dump(YamlFileConstruct.MAPPING.getMap(runConfigBuilder.toYamlFile()));
                     try {
                        Files.write(Path.of(destPath), toWrite.getBytes());
                     } catch (IOException e) {
                        logger.error("Failed to write to {}\n{}", destPath, e.getMessage());
                     }
                  }
               }
            }
         }
         System.exit(0);
      }

      String outputPath = null;
      DateTimeFormatter dt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
      String uid = dt.format(LocalDateTime.now());
      if (commandLine.hasOption("basePath")) {
         outputPath = commandLine.getOptionValue("basePath") + "/" + uid;
      } else if (commandLine.hasOption("fullPath")) {
         outputPath = commandLine.getOptionValue("fullPath");
      } else {
         outputPath = "/tmp";
      }

      CmdBuilder cmdBuilder = CmdBuilder.getBuilder();
      RunConfigBuilder runConfigBuilder = new RunConfigBuilder(cmdBuilder);

      //WamlParser wamlParser = new WamlParser();
      Parser yamlParser = Parser.getInstance();
      for (String yamlPath : yamlPaths) {
         File yamlFile = new File(yamlPath);
         if (!yamlFile.exists()) {
            logger.error("Error: cannot find " + yamlPath);
            System.exit(1);//return error to shell / jenkins
         } else {
            if (yamlFile.isDirectory()) {
               logger.trace("loading directory: " + yamlPath);
               for (File child : yamlFile.listFiles()) {
                  if (child.getName().endsWith("yaml") || child.getName().endsWith("yml")) {
                     logger.trace("  loading: " + child.getPath());
                     //String content = FileUtility.readFile(child.getPath());
                     YamlFile file = yamlParser.loadFile(child.getPath(), commandLine.hasOption("enableWaml"));
                     if (file == null) {
                        logger.error("Aborting run due to error reading {}", yamlPath);
                        System.exit(1);
                     }
                     runConfigBuilder.loadYaml(file);
                  } else {
                     logger.trace("  skipping: " + child.getPath());
                  }
               }
            } else {
               logger.trace("loading: " + yamlPath);
               YamlFile file = yamlParser.loadFile(yamlPath, commandLine.hasOption("enableWaml"));
               if (file == null) {
                  logger.error("Aborting run due to error reading {}", yamlPath);
                  System.exit(1);
               }
               runConfigBuilder.loadYaml(file);

            }
         }
      }
      if (commandLine.hasOption("knownHosts")) {
         runConfigBuilder.setKnownHosts(commandLine.getOptionValue("knownHosts"));
      }
      if (commandLine.hasOption("identity")) {
         System.out.printf("setting custom identity %s%n", commandLine.getOptionValue("identity"));
         runConfigBuilder.setIdentity(commandLine.getOptionValue("identity"));
      }
      if (commandLine.hasOption("passphrase") && !commandLine.getOptionValue("passphrase").equals(RunConfigBuilder.DEFAULT_PASSPHRASE)) {
         System.out.printf("setting passpharse for identity file%n");
         runConfigBuilder.setPassphrase(commandLine.getOptionValue("passphrase"));
      }

      if (commandLine.hasOption("timeout")) {
         runConfigBuilder.setTimeout(Integer.parseInt(commandLine.getOptionValue("timeout")));
      }

      Properties stateProps = commandLine.getOptionProperties("S");
      if (!stateProps.isEmpty()) {
         stateProps.forEach((k, v) -> {
            runConfigBuilder.forceRunState(k.toString(), v.toString());
         });
      }

      if (commandLine.hasOption("trace")) {
         runConfigBuilder.trace(commandLine.getOptionValue("trace"));
      }

      RunConfig config = runConfigBuilder.buildConfig();

      if (commandLine.hasOption("test")) {
         //logger.info(config.debug());
         System.out.printf("%s", config.debug(true));
         System.exit(0);
      }

      File outputFile = new File(outputPath);
      if (!outputFile.exists()) {
         outputFile.mkdirs();
      }

      //TODO RunConfig should be immutable and terminal color is probably better stored in Run
      //TODO should we separte yaml config from environment config (identity, knownHosts, threads, color terminal)
      if (commandLine.hasOption("colorTerminal")) {
         config.setColorTerminal(true);
      }

      if (config.hasErrors()) {
         for (String error : config.getErrors()) {
            System.out.printf("Error: %s%n", error);
         }
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
         Thread rtrn = new Thread(r, "command-" + factoryCounter.getAndIncrement());
         rtrn.setUncaughtExceptionHandler(uncaughtExceptionHandler);
         return rtrn;
      };
      ThreadPoolExecutor executor = new ThreadPoolExecutor(commandThreads / 2, commandThreads, 30, TimeUnit.MINUTES, workQueue, factory);

      ScheduledThreadPoolExecutor scheduled = new ScheduledThreadPoolExecutor(scheduledThreads, runnable -> new Thread(runnable, "scheduled-" + scheduledCounter.getAndIncrement()));

      Dispatcher dispatcher = new Dispatcher(executor, scheduled);

      if (commandLine.hasOption("breakpoint")) {
         Scanner scanner = new Scanner(System.in);
         Arrays.asList(commandLine.getOptionValues("breakpoint")).forEach(breakpoint -> {
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


      final Run run = new Run(outputPath, config, dispatcher);

      logger.info("Starting with output path = " + run.getOutputPath());

      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
         if (!run.isAborted()) {
            run.abort(false);
            run.writeRunJson();
         }
      }, "shutdown-abort"));


      int port = Integer.parseInt(commandLine.getOptionValue("jsonport", "" + JsonServer.DEFAULT_PORT));
      JsonServer jsonServer = new JsonServer(run, port);

      jsonServer.start();

      long start = System.currentTimeMillis();

      run.getRunLogger().info("Running qDup version {} @ {}", properties.getProperty("version", "unkonown"), properties.getProperty("hash", "unknown"));
      run.run();
      //run.writeRunJson(); moved into run.run()

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
