package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.qdup.cmd.*;
import io.hyperfoil.tools.qdup.cmd.impl.Download;
import io.hyperfoil.tools.qdup.cmd.impl.RoleEnv;
import io.hyperfoil.tools.qdup.cmd.impl.ScriptCmd;
import io.hyperfoil.tools.qdup.config.Role;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.shell.AbstractShell;
import io.hyperfoil.tools.qdup.shell.ContainerShell;
import io.hyperfoil.tools.yaup.AsciiArt;
import io.hyperfoil.tools.yaup.HashedSets;
import io.hyperfoil.tools.yaup.StringUtil;
import io.hyperfoil.tools.yaup.json.Json;
import io.hyperfoil.tools.yaup.time.SystemTimer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.slf4j.Logger;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.hyperfoil.tools.qdup.cmd.PatternValuesMap.QDUP_GLOBAL;
import static io.hyperfoil.tools.qdup.cmd.PatternValuesMap.QDUP_GLOBAL_ABORTED;

/**
 * Created by wreicher
 *
 */
public class Run implements Runnable, DispatchObserver {

    public static final String RUN_LOGGER_NAME = "qdup.run";


    private static final XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

    private final static AtomicReferenceFieldUpdater<Run,Stage> stageUpdated = AtomicReferenceFieldUpdater.newUpdater(Run.class, Stage.class,"stage");

    //TODO does a static logger name retain file appenders from previous Runs?

    static class JitterCheck implements Runnable{

        @Override
        public void run() {
            long period = 50;
            long threshold = 100;
            long lastTimestamp = System.nanoTime();
            while (true) {
                try {
                    Thread.sleep(period);
                } catch (InterruptedException e) {
                    logger.debug("Interrupted, terminating jitter watchdog");
                    boolean interrupted = Thread.interrupted();
                    return;
                }
                long currentTimestamp = System.nanoTime();
                long delay = TimeUnit.NANOSECONDS.toMillis(currentTimestamp - lastTimestamp);
                if (delay > threshold) {
                    logger.error("Jitter watchdog was not invoked for {} ms (threshold is {} ms); please check your GC settings.", delay, threshold);
                }
                lastTimestamp = currentTimestamp;
            }
        }
    }

    private volatile Stage stage;

    private final List<RunObserver> runObservers;

    private Map<String,Long> timestamps;
    private RunConfig config;
    private String outputPath;
    private AtomicBoolean aborted;
    private Coordinator coordinator;
    private Dispatcher dispatcher;
    private Profiles profiles;
    private Local local;

    private HashedSets<Host, Download> pendingDownloads;
    private HashedSets<Host,String> pendingDeletes;

    private CountDownLatch runLatch = new CountDownLatch(1);

    XLogger runLogger;// = XLoggerFactory.getXLogger(RUN_LOGGER_NAME);
    XLogger stateLogger;// = XLoggerFactory.getXLogger(STATE_LOGGER_NAME);

    FileAppender logAppender;
    private List<Stage> skipStages;


    public Run(String outputPath,RunConfig config,Dispatcher dispatcher){
        if(config==null || dispatcher==null){
            throw new NullPointerException("Run config and dispatcher cannot be null");
        }

        this.runObservers = new LinkedList<>();

        this.config = config;

        this.outputPath = outputPath;
        this.dispatcher = dispatcher;
        this.dispatcher.addDispatchObserver(this);
        this.stage = Stage.Pending;
        this.aborted = new AtomicBoolean(false);

        this.timestamps = new LinkedHashMap<>();
        this.profiles = new Profiles();
        this.coordinator = new Coordinator(new Globals(config.getJsFunctions()));
        this.local = new Local(config);
        this.skipStages = config.getSkipStages();

        coordinator.addObserver((signal_name)->{
            runLogger.info(
                    "{}reached {}{}",
                    config.isColorTerminal()?AsciiArt.ANSI_CYAN:"",
                    signal_name,
                    config.isColorTerminal()?AsciiArt.ANSI_RESET:""
            );
        });
        this.pendingDownloads = new HashedSets<>();
        this.pendingDeletes = new HashedSets<>();
        updateCoordinatedSettings();
    }

