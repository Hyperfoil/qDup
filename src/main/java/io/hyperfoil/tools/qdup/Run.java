package io.hyperfoil.tools.qdup;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.DispatchObserver;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.cmd.Script;
import io.hyperfoil.tools.qdup.cmd.ScriptContext;
import io.hyperfoil.tools.qdup.cmd.impl.RoleEnv;
import io.hyperfoil.tools.qdup.cmd.impl.ScriptCmd;
import io.hyperfoil.tools.qdup.config.Role;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.yaup.AsciiArt;
import io.hyperfoil.tools.yaup.HashedSets;
import io.hyperfoil.tools.yaup.StringUtil;
import io.hyperfoil.tools.yaup.json.Json;
import io.hyperfoil.tools.yaup.time.SystemTimer;
import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by wreicher
 *
 */
public class Run implements Runnable, DispatchObserver {

    final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());
    private final static AtomicReferenceFieldUpdater<Run,Stage> stageUpdated = AtomicReferenceFieldUpdater.newUpdater(Run.class, Stage.class,"stage");

    //TODO does a static logger name retain file appenders from previous Runs?
    public static final String RUN_LOGGER_NAME = "qdup.run";
    public static final String STATE_LOGGER_NAME = "qdup.run.state";

    class JitterCheck implements Runnable{

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

    private volatile Stage stage = Stage.Pending;

    private final List<RunObserver> runObservers;

    private Map<String,Long> timestamps;
    private RunConfig config;
    private String outputPath;
    private AtomicBoolean aborted;
    private Coordinator coordinator;
    private Dispatcher dispatcher;
    private Profiles profiles;
    private Local local;

    private HashedSets<Host,PendingDownload> pendingDownloads;
    private HashedSets<Host,String> pendingDeletes;

    private CountDownLatch runLatch = new CountDownLatch(1);
    private Logger runLogger;
    private Logger stateLogger;
    private ConsoleAppender<ILoggingEvent> consoleAppender;
    private FileAppender<ILoggingEvent> fileAppender;

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
        this.coordinator = new Coordinator();
        this.local = new Local(config);

        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        PatternLayoutEncoder consoleLayout = new PatternLayoutEncoder();
        consoleLayout.setPattern ("%date{HH:mm:ss.SSS} %msg%n");
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

        stateLogger = (Logger) LoggerFactory.getLogger(STATE_LOGGER_NAME);
        runLogger = (Logger) LoggerFactory.getLogger(RUN_LOGGER_NAME);
        runLogger.addAppender(fileAppender);
        if(!runLogger.isAttached(consoleAppender)) {
            runLogger.addAppender(consoleAppender);
        }
        runLogger.setLevel(Level.DEBUG);
        runLogger.setAdditive(false); /* set to true if root should log too */
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
    }

    public void addRunObserver(RunObserver observer){this.runObservers.add(observer);}
    public void removeRunObserver(RunObserver observer){this.runObservers.remove(observer);}
    public boolean hasRunObserver(){return !this.runObservers.isEmpty();}

    @Override
    public void preStart(){
        timestamps.put(stage.getName()+"Start",System.currentTimeMillis());
    }
    @Override
    public void postStop(){
        timestamps.put(stage.getName()+"Stop",System.currentTimeMillis());
        nextStage();
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
                    startDispatcher = queueSetupScripts();
                }

                break;
            case PreSetup:
                if(stageUpdated.compareAndSet(this,Stage.PreSetup, Stage.Setup)){
                    startDispatcher = queueSetupScripts();
                }
                break;
            case Setup:
                //if we are able to set the stage to Run
                if(stageUpdated.compareAndSet(this,Stage.Setup,Stage.Run)){
                    startDispatcher = queueRunScripts();
                }
                break;
            case Run:
                if(stageUpdated.compareAndSet(this,Stage.Run,Stage.Cleanup)){
                    runPendingDownloads();
                    startDispatcher = queueCleanupScripts();
                }
                break;
            case Cleanup:
                if(stageUpdated.compareAndSet(this,Stage.Cleanup,Stage.Done)) {
                    runPendingDownloads();//download anything queued during cleanup
                    runPendingDeletes();
                    postRun();//release any latches blocking a call to run()
                }
                break;
            default:
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
    public Logger getRunLogger(){return runLogger;}
    public Logger getStateLogger(){return stateLogger;}

    public void addPendingDelete(Host host,String path){
        pendingDeletes.put(host,path);
    }
    public void addPendingDownload(Host host,String path,String destination){
        pendingDownloads.put(host,new PendingDownload(path,destination));
    }
    public void runPendingDeletes(){
        if(!pendingDeletes.isEmpty()){
            for(Host host : pendingDeletes.keys()){
                SshSession sshSession = new SshSession(host,
                   config.getKnownHosts(),
                   config.getIdentity(),
                   config.getPassphrase(),
                   config.getTimeout(),
                   "",
                   getDispatcher().getScheduler(),
                   false
                );

                Set<String> deleteList = pendingDeletes.get(host);
                for(String delete : deleteList){
                    sshSession.execSync("rm "+delete);
                }
            }
        }
    }
    public void runPendingDownloads(){
        //TODO synchronize so only one thread tries the downloads (run ending while being aborted?)
        logger.info("{} runPendingDownloads",config.getName());
        if(!pendingDownloads.isEmpty()){
            timestamps.put("downloadStart",System.currentTimeMillis());
            for(Host host : pendingDownloads.keys()){
                Set<PendingDownload> downloadList = pendingDownloads.get(host);
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
    public void done(){
        coordinator.clearWaiters();
        dispatcher.stop(false);
    }
    public Json pendingDownloadJson(){
        Json rtrn = new Json();
        for(Host host : pendingDownloads.keys()){
            Json hostJson = new Json();
            rtrn.set(host.toString(),hostJson);
            Set<PendingDownload> pendings = pendingDownloads.get(host);
            for(PendingDownload pending : pendings){
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
    public void abort(Boolean skipCleanUp){
        if(aborted.compareAndSet(false,true)){
            coordinator.clearWaiters();
            if (!skipCleanUp) {
                stageUpdated.set(this, Stage.Run);//set the stage as run so dispatcher.stop call to DispatchObserver.postStop will set it to Cleanup
            } else {
                logger.warn("Skipping cleanup - Abort has been defined to not run any cleanup scripts");
                stageUpdated.set(this, Stage.PostCleanup);//set the stage as PostCleanup so dispatcher.stop call to DispatchObserver.postStop will set it to Done
            }
            dispatcher.stop(false);//interrupts working threads and stops dispatching next commands
            //runPendingDownloads();//added here in addition to queueCleanupScripts to download when run aborts
            //abort doesn't end the run, cleanup ends the run
            //runLatch.countDown();
        }
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
        if(Stage.Pending.equals(stage)){

            //TODO enable jitter check? what amount of jitter matters for qDup?
//            Thread jitterThread = new Thread(new JitterCheck(),"jitter-check");
//            jitterThread.setDaemon(true);
//            jitterThread.start();

            timestamps.put("start",System.currentTimeMillis());
            if(config.hasErrors()){
                config.getErrors().forEach(e->logger.error(e.toString()));
                config.getErrors().forEach(e->runLogger.error(e.toString()));
                timestamps.put("stop",System.currentTimeMillis());
                return;
            }
            boolean coordinatorInitialized = initializeCoordinator();
            if(!coordinatorInitialized){
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
            fileAppender.stop();
            consoleAppender.stop();
            writeRunJson();
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
                SshSession session = new SshSession(
                   host,
                   config.getKnownHosts(),
                   config.getIdentity(),
                   config.getPassphrase(),
                   config.getTimeout(),
                   "",getDispatcher().getScheduler(),
                   isTrace(name));
                session.setName(name);
                if ( session.isReady() ) {
                    //TODO configure session delay
                    //session.setDelay(SuffixStream.NO_DELAY);
                    ScriptContext scriptContext = new ScriptContext(
                        session,
                        config.getState().getChild(host.getHostName(), State.HOST_PREFIX),
                        this,
                        profiles.get(name),
                        setup,
                        config.getSettings().has("check-exit-code")
                    );
                    getDispatcher().addScriptContext(scriptContext);
                    return session.isOpen();
                }
                else {
                    session.close();
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
                String name = "pre-setup@"+host.getShortHostName();
                SshSession session = new SshSession(
                   host,
                   config.getKnownHosts(),
                   config.getIdentity(),
                   config.getPassphrase(),
                   config.getTimeout(),
                   "",getDispatcher().getScheduler(),
                   isTrace(name));
                session.setName(name);
                if ( session.isReady() ) {
                    //TODO configure session delay
                    //session.setDelay(SuffixStream.NO_DELAY);
                    ScriptContext scriptContext = new ScriptContext(
                        session,
                        config.getState().getChild(host.getHostName(), State.HOST_PREFIX),
                        this,
                        profiles.get(name),
                        setup,
                        config.getSettings().has("check-exit-code")
                    );
                    getDispatcher().addScriptContext(scriptContext);
                    return session.isOpen();
                }
                else {
                    session.close();
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
                       String name = roleName+"-setup@"+host.getShortHostName();
                       SshSession session = new SshSession(
                               host,
                               config.getKnownHosts(),
                               config.getIdentity(),
                               config.getPassphrase(),
                               config.getTimeout(),
                               "", getDispatcher().getScheduler(),
                                isTrace(name));
                       session.setName(name);
                       if ( session.isReady() ) {
                           //TODO configure session delay
                           //session.setDelay(SuffixStream.NO_DELAY);
                           ScriptContext scriptContext = new ScriptContext(
                                   session,
                                   config.getState().getChild(host.getHostName(), State.HOST_PREFIX),
                                   this,
                                   profiles.get(name),
                                   setup,
                                   config.getSettings().has("check-exit-code")
                           );
                           getDispatcher().addScriptContext(scriptContext);
                           return session.isOpen();
                       }
                       else {
                            session.close();
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
                            String name = script.getName() + "@" + host.getShortHostName();
                            timer.start("connect:" + host.toString());
                            SshSession session = new SshSession(
                                    host,
                                    config.getKnownHosts(),
                                    config.getIdentity(),
                                    config.getPassphrase(),
                                    config.getTimeout(),
                                    setupCommand,
                                    getDispatcher().getScheduler(),
                                    isTrace(name)

                            );
                            session.setName(name);
                            if (session.isReady()) {
                                //session.setDelay(SuffixStream.NO_DELAY);
                                timer.start("context:" + host.toString());
                                ScriptContext scriptContext = new ScriptContext(
                                        session,
                                        scriptState,
                                        this,
                                        timer,
                                        script,
                                        config.getSettings().has("check-exit-code")
                                );

                                getDispatcher().addScriptContext(scriptContext);
                                boolean rtrn = session.isOpen();
                                timer.start("waiting for start");
                                return rtrn;
                            } else {
                                session.close();
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
        return config.getTracePatterns().stream().anyMatch(pattern -> value.contains(pattern) || Pattern.matches(pattern,value));
    }

    private boolean queueCleanupScripts(){
        logger.info("{}.queueCleanupScripts",this);
        //run before cleanup
        logger.debug("{}.setup",this);

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
                        String name = roleName + "-cleanup@" + host.getShortHostName();
                        SshSession session = new SshSession(
                                host,
                                config.getKnownHosts(),
                                config.getIdentity(),
                                config.getPassphrase(),
                                config.getTimeout(),
                                setupCommand,
                                getDispatcher().getScheduler(),
                                isTrace(name));
                        session.setName(name);
                        if ( session.isReady() ) {

                            //session.setDelay(SuffixStream.NO_DELAY);
                            ScriptContext scriptContext = new ScriptContext(
                                    session,
                                    config.getState(),
                                    this,
                                    profiles.get(roleName + "-cleanup@" + host.getShortHostName()),
                                    cleanup,
                                    config.getSettings().has("check-exit-code")
                            );
                            getDispatcher().addScriptContext(scriptContext);
                            return session.isOpen();
                        }
                        else {
                            session.close();
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
        String tree = config.getState().tree();

        String filteredTree = getConfig().getState().getSecretFilter().filter(tree);

        stateLogger.debug("{} closing state:\n{}",config.getName(),filteredTree);

        runLatch.countDown();
    }
    public Dispatcher getDispatcher(){return dispatcher;}
    public Coordinator getCoordinator(){return coordinator;}
    public String getOutputPath(){ return outputPath;}

}
