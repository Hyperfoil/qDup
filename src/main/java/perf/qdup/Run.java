package perf.qdup;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.slf4j.profiler.Profiler;
import perf.qdup.cmd.*;
import perf.qdup.cmd.impl.ScriptCmd;
import perf.qdup.config.StageValidation;
import perf.qdup.config.RunConfig;
import perf.yaup.json.Json;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Created by wreicher
 *
 */
public class Run implements Runnable {

    final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

    class PendingDownload {
        private String path;
        private String destination;
        public PendingDownload(String path,String destination){
            this.path = path;
            this.destination = destination;
        }
        public String getPath(){return path;}
        public String getDestination(){return destination;}
    }


    private Map<String,Long> timestamps;
    private RunConfig config;
    private String outputPath;
    private boolean aborted;
    private Coordinator coordinator;
    private CommandDispatcher dispatcher;
    private Profiles profiles;
    private Local local;

    private Map<Host,Env.Diff> setupEnv;
    private Map<Host,List<PendingDownload>> pendingDownloads;

    private CountDownLatch runLatch = new CountDownLatch(1);
    private Logger runLogger;
    private ConsoleAppender<ILoggingEvent> consoleAppender;
    private FileAppender<ILoggingEvent> fileAppender;


    public Run(String outputPath,RunConfig config,CommandDispatcher dispatcher){
        this.config = config;

        this.outputPath = outputPath;
        this.dispatcher = dispatcher;
        this.aborted = false;


        this.timestamps = new LinkedHashMap<>();
        this.profiles = new Profiles();
        this.coordinator = new Coordinator();
        this.local = new Local(config);

        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        PatternLayoutEncoder consoleLayout = new PatternLayoutEncoder();
        consoleLayout.setPattern (config.isColorTerminal() ? "%red(%date) %highlight(%msg) %n" : "%date %msg%n");
        consoleLayout.setContext(lc);
        consoleLayout.start();
        consoleAppender = new ConsoleAppender<>();
        consoleAppender.setEncoder(consoleLayout);
        consoleAppender.setContext(lc);
        consoleAppender.start();


        PatternLayoutEncoder fileLayout = new PatternLayoutEncoder();
        fileLayout.setPattern("%date %msg%n");
        fileLayout.setContext(lc);
        fileLayout.start();
        fileLayout.setOutputPatternAsHeader(true);

        fileAppender = new FileAppender<>();
        fileAppender.setFile(this.outputPath+ File.separator+"run.log");
        fileAppender.setEncoder(fileLayout);
        fileAppender.setContext(lc);
        fileAppender.start();

        runLogger = (Logger) LoggerFactory.getLogger("activeRun");
        runLogger.addAppender(fileAppender);
        runLogger.addAppender(consoleAppender);
        runLogger.setLevel(Level.DEBUG);
        runLogger.setAdditive(false); /* set to true if root should log too */

        coordinator.addObserver((signal_name)->{
            runLogger.info("{} reached {}",config.getName(),signal_name);
        });


        this.pendingDownloads = new ConcurrentHashMap<>();
        this.setupEnv = new ConcurrentHashMap<>();
    }
    public Local getLocal(){return local;}