    private void updateCoordinatedSettings(){
        if(config.isStreamLogging()){
            coordinator.setSetting(Globals.STREAM_LOGGING,true);
        }
    }

    private boolean removeLogger(){
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = LoggerConfig.createLogger(false, Level.INFO, Run.RUN_LOGGER_NAME,"false",new AppenderRef[0],null,config,null);

        if(logAppender!=null){
            ctx.getLogger(Run.RUN_LOGGER_NAME).removeAppender(logAppender);
            loggerConfig.removeAppender(logAppender.getName());
            logAppender.stop();

            String loggerName = getLoggerName();

            ctx.getLogger(runLogger.getName()).removeAppender(logAppender);
            if(ctx.getLogger(Run.RUN_LOGGER_NAME).getAppenders().containsKey(Run.RUN_LOGGER_NAME)){
                Appender toRemove = ctx.getLogger(Run.RUN_LOGGER_NAME).getAppenders().get(Run.RUN_LOGGER_NAME);
                ctx.getLogger(Run.RUN_LOGGER_NAME).removeAppender(toRemove);
                loggerConfig.removeAppender(Run.RUN_LOGGER_NAME);
                if(toRemove.isStarted()){
                    toRemove.stop();
                }
            }
            return true;
        }
        return false;
    }

    private String getLoggerName(){
        String loggerName = "qdup."+getOutputPath().replaceAll(FileSystems.getDefault().getSeparator(),"_");
        return loggerName;
    }

    private String getStateLoggerName(){
        return getLoggerName().concat(".state");
    }

    boolean ensureLogger(){
        if(logAppender == null || !logAppender.getFileName().contains(getOutputPath())){
            synchronized (this){
                final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
                final Configuration config = ctx.getConfiguration();
                String loggerName = getLoggerName();
                LoggerConfig runLoggerConfig = LoggerConfig.createLogger(true, Level.INFO, loggerName,"false",new AppenderRef[0],null,config,null);
                config.addLogger(runLoggerConfig.getName(),runLoggerConfig);
                runLoggerConfig.setAdditive(true); //doesn't work
                runLogger = XLoggerFactory.getXLogger(loggerName);
                LoggerConfig stateLoggerConfig = LoggerConfig.createLogger(true, Level.ALL, runLogger.getName()+".state","false",new AppenderRef[0],null,config,null);
                stateLoggerConfig.setAdditive(true);
                config.addLogger(stateLoggerConfig.getName(),stateLoggerConfig);
                stateLogger = XLoggerFactory.getXLogger(getStateLoggerName());
                if (logAppender == null) {
                    Path outputPath = Paths.get(getOutputPath());
                    if(!Files.exists(outputPath)){
                        try {
                            Files.createDirectories(outputPath);
                        } catch (IOException e) {
                            logger.error("error creating output directory "+getOutputPath(),e);
                            return false;
                        }
                    }
                    logAppender = FileAppender.newBuilder()
                        //unique name for restart issue
                        .setName(Run.RUN_LOGGER_NAME)
                        .withFileName(Paths.get(getOutputPath(),"run.log").toString())
                        .withImmediateFlush(true)
                        .withAppend(false)//changed from false to test output during mvn test
                        .setLayout(PatternLayout.newBuilder()
                            .withPattern("%d{HH:mm:ss.SSS} %msg%n%throwable")
                            .build())
                        .build();
                    logAppender.start();
                    ctx.updateLoggers();
                    config.getLoggers().get(loggerName).addAppender(logAppender, Level.ALL,null);
                    ctx.updateLoggers();
                }
            }
        }


        return logAppender == null;
    }


    public void addRunObserver(RunObserver observer){this.runObservers.add(observer);}
    public void removeRunObserver(RunObserver observer){this.runObservers.remove(observer);}
    public boolean hasRunObserver(){return !this.runObservers.isEmpty();}


