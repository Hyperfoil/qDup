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
import perf.qdup.config.*;
import perf.yaup.HashedLists;
import perf.yaup.StringUtil;
import perf.yaup.json.Json;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Created by wreicher
 *
 */
public class Run implements Runnable, CommandDispatcher.DispatchObserver {

    final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());
    private final static AtomicReferenceFieldUpdater<Run,Stage> stageUpdated = AtomicReferenceFieldUpdater.newUpdater(Run.class,Run.Stage.class,"stage");

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

    enum Stage {Setup("setup"),Run("run"),Cleanup("cleanup");
        String name;
        Stage(String name){
            this.name = name;
        }
        public String getName(){return name;}
    }


    private volatile Stage stage;
    private Map<String,Long> timestamps;
    private RunConfig config;
    private String outputPath;
    private AtomicBoolean aborted;
    private Coordinator coordinator;
    private CommandDispatcher dispatcher;
    private Profiles profiles;
    private Local local;

    private CommandDispatcher.ScriptObserver envObserver;
    private final Map<Host,Env.Diff> setupEnvDiff;
    private final Map<Host,Env> startEnvs;
    private HashedLists<Host,PendingDownload> pendingDownloads;

    private CountDownLatch runLatch = new CountDownLatch(1);
    private Logger runLogger;
    private ConsoleAppender<ILoggingEvent> consoleAppender;
    private FileAppender<ILoggingEvent> fileAppender;

    public Run(String outputPath,RunConfig config,CommandDispatcher dispatcher){
        if(config==null || dispatcher==null){
            throw new NullPointerException("Run config and dispatcher cannot be null");
        }

        this.config = config;

        this.outputPath = outputPath;
        this.dispatcher = dispatcher;
        this.dispatcher.addDispatchObserver(this);
        this.stage = Stage.Setup;
        this.aborted = new AtomicBoolean(false);


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

        this.pendingDownloads = new HashedLists<>();
        this.setupEnvDiff = new ConcurrentHashMap<>();
        this.startEnvs = new ConcurrentHashMap<>();
        this.envObserver = new CommandDispatcher.ScriptObserver() {

            @Override
            public void onStart(Cmd command, Context context) {
                context.getSession().clearCommand();
                context.getSession().sh("env");
                String env = context.getSession().getOutput();
                Env start = new Env(env);
                startEnvs.put(context.getSession().getHost(),start);

                logger.debug("{} env = \"{}\" \n Env.start = {}",context.getSession().getHost(),env,start.debug());
            }

            @Override
            public void onStop(Cmd command, Context context) {

                context.getSession().clearCommand();
                context.getSession().sh("env");
                String env = context.getSession().getOutput();
                Env stop = new Env(env);

                logger.debug("{} env = \"{}\" \n Env.stop = {}",context.getSession().getHost(),env,stop.debug());

                if(startEnvs.containsKey(context.getSession().getHost())){
                    Env start = startEnvs.get(context.getSession().getHost());
                    Env.Diff diff = start.diffTo(stop);
                    logger.info("{} {}",context.getSession().getHost(),diff.debug());

                    setupEnvDiff.put(context.getSession().getHost(),diff);

                }else{
                    logger.info("{} does not have Env.start",context.getSession().getHost());
                }
            }
        };
    }

    @Override
    public void preStart(){
        timestamps.put(stage.getName()+"Start",System.currentTimeMillis());
    }
    @Override
    public void postStop(){
        timestamps.put(stage.getName()+"Stop",System.currentTimeMillis());
        nextStage();
        this.setupEnvDiff.clear();

    }
    private void nextStage(){
        switch (stage){
            case Setup:
                //if we are able to set the stage to Run
                if(stageUpdated.compareAndSet(this,Stage.Setup,Stage.Run)){
                    dispatcher.removeScriptObserver(envObserver);
                    queueRunScripts();
                    dispatcher.start();
                }
                break;
            case Run:
                runPendingDownloads();
                //if we are able to set the stage to Cleanup
                if(stageUpdated.compareAndSet(this,Stage.Run,Stage.Cleanup)){
                    queueCleanupScripts();
                    dispatcher.start();
                }
                break;
            case Cleanup:
                postRun();//release any latches blocking a call to run()
                break;
            default:
        }
    }


    public Local getLocal(){return local;}

    public RunConfig getConfig(){return config;}
    public boolean isAborted(){return aborted.get();}
    public Logger getRunLogger(){return runLogger;}
    public void addPendingDownload(Host host,String path,String destination){
        pendingDownloads.put(host,new PendingDownload(path,destination));
    }
    public void runPendingDownloads(){

        //TODO synchronize so only one threads tries the downloads (run ending while being aborted?)
        logger.info("{} runPendingDownloads",config.getName());
        if(!pendingDownloads.isEmpty()){
            timestamps.put("downloadStart",System.currentTimeMillis());
            for(Host host : pendingDownloads.keys()){
                List<PendingDownload> downloadList = pendingDownloads.get(host);
                for(PendingDownload pendingDownload : downloadList){
                    String downloadPath = pendingDownload.getPath();
                    String downloadDestination = pendingDownload.getDestination();
                    if(downloadDestination == null || downloadPath == null){
                        logger.error("NULL in queue-download "+downloadPath+" -> "+downloadDestination);
                    }else {
                        local.download(pendingDownload.getPath(), pendingDownload.getDestination(), host);
                    }
                }
            }
            timestamps.put("downloadStop",System.currentTimeMillis());
            pendingDownloads.clear();
        }
    }
    public void setStartEnv(SshSession session){
        //session.clearCommand();
        session.sh("env");
        String env = session.getOutput();
        Env start = new Env(env);
        startEnvs.put(session.getHost(),start);
        logger.info("{} env = \"{}\" \n Env.start = {}",session.getHost(),env,start.debug());

    }
    public void done(){
        coordinator.clearWaiters();
        dispatcher.stop();
    }
    public Json pendingDownloadJson(){
        Json rtrn = new Json();
        for(Host host : pendingDownloads.keys()){
            Json hostJson = new Json();
            rtrn.set(host.toString(),hostJson);
            List<PendingDownload> pendings = pendingDownloads.get(host);
            for(PendingDownload pending : pendings){
                Json pendingJson = new Json();
                pendingJson.set("path",pending.getPath());
                pendingJson.set("dest",pending.getDestination());
                hostJson.add(pendingJson);
            }
        }
        return rtrn;
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

            toWrite.set("timestamps",timestamps);
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
        if(aborted.compareAndSet(false,true)){
            coordinator.clearWaiters();
            stageUpdated.set(this,Stage.Run);//set the stage as run so dispatcher.stop call to DispatchObserver.postStop will set it to Cleanup
            dispatcher.stop();//interrupts working threads and stops dispatching next commands
            //runPendingDownloads();//added here in addition to queueCleanupScripts to download when run aborts
            //runLatch.countDown();
        }
    }
    private void queueSetupScripts(){
        logger.debug("{}.setup",this);

        //Observer to set the Env.Diffs
        dispatcher.addScriptObserver(envObserver);

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
    }

    @Override
    public String toString(){return config.getName()+" -> "+outputPath;}

    //TODO separate coordinators for each stage?
    private boolean initializeCoordinator(){
        List<String> noSignal = new ArrayList<>();
        Set<String> signaled = new HashSet<>();
        Consumer<StageSummary> setupCoordinator = (stageSummary)->{
            stageSummary.getSignals().forEach((signalName)->{
                int count = stageSummary.getSignalCount(signalName);
                coordinator.initialize(signalName,count);
                signaled.add(signalName);
            });
            stageSummary.getWaiters().stream().filter((waitName)->
                    !signaled.contains(waitName)
            ).forEach((notSignaled)->{
                noSignal.add(notSignaled);
            });
        } ;

        setupCoordinator.accept(config.getSetupStage());
        if(!noSignal.isEmpty()){
            noSignal.forEach((notSignaled)->{
                runLogger.error("{} setup scripts missing signal for {}",this,notSignaled);
            });
            return false;
        }
        setupCoordinator.accept(config.getRunStage());

        if(!noSignal.isEmpty()){
            noSignal.forEach((notSignaled)->{
                runLogger.error("{} run scripts missing signal for {}",this,notSignaled);
            });
            return false;
        }
        setupCoordinator.accept(config.getCleanupStage());
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
        timestamps.put("start",System.currentTimeMillis());

        if(config.hasErrors()){
            config.getErrors().forEach(logger::error);
            config.getErrors().forEach(runLogger::error);
            timestamps.put("stop",System.currentTimeMillis());
            return;
        }

        boolean coordinatorInitialized = initializeCoordinator();
        if(!coordinatorInitialized){
            timestamps.put("stop",System.currentTimeMillis());
            return;
        }

        runLogger.info("{} starting state:\n{}",config.getName(),config.getState().tree());
        queueSetupScripts();
        dispatcher.start();
        try {
            runLatch.await();

        } catch (InterruptedException e) {
            //e.printStackTrace();
        } finally{
            timestamps.put("stop",System.currentTimeMillis());
        }
        //moved to here because abort would avoid the cleanup in postRun()
        fileAppender.stop();
        consoleAppender.stop();
    }
    private void queueRunScripts(){
        logger.debug("{}.queueRunScripts",this);

        List<Callable<Boolean>> connectSessions = new LinkedList<>();

        for(Host host : config.getRunHosts()){

            Env.Diff diff = setupEnvDiff.containsKey(host) ? setupEnvDiff.get(host) : new Env.Diff(Collections.emptyMap(),Collections.emptySet());

            final StringBuilder setEnv = new StringBuilder();
            final StringBuilder unsetEnv = new StringBuilder();
            try {

                //TODO BUG the bug is that we don't escape long env values with spaces, maybe separate each set?
                diff.keys().forEach(key -> {
                    String keyValue = diff.get(key);
                    if (setEnv.length() > 0) {
                        setEnv.append(" ");
                    }
                    setEnv.append(" "+key+"="+StringUtil.quote(keyValue));
                });
            }catch(Exception e){
                e.printStackTrace();
                System.exit(0);
            }
            diff.unset().forEach(key->{
              unsetEnv.append(" "+key);
            });
            String updateEnv = (setEnv.length()>0? "export"+setEnv.toString():"")+(unsetEnv.length()>0?";unset"+unsetEnv.toString():"");
            if(!updateEnv.isEmpty()) {
                logger.info("{} update env from setup {}", host, updateEnv);
            }

            for(ScriptCmd scriptCmd : config.getRunCmds(host)){
                connectSessions.add(()->{

                    State hostState = config.getState().addChild(host.getHostName(),State.HOST_PREFIX);
                    //added a script instance state to allow two scripts on same host
                    State scriptState = hostState.getChild(scriptCmd.getName()).getChild(scriptCmd.getName()+"-"+scriptCmd.getUid());

                    long start = System.currentTimeMillis();

                    Profiler profiler = profiles.get(scriptCmd.getName()+"-"+scriptCmd.getUid()+"@"+host);

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
            boolean ok = dispatcher.getExecutor().invokeAll(connectSessions,60,TimeUnit.SECONDS).stream().map((f) -> {
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

            if(!ok){
                logger.error("Error: trying to connect shell sessions for run stage");
                this.abort();
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    private void queueCleanupScripts(){
        logger.info("{}.queueCleanupScripts",this);
        //run before cleanup
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
    }
    private void postRun(){
        logger.debug("{}.postRun",this);
        runLogger.info("{} closing state:\n{}",config.getName(),config.getState().tree());
        runLatch.countDown();
    }
    public CommandDispatcher getDispatcher(){return dispatcher;}
    public Coordinator getCoordinator(){return coordinator;}
    public String getOutputPath(){ return outputPath;}

}