    public RunConfig getConfig(){return config;}
    public boolean isAborted(){return aborted;}
    public Logger getRunLogger(){return runLogger;}
    private List<PendingDownload> ensurePendingDownload(Host host){
        //TODO not thread safe
        if(!pendingDownloads.containsKey(host)){
            pendingDownloads.put(host,new LinkedList<>());
        }
        return pendingDownloads.get(host);
    }
    public void addPendingDownload(Host host,String path,String destination){
        List<PendingDownload> list = ensurePendingDownload(host);
        list.add(new PendingDownload(path,destination));
    }
    public void runPendingDownloads(){
        logger.info("{} runPendingDownloads",config.getName());
        if(!pendingDownloads.isEmpty()){
            timestamps.put("downloadStart",System.currentTimeMillis());
            for(Host host : pendingDownloads.keySet()){
                List<PendingDownload> downloadList = pendingDownloads.get(host);
                for(PendingDownload pendingDownload : downloadList){
                    local.download(pendingDownload.getPath(),pendingDownload.getDestination(),host);
                }
            }
            timestamps.put("downloadStop",System.currentTimeMillis());
            pendingDownloads.clear();
        }
    }
    public void done(){
        coordinator.clearWaiters();
        dispatcher.clearActive();
    }
    public void writeRunJson(){
        try (FileOutputStream out = new FileOutputStream(this.outputPath+File.separator+"run.json")) {

            Json toWrite = new Json();

            toWrite.set("state",this.getConfig().getState().toJson());

            Json latches = new Json();
            this.getCoordinator().getLatchTimes().forEach((key,value)->{
                latches.set(key,value);
            });
            Json counters = new Json();
            this.getCoordinator().getCounters().forEach((key,value)->{
                counters.set(key,value);
            });
            Json timestamps = new Json();
            this.timestamps.forEach((key,value)->{
                timestamps.set(key,value);
            });

            toWrite.set("timetamps",timestamps);
            toWrite.set("latches",latches);
            toWrite.set("counters",counters);
            toWrite.set("profiles",profiles.getJson());

            out.write(toWrite.toString(2).getBytes());
            out.flush();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void abort(){
        //TODO how to interrupt watchers
        this.aborted = true;
        dispatcher.stop();//interrupts working threads and stops dispatching next commands
        runPendingDownloads();//added here in addition to queueCleanupScripts to download when run aborts
        runLatch.countDown();
    }
    private void queueSetupScripts(){
        logger.debug("{}.setup",this);
        timestamps.put("setupStart",System.currentTimeMillis());
        CommandDispatcher.ScriptObserver scriptObserver = new CommandDispatcher.ScriptObserver() {

            private Env start;
            private Env stop;

            @Override
            public void onStart(Cmd command, Context context) {
                context.getSession().clearCommand();
                context.getSession().sh("env");
                start = new Env(context.getSession().getOutput());
            }

            @Override
            public void onStop(Cmd command, Context context) {

                context.getSession().clearCommand();
                context.getSession().sh("env");
                stop = new Env(context.getSession().getOutput());

                Env.Diff diff = start.diffTo(stop);
                setupEnv.put(context.getSession().getHost(),diff);
                dispatcher.removeScriptObserver(this);

            }
        };
        CommandDispatcher.CommandObserver setupObserver = new CommandDispatcher.CommandObserver() {
            @Override
            public void onStart(Cmd command) {}

            @Override
            public void onStop(Cmd command) {}

            @Override
            public void onStart() {}

            @Override
            public void onStop() {
                logger.debug("{}.setup stop",this);
                dispatcher.removeObserver(this);

                timestamps.put("setupStop",System.currentTimeMillis());
                queueRunScripts();
            }
        };
        dispatcher.addScriptObserver(scriptObserver);
        dispatcher.addObserver(setupObserver);
        for(Host host : config.getSetupHosts()){

                State hostState = config.getState().addChild(host.getHostName(),State.HOST_PREFIX);

                Cmd setupCmd = config.getSetupCmd(host);

                Profiler profiler = profiles.get(setupCmd.toString());

                logger.info("{} connecting {} to {}@{}",this,setupCmd,host.getUserName(),host.getHostName());
                profiler.start("connect:"+host.toString());
                SshSession scriptSession = new SshSession(host,config.getKnownHosts(),config.getIdentity(),config.getPassphrase());
                profiler.start("waiting for start");
                if(!scriptSession.isOpen()){
                    logger.error("{} failed to connect {} to {}@{}. Aborting",config.getName(),setupCmd,host.getUserName(),host.getHostName());
                    abort();
                    return;
                }
                long stop = System.currentTimeMillis();

                State scriptState = hostState.getChild(setupCmd.toString());
                Context context = new Context(scriptSession,scriptState,this,profiler);

                dispatcher.addScript(setupCmd, context);

        }
        dispatcher.start();
    }

    @Override
    public String toString(){return config.getName()+" -> "+outputPath;}

    public boolean initializeCoordinator(){
        List<String> noSignal = new ArrayList<>();
        Consumer<StageValidation> setupCoordinator = (stageValidation)->{
            stageValidation.getSignals().forEach((signalName)->{
                int count = stageValidation.getSignalCount(signalName);
                coordinator.initialize(signalName,count);
            });
            stageValidation.getWaiters().stream().filter((waitName)->
                    !stageValidation.getSignals().contains(waitName)
            ).forEach((notSignaled)->noSignal.add(notSignaled));
        } ;

        setupCoordinator.accept(config.getRunValidation().getSetupStage());
        if(!noSignal.isEmpty()){
            noSignal.forEach((notSignaled)->{
                runLogger.error("{} setup scripts missing signal for {}",this,notSignaled);
            });
            return false;
        }
        setupCoordinator.accept(config.getRunValidation().getRunStage());
        if(!noSignal.isEmpty()){
            noSignal.forEach((notSignaled)->{
                runLogger.error("{} run scripts missing signal for {}",this,notSignaled);
            });
            return false;
        }
        setupCoordinator.accept(config.getRunValidation().getCleanupStage());
        if(!noSignal.isEmpty()){
            noSignal.forEach((notSignaled)->{
                runLogger.error("{} cleanup scripts missing signal for {}",this,notSignaled);
            });
            return false;
        }
        return true;
    }

    @Override
    public void run() {
        timestamps.put("run",System.currentTimeMillis());


        if(!config.getRunValidation().isValid()){
            //TODO raise warnings if not validated
            if(config.getRunValidation().getSetupStage().hasErrors()){
                config.getRunValidation().getSetupStage().getErrors().forEach((error->runLogger.error(error)));
            }
            if(config.getRunValidation().getRunStage().hasErrors()){
                config.getRunValidation().getRunStage().getErrors().forEach((error->runLogger.error(error)));
            }
            if(config.getRunValidation().getCleanupStage().hasErrors()){
                config.getRunValidation().getCleanupStage().getErrors().forEach((error->runLogger.error(error)));
            }
            timestamps.put("end",System.currentTimeMillis());
            return;
        }

        boolean coordinatorInitialized = initializeCoordinator();
        if(!coordinatorInitialized){
            timestamps.put("end",System.currentTimeMillis());
            return;
        }

        runLogger.info("{} starting state:\n{}",config.getName(),config.getState().tree());
        queueSetupScripts();
        try {
            runLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //moved to here because abort would avoid the cleanup in postRun()
        fileAppender.stop();
        consoleAppender.stop();

        //dispatcher.closeSessions(); //was racing the close sessions from checkActiveCount
    }
    private void queueRunScripts(){
        timestamps.put("runStart",System.currentTimeMillis());
        logger.debug("{}.queueRunScripts",this);

        CommandDispatcher.CommandObserver runObserver = new CommandDispatcher.CommandObserver() {
            @Override
            public void onStart(Cmd command) {}

            @Override
            public void onStop(Cmd command) {}

            @Override
            public void onStart() {}

            @Override
            public void onStop() {
                dispatcher.removeObserver(this);
                timestamps.put("runStop",System.currentTimeMillis());
                queueCleanupScripts();
            }
        };
        List<Callable<Boolean>> connectSessions = new LinkedList<>();

        for(Host host : config.getRunHosts()){

            Env.Diff diff = setupEnv.containsKey(host) ? setupEnv.get(host) : new Env.Diff(Collections.emptyMap(),Collections.emptySet());

            final StringBuilder setEnv = new StringBuilder();
            final StringBuilder unsetEnv = new StringBuilder();
            try {
                diff.keys().forEach(key -> {
                    String keyValue = diff.get(key);
                    if (setEnv.length() > 0) {
                        setEnv.append(" ");
                    }
                    setEnv.append(" " + key + "=" + keyValue);
                });
            }catch(Exception e){
                e.printStackTrace();
                System.exit(0);
            }
            diff.unset().forEach(key->{
              unsetEnv.append(" "+key);
            });
            String updateEnv = (setEnv.length()>0? "export"+setEnv.toString():"")+(unsetEnv.length()>0?";unset"+unsetEnv.toString():"");
            logger.info("{} update env from setup {}",host,updateEnv);

            for(ScriptCmd scriptCmd : config.getRunCmds(host)){
                connectSessions.add(()->{
                    State hostState = config.getState().addChild(host.getHostName(),State.HOST_PREFIX);
                    State scriptState = hostState.getChild(scriptCmd.getName());
                    long start = System.currentTimeMillis();
                    Profiler profiler = profiles.get(scriptCmd.getName()+"@"+host);
                    logger.info("{} connecting {} to {}@{}",this,scriptCmd.getName(),host.getUserName(),host.getHostName());
                    profiler.start("connect:"+host.toString());
                    SshSession scriptSession = new SshSession(host,config.getKnownHosts(),config.getIdentity(),config.getPassphrase()); //this can take some time, hopefully it isn't a problem

                    if(!updateEnv.isEmpty()) {
                        scriptSession.sh(updateEnv);
                        String output = scriptSession.getOutput();//take output to ensure lock stays @ 1
                    }
                    profiler.start("waiting for start");
                    if(!scriptSession.isOpen()){
                        logger.error("{} failed to connect {} to {}@{}. Aborting",config.getName(),scriptCmd.getName(),host.getUserName(),host.getHostName());
                        abort();
                        return false;
                    }
                    long stop = System.currentTimeMillis();
                    Context context = new Context(scriptSession,scriptState,this,profiler);
                    logger.debug("{} connected {}@{} in {}s",this,scriptCmd.getName(),host,((stop-start)/1000));
                    dispatcher.addScript(scriptCmd, context);
                    return true;

                });
            }
        }
        try {
            boolean ok = dispatcher.getExecutor().invokeAll(connectSessions).stream().map((f) -> {
                boolean rtrn = false;
                try {
                    rtrn = f.get();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
                return rtrn;
            })
            .collect(Collectors.reducing(Boolean::logicalAnd)).orElse(false);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        dispatcher.addObserver(runObserver);
        dispatcher.start();
    }
    private void queueCleanupScripts(){
        timestamps.put("cleanupStart",System.currentTimeMillis());
        logger.info("{}.queueCleanupScripts",this);
        CommandDispatcher.CommandObserver cleanupObserver = new CommandDispatcher.CommandObserver() {
            @Override
            public void onStart(Cmd command) {}

            @Override
            public void onStop(Cmd command) {}

            @Override
            public void onStart() {}

            @Override
            public void onStop() {
                timestamps.put("cleanupStop",System.currentTimeMillis());
                dispatcher.removeObserver(this);
                postRun();
            }
        };

        //run before cleanup
        runPendingDownloads();

        List<Callable<Boolean>> connectSessions = new LinkedList<>();

        for(Host host : config.getCleanupHosts()){
            Cmd cleanupCmd = config.getCleanupCmd(host);
            connectSessions.add(()-> {
                State hostState = config.getState().addChild(host.getHostName(), State.HOST_PREFIX);
                State scriptState = hostState.getChild(cleanupCmd.toString());
                long start = System.currentTimeMillis();
                Profiler profiler = profiles.get(cleanupCmd.toString() + "@" + host);
                logger.info("{} connecting {} to {}@{}", this, cleanupCmd.toString(), host.getUserName(), host.getHostName());
                profiler.start("connect:"+host.toString());
                SshSession scriptSession = new SshSession(host,config.getKnownHosts(),config.getIdentity(),config.getPassphrase()); //this can take some time, hopefully it isn't a problem
                profiler.start("waiting for start");
                if(!scriptSession.isOpen()){
                    logger.error("{} failed to connect {} to {}@{}. Aborting",config.getName(),cleanupCmd.toString(),host.getUserName(),host.getHostName());
                    abort();
                    return false;
                }

                long stop = System.currentTimeMillis();

                Context context = new Context(scriptSession,scriptState,this,profiler);
                logger.debug("{} connected {}@{} in {}s",this,cleanupCmd.toString(),host,((stop-start)/1000));
                dispatcher.addScript(cleanupCmd, context);
                return true;
            });
        }
        if(!connectSessions.isEmpty()) {
            try {
                boolean ok = dispatcher.getExecutor().invokeAll(connectSessions).stream().map((f) -> {
                    boolean rtrn = false;
                    try {
                        rtrn = f.get();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    }
                    return rtrn;
                })
                        .collect(Collectors.reducing(Boolean::logicalAnd)).get();

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        dispatcher.addObserver(cleanupObserver);
        dispatcher.start();
    }
    private void postRun(){
        logger.info("{}.postRun",this);
        runLogger.info("{} closing state:\n{}",config.getName(),config.getState().tree());

        runLatch.countDown();

    }

    public CommandDispatcher getDispatcher(){return dispatcher;}
    public Coordinator getCoordinator(){return coordinator;}
    public String getOutputPath(){ return outputPath;}

}