    public void error(String message){
        ensureLogger();
        runLogger.error(message);

    }
    public void log(String message){
        ensureLogger();
        runLogger.info(message);
    }

    @Override
    public void preStart(){
        ensureLogger();
        timestamps.put(stage.getName()+"Start",System.currentTimeMillis());
    }
    @Override
    public void postStop(){
        ensureLogger();
        timestamps.put(stage.getName()+"Stop",System.currentTimeMillis());
        boolean started = nextStage();
        if(!started){

        }
        //this.setupEnvDiff.clear();//why are we clearing the setup env diff? don't we need it for cleanup too?
    }
    private boolean nextStage(){
        boolean startDispatcher = false;
        if(hasRunObserver()){
            for(RunObserver observer : runObservers){
                observer.postStage(stage);
            }
        }
        switch (stage){
            case Pending:
                //removed because no longer want to use cmds to create tmp_dir
//                if(stageUpdated.compareAndSet(this,Stage.Pending, Stage.PreSetup)){
//                    startDispatcher = queuePreSetupScripts();
//                }
                if(stageUpdated.compareAndSet(this,Stage.Pending, Stage.Setup)){
                    if(skipStages.contains(stage)){
                        return nextStage();
                    } else {
                        startDispatcher = queueSetupScripts();
                    }
                }
                break;
            case PreSetup:
                if(stageUpdated.compareAndSet(this,Stage.PreSetup, Stage.Setup)){
                    if(skipStages.contains(stage)){
                        return nextStage();
                    } else {
                        startDispatcher = queueSetupScripts();
                    }
                }
                break;
            case Setup:
                //if we are able to set the stage to Run
                if(stageUpdated.compareAndSet(this,Stage.Setup,Stage.Run)){
                    if(skipStages.contains(stage)){
                        return nextStage();
                    } else {
                        startDispatcher = queueRunScripts();
                    }
                }
                break;
            case Run:
                if(stageUpdated.compareAndSet(this,Stage.Run,Stage.PreCleanup)){
                    runPendingDownloads();
                    return nextStage();
                }
                break;
            case PreCleanup:
                if(stageUpdated.compareAndSet(this,Stage.PreCleanup,Stage.Cleanup)){
                    if(skipStages.contains(stage)){
                        return nextStage();
                    } else {
                        startDispatcher = queueCleanupScripts();
                    }
                }
                break;
            case Cleanup:
                if(stageUpdated.compareAndSet(this,Stage.Cleanup,Stage.PostCleanup)) {
                    runPendingDownloads();//download anything queued during cleanup
                    runPendingDeletes();
                    return nextStage();
                }
                break;
            case PostCleanup:
                if(stageUpdated.compareAndSet(this,Stage.PostCleanup,Stage.Done)) {
                    if(skipStages.contains(stage)){
                        return nextStage();
                    } else {
                        postRun();//release any latches blocking a call to run()
                    }
                }
                break;
            default:
                if(stageUpdated.compareAndSet(this,Stage.PostCleanup,Stage.Done)){
                    //happens for abort
                    postRun();
                    break;
                }
        }
        if(startDispatcher){
            if(hasRunObserver()){
                for(RunObserver observer: runObservers){
                    observer.preStage(stage);
                }
            }
            dispatcher.start();
        }
        return startDispatcher;
    }

    public Stage getStage(){return stage;}
    public Local getLocal(){return local;}
    public RunConfig getConfig(){return config;}
    public boolean isAborted(){return aborted.get();}
    public Logger getRunLogger(){
        ensureLogger();
        return runLogger;
    }
    public Logger getStateLogger(){
        ensureLogger();
        return stateLogger;
    }

    public void addPendingDelete(Host host,String path){
        pendingDeletes.put(host,path);
    }
    public void addPendingDownload(Host host,String path,String destination, Long maxSize){
        pendingDownloads.put(host,new Download(path,destination,maxSize));
    }
    public void runPendingDeletes(){
        if(!pendingDeletes.isEmpty()){
            for(Host host : pendingDeletes.keys()){
                AbstractShell shell = AbstractShell.getShell(
                    host,
                    "",
                    getDispatcher().getScheduler(),
                    getConfig().getState().getSecretFilter(),
                    false);
                Set<String> deleteList = pendingDeletes.get(host);
                for(String delete : deleteList){
                    shell.execSync("rm "+delete);
                }
                shell.close(true);
            }
        }
    }
    public void runPendingDownloads(){
        //TODO synchronize so only one thread tries the downloads (run ending while being aborted?)
        if(!pendingDownloads.isEmpty()){
            logger.info("{} downloading queued downloads",config.getName());
            timestamps.put("downloadStart",System.currentTimeMillis());
            for(Host host : pendingDownloads.keys()){
                Set<Download> downloadList = pendingDownloads.get(host);
                for(Download pendingDownload : downloadList){
                    String downloadPath = pendingDownload.getPath();
                    String downloadDestination = pendingDownload.getDestination();
                    if(downloadDestination == null || downloadPath == null){
                        logger.error("NULL in queue-download "+downloadPath+" -> "+downloadDestination);
                    }else {
                        pendingDownload.execute(null, () -> local, () -> host);
                    }
                }
            }
            timestamps.put("downloadStop",System.currentTimeMillis());
            pendingDownloads.clear();
        }else{
        }
    }
    public void done(){
        coordinator.clearWaiters();
        dispatcher.stop(false);
    }
    public Json pendingDownloadJson(){
        Json rtrn = new Json();
        for(Host host : pendingDownloads.keys()){
            Json hostJson = new Json();
            rtrn.set(host.toString(),hostJson);
            Set<Download> pendings = pendingDownloads.get(host);
            for(Download pending : pendings){
                Json pendingJson = new Json();
                pendingJson.set("path",pending.getPath());
                pendingJson.set("dest",pending.getDestination());
                hostJson.add(pendingJson);
            }
        }
        return rtrn;
    }
    public Json getProfiles(){return profiles.getJson();}
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
            toWrite.set("profiles",getProfiles());

            String filtered = getConfig().getState().getSecretFilter().filter(toWrite.toString(2));

            out.write(filtered.getBytes());
            out.flush();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     *
     * @param skipCleanUp
     * @return true iff this was the call that aborted the current run
     */
    public boolean abort(Boolean skipCleanUp){
        if(aborted.compareAndSet(false,true)){
            getConfig().getState().set(QDUP_GLOBAL+"."+QDUP_GLOBAL_ABORTED,true);//add ABORTED state for any cleanup scripts
            coordinator.clearWaiters();
            if (!skipCleanUp && stage.isBefore(Stage.Cleanup)) {
                stageUpdated.set(this, Stage.Run);//set the stage as run so dispatcher.stop call to DispatchObserver.postStop will set it to Cleanup
            } else {
                logger.warn("Skipping cleanup - Abort has been defined to not run any cleanup scripts");
                stageUpdated.set(this, Stage.PostCleanup);//set the stage as PostCleanup so dispatcher.stop call to DispatchObserver.postStop will set it to Done

            }
            dispatcher.stop(false);//interrupts working threads and stops dispatching next commands
            //runPendingDownloads();//added here in addition to queueCleanupScripts to download when run aborts
            //abort doesn't end the run, cleanup ends the run
            //runLatch.countDown();
            return true;//we aborted
        }else{
            logger.info("abort called when already aborted");
            dispatcher.stop(false);
        }
        return false;
    }

    @Override
    public String toString(){return config.getName()+" -> "+outputPath;}

    //TODO separate coordinators for each stage?
    private boolean initializeCoordinator(){
        config.getSignalCounts().forEach((name,count)->{
            coordinator.setSignal(name,count.intValue());
        });
        return true;
    }

    @Override
    public void run() {
        ensureLogger();
        if(Stage.Pending.equals(stage)){

            //TODO enable jitter check? what amount of jitter matters for qDup?
//            Thread jitterThread = new Thread(new JitterCheck(),"jitter-check");
//            jitterThread.setDaemon(true);
//            jitterThread.start();

            timestamps.put("start",System.currentTimeMillis());
            if(config.hasErrors()){
                logger.error("cannot start run due to config errors");
                config.getErrors().forEach(e->logger.error(e.toString()));
                config.getErrors().forEach(e->runLogger.error(e.toString()));
                timestamps.put("stop",System.currentTimeMillis());
                return;
            }
            boolean coordinatorInitialized = initializeCoordinator();
            if(!coordinatorInitialized){
                logger.error("cannot start run due to coordinator errors");
                timestamps.put("stop",System.currentTimeMillis());
                return;
            }
            String tree = config.getState().tree();

            String filteredTree = getConfig().getState().getSecretFilter().filter(tree);
            stateLogger.debug("{} starting state:\n{}",config.getName(),filteredTree);
            boolean ok = nextStage();
            if(ok) {
                try {
                    runLatch.await();
                } catch (InterruptedException e) {
                    //e.printStackTrace();
                } finally{
                    timestamps.put("stop",System.currentTimeMillis());
                }

            }else{
                //TODO failed to start

            }
            //moved to here because abort would avoid the cleanup in postRun()
            //will need to move if runLatch becomes optional

            //logAppender.stop(5,TimeUnit.SECONDS);
            removeLogger();
            writeRunJson();
        }
    }
    public boolean joinLatch(){
        try {
            runLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return true;
    }
    public boolean joinLatch(long timeout,TimeUnit unit){
        try{
            return runLatch.await(timeout,unit);
        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
            return false;
        }
    }
    private boolean connectAll(List<Callable<Boolean>> toCall,int timeout){
        boolean ok = false;
        try {
            ok = getDispatcher().invokeAll(toCall/*,timeout, TimeUnit.SECONDS*/).stream().map((f) -> {

                boolean rtrn = false;
                try {
                    if(f != null) { //can be null when failed to authenticate
                        rtrn = f.get();
                    }else{

                    }
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
        return ok;
    }
    private Script createTempDirectory(){
        Script script = new Script("create-qdup-temp");
        script.then(
           Cmd.sh(this.config.getSetting(RunConfig.MAKE_TEMP_KEY,RunConfigBuilder.MAKE_TEMP_CMD).toString())
           .then(Cmd.setState(State.HOST_PREFIX+RunConfigBuilder.TEMP_DIR))
        );

        return script;
    }
    private Script removeTempDirectory(){
        Script script = new Script("remove-qdup-temp");
        script.then(
           Cmd.sh(
              this.config.getSetting(RunConfig.REMOVE_TEMP_KEY,RunConfigBuilder.REMOVE_TEMP_CMD) +
              " " +
              StringUtil.PATTERN_PREFIX+RunConfigBuilder.TEMP_DIR+StringUtil.PATTERN_SUFFIX)
        );

        return script;
    }

    private boolean queuePostCleanupScripts(){
        logger.debug("{}.post-cleanup",this);
        List<Callable<Boolean>> connectSessions = new LinkedList<>();

        Script setup = removeTempDirectory();
        config.getAllHostsInRoles().forEach(host->{
            connectSessions.add(()->{
                String name = "post-cleanup@"+host.getShortHostName();
                if(config.getSettings().has(RunConfig.TRACE_NAME)){
                    name = name+"."+Cmd.populateStateVariables(config.getSettings().getString(RunConfig.TRACE_NAME),null,getConfig().getState(),getCoordinator(),Json.fromMap(getTimestamps()));
                }
                AbstractShell shell = AbstractShell.getShell(
                        host,
                        "",
                        getDispatcher().getScheduler(),
                        getConfig().getState().getSecretFilter(),
                        isTrace(name)
                );
                shell.setName(name);

//                SshSession session = new SshSession(
//                    name,
//                    host,
//                    config.getKnownHosts(),
//                    config.getIdentity(),
//                    config.getPassphrase(),
//                    config.getTimeout(),
//                    "",
//                    getDispatcher().getScheduler(),
//                    isTrace(name));
                if ( shell.isReady() ) {
                    //TODO configure session delay
                    //session.setDelay(SuffixStream.NO_DELAY);
                    ScriptContext scriptContext = new ScriptContext(
                        shell,
                        config.getState().getChild(host.getHostName(), State.HOST_PREFIX),
                        this,
                        profiles.get(name),
                        setup,
                            (Boolean)config.getSetting("check-exit-code",false)
                    );
                    getDispatcher().addScriptContext(scriptContext);
                    return shell.isOpen();
                }
                else {
                    shell.close();
                    return false;
                }
            });
        });

        boolean ok = true;
        if(!connectSessions.isEmpty()) {
            ok = connectAll(connectSessions, 60);
            if (!ok) {
                abort(false);
            }

        }else{

        }
        return ok;

    }

    private boolean queuePreSetupScripts(){
        logger.debug("{}.pre-setup",this);
        List<Callable<Boolean>> connectSessions = new LinkedList<>();

        Script setup = createTempDirectory();
        config.getAllHostsInRoles().forEach(host->{
            connectSessions.add(()->{
                String name = "pre-setup@"+host.getShortHostName()+"."+Cmd.populateStateVariables(config.getSettings().getString(RunConfig.TRACE_NAME),null,getConfig().getState(),getCoordinator(),Json.fromMap(getTimestamps()));
                AbstractShell shell = AbstractShell.getShell(
                        host,
                        "",
                        getDispatcher().getScheduler(),
                        getConfig().getState().getSecretFilter(),
                        isTrace(name)
                );
                shell.setName(name);
                if ( shell.isReady() ) {
                    //TODO configure session delay
                    //session.setDelay(SuffixStream.NO_DELAY);
                    ScriptContext scriptContext = new ScriptContext(
                        shell,
                        config.getState().getChild(host.getHostName(), State.HOST_PREFIX),
                        this,
                        profiles.get(name),
                        setup,
                            (Boolean)config.getSetting("check-exit-code",false)
                    );
                    getDispatcher().addScriptContext(scriptContext);
                    return shell.isOpen();
                }
                else {
                    shell.close();
                    return false;
                }
            });
        });

        boolean ok = true;
        if(!connectSessions.isEmpty()) {
            ok = connectAll(connectSessions, 60);
            if (!ok) {
                abort(false);
            }

        }else{

        }
        return ok;

    }
    private boolean queueSessions(List<Callable<Boolean>> connectSessions){
        boolean ok = true;
        if(!connectSessions.isEmpty()){
            ok = connectAll(connectSessions, 60);
            if (!ok) {
                abort(false);
            }
        }
        return ok;
    }
    private boolean queueSetupScripts(){
        logger.debug("{}.setup",this);

        //Observer to set the Env.Diffs
        List<Callable<Boolean>> connectSessions = new LinkedList<>();
        //TODO don't run an ALL-setup but rather put it in the start of each connection?
        config.getRoleNames().stream().forEach(roleName->{
            final Role role = config.getRole(roleName);
            if(!role.getSetup().isEmpty()){
               final Script setup = new Script(roleName+"-setup");
               setup.then(new RoleEnv(role,true));
               role.getSetup().forEach(cmd->{
                   setup.then(cmd);
               });
               setup.then(new RoleEnv(role,false));

               role.getHosts(config).forEach(host->{
                   connectSessions.add(()->{
                       String name = roleName+"-setup@"+host.getShortHostName()+"."+Cmd.populateStateVariables(config.getSettings().getString(RunConfig.TRACE_NAME),null,getConfig().getState(),getCoordinator(),Json.fromMap(getTimestamps()));
                       AbstractShell shell =  AbstractShell.getShell(
                               host,
                               "",
                               getDispatcher().getScheduler(),
                               getConfig().getState().getSecretFilter(),
                               isTrace(name)
                       );
                       shell.setName(name);
                       if ( shell.isReady() ) {
                           //TODO configure session delay
                           //session.setDelay(SuffixStream.NO_DELAY);
                           ScriptContext scriptContext = new ScriptContext(
                                   shell,
                                   config.getState().getChild(host.getHostName(), State.HOST_PREFIX),
                                   this,
                                   profiles.get(name),
                                   setup,
                                   (Boolean)config.getSetting("check-exit-code",false)
                           );
                           if(config.isStreamLogging()){
                               shell.addLineObserver("stream",(line)->{
                                   ensureLogger();
                                    scriptContext.log(line);
                               });
                           }

                           getDispatcher().addScriptContext(scriptContext);
                           return shell.isOpen();
                       }
                       else {
                           logger.error("setup failed to connect "+host.getSafeString());
                           shell.close();
                           return false;
                       }
                   });
               });
            }

        });
        boolean ok = true;
        if(!connectSessions.isEmpty()) {
            ok = connectAll(connectSessions, 60);
            if (!ok) {
                getRunLogger().error("failed to connect all ssh sessions for setup");
                abort(false);
            }

        }else{

        }
        return ok;
    }
    private boolean queueRunScripts(){
        logger.debug("{}.queueRunScripts",this);

        List<Callable<Boolean>> connectSessions = new LinkedList<>();

        Role allRole = config.getRole(RunConfigBuilder.ALL_ROLE);
        for(String roleName : config.getRoleNames()){
            Role role = config.getRole(roleName);
            if (!role.getRun().isEmpty()) {
                for (ScriptCmd script : role.getRun()) {
                    for (Host host : role.getHosts(config)) {
                        State hostState = config.getState().getChild(host.getHostName(), State.HOST_PREFIX);
                        State scriptState = hostState.getChild(script.getName()).getChild("id=" + script.getUid());
                        SystemTimer timer = profiles.get(script.getName() + "-" + script.getUid() + "@" + host);
                        Env env = role.hasEnvironment(host) ? role.getEnv(host) : new Env();
                        if(!role.getName().equals(RunConfigBuilder.ALL_ROLE) && allRole!=null && allRole.hasEnvironment(host)){
                            env.merge(allRole.getEnv(host));
                        }
                        String setupCommand = env.getDiff().getCommand();
                        connectSessions.add(() -> {
                            String name = script.getName()+":"+script.getUid()+"@"+host.getShortHostName()+"."+Cmd.populateStateVariables(config.getSettings().getString(RunConfig.TRACE_NAME),null,getConfig().getState(),getCoordinator(),Json.fromMap(getTimestamps()));
                            timer.start("connect:" + host.toString());
                            AbstractShell shell = AbstractShell.getShell(
                                    host,
                                    setupCommand,
                                    getDispatcher().getScheduler(),
                                    getConfig().getState().getSecretFilter(),
                                    isTrace(name)
                            );
                            shell.setName(name);
                            if (shell.isReady()) {
                                //shell.shSync(setupCommand); //moved into getShell
                                //session.setDelay(SuffixStream.NO_DELAY);
                                timer.start("context:" + host.toString());
                                ScriptContext scriptContext = new ScriptContext(
                                        shell,
                                        scriptState,
                                        this,
                                        timer,
                                        script,
                                        (Boolean)config.getSetting("check-exit-code",false)
                                );
                                if(config.isStreamLogging()){
                                    shell.addLineObserver("stream",(line)->{
                                        ensureLogger();
                                        scriptContext.log(line);
                                    });
                                }
                                getDispatcher().addScriptContext(scriptContext);
                                boolean rtrn = shell.isOpen();
                                timer.start("waiting for start");
                                return rtrn;
                            } else {
                                logger.error("run failed to connect "+host.getSafeString()
                                        +(host.hasPassword() ?
                                            ", verify ssh works with the provided username and password" :
                                            ", verify password-less ssh works with the selected keys"
                                        )
                                );
                                shell.close();
                                return false;
                            }
                        });
                    }
                }
            }
        }
        boolean ok = true;

        if(!connectSessions.isEmpty()) {
            ok = false; // it better be set by dispatcher then
            try {
                ok = getDispatcher().invokeAll(connectSessions).stream().map((f)->{
                    boolean rtrn = false;
                    try{
                        rtrn = f.get();
                    } catch (InterruptedException e){
                        e.printStackTrace();
                    } catch (Exception e ){
                        e.printStackTrace();
                    }
                    return rtrn;
                }).collect(Collectors.reducing(Boolean::logicalAnd)).get();
            } catch (InterruptedException e){

            }
            if(!ok){
                getRunLogger().error("failed to connect all ssh sessions for run");
                abort(false);
            }
        }else{

        }
        return ok;
    }

    private boolean isTrace(String value){
        //return true; //temporarily debug everything
        return config.getTracePatterns().stream().anyMatch(pattern -> value.contains(pattern) || Pattern.matches(pattern,value));
    }

    private boolean queueCleanupScripts(){
        //run before cleanup
        logger.debug("{}.cleanup",this);

        //Observer to set the Env.Diffs
        List<Callable<Boolean>> connectSessions = new LinkedList<>();

        config.getRoleNames().forEach(roleName->{
            Role role = config.getRole(roleName);
            if(!role.getCleanup().isEmpty()){
                final Script cleanup = new Script(roleName+"-cleanup");
                role.getCleanup().forEach(cmd->{
                    cleanup.then(cmd);
                });
                role.getHosts(config).forEach(host->{
                    String setupCommand = role.hasEnvironment(host) ? role.getEnv(host).getDiff().getCommand() : "";
                    connectSessions.add(()->{
                        String name = roleName + "-cleanup@"+host.getShortHostName()+"."+Cmd.populateStateVariables(config.getSettings().getString(RunConfig.TRACE_NAME),null,getConfig().getState(),getCoordinator(),Json.fromMap(getTimestamps()));
                        AbstractShell shell = AbstractShell.getShell(
                                host,
                                "",
                                getDispatcher().getScheduler(),
                                getConfig().getState().getSecretFilter(),
                                isTrace(name)
                        );
                        shell.setName(name);
                        if ( shell.isReady() ) {

                            //session.setDelay(SuffixStream.NO_DELAY);
                            ScriptContext scriptContext = new ScriptContext(
                                    shell,
                                    config.getState().getChild(host.getHostName(), State.HOST_PREFIX),
                                    this,
                                    profiles.get(roleName + "-cleanup@" + host.getShortHostName()),
                                    cleanup,
                                    (Boolean)config.getSetting("check-exit-code",false)
                            );
                            if(config.isStreamLogging()){
                                shell.addLineObserver("stream",(line)->{
                                    ensureLogger();
                                    scriptContext.log(line);
                                });
                            }

                            getDispatcher().addScriptContext(scriptContext);
                            return shell.isOpen();
                        }
                        else {
                            logger.error("cleanup failed to connect "+host.getSafeString());
                            shell.close();
                            return false;
                        }
                    });
                });
            }
        });
        boolean ok = true;
        if(!connectSessions.isEmpty()){
            ok = connectAll(connectSessions,60);
            if(!ok){
                getRunLogger().error("failed to connect all ssh sessions for cleanup");
                abort(false);
            }

        }else{

        }
        return ok;
    }
    private void postRun(){
        logger.debug("{}.postRun",this);
        getConfig().getAllHostsInRoles().forEach(host->{
            if(host.isContainer() && host.needStopContainer() && host.startedContainer()){
                if(host.hasStopContainer()){
                    ContainerShell containerShell = new ContainerShell(
                        host, 
                        "",
                        dispatcher.getScheduler(), 
                        getConfig().getState().getSecretFilter(), 
                        false);
                    containerShell.stopContainerIfStarted();
                }
            }
        });
        String tree = config.getState().tree();//tree filters itself
        stateLogger.debug("{} closing state:\n{}",config.getName(),tree);
        runLatch.countDown();
    }
    public Dispatcher getDispatcher(){return dispatcher;}
    public Coordinator getCoordinator(){return coordinator;}
    public String getOutputPath(){ return outputPath;}

    public Map<String,Long> getTimestamps(){return Collections.unmodifiableMap(new LinkedHashMap<>(timestamps));}
}
