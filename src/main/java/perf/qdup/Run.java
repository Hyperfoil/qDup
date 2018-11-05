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
import perf.qdup.cmd.impl.RoleEnv;
import perf.qdup.cmd.impl.ScriptCmd;
import perf.qdup.config.Role;
import perf.qdup.config.RunConfig;
import perf.qdup.config.StageSummary;
import perf.qdup.stream.SuffixStream;
import perf.yaup.HashedSets;
import perf.yaup.json.Json;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Created by wreicher
 *
 */
public class Run implements Runnable, DispatchObserver {

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
    private Dispatcher dispatcher;
    private Profiles profiles;
    private Local local;

    private HashedSets<Host,PendingDownload> pendingDownloads;

    private CountDownLatch runLatch = new CountDownLatch(1);
    private Logger runLogger;
    private ConsoleAppender<ILoggingEvent> consoleAppender;
    private FileAppender<ILoggingEvent> fileAppender;

    public Run(String outputPath,RunConfig config,Dispatcher dispatcher){
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
        this.pendingDownloads = new HashedSets<>();
    }

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
    private void nextStage(){
        switch (stage){
            case Setup:
                //if we are able to set the stage to Run
                if(stageUpdated.compareAndSet(this,Stage.Setup,Stage.Run)){
                    boolean ok = queueRunScripts();
                    if(ok) {
                        dispatcher.start();
                    }else{

                    }
                }
                break;
            case Run:
                runPendingDownloads();
                //if we are able to set the stage to Cleanup
                if(stageUpdated.compareAndSet(this,Stage.Run,Stage.Cleanup)){
                    boolean ok = queueCleanupScripts();
                    if(ok){
                        dispatcher.start();
                    }else{
                    }
                }else{
                }
                break;
            case Cleanup:
                runPendingDownloads();//download anything queued during cleanup
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
        dispatcher.stop();
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
            //abort doesn't end the run, cleanup ends the run
            //runLatch.countDown();
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
        boolean ok = queueSetupScripts();
        if(ok) {

            dispatcher.start();
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
        fileAppender.stop();
        consoleAppender.stop();
    }
    private boolean connectAll(List<Callable<Boolean>> toCall,int timeout){
        boolean ok = false;
        try {
            ok = getDispatcher().invokeAll(toCall/*,timeout, TimeUnit.SECONDS*/).stream().map((f) -> {
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
        return ok;
    }
    private boolean queueSetupScripts(){
        long start = System.currentTimeMillis();
        logger.debug("{}.setup",this);

        //Observer to set the Env.Diffs
        List<Callable<Boolean>> connectSessions = new LinkedList<>();

        config.getRoleNames().forEach(roleName->{
            final Role role = config.getRole(roleName);
            if(!role.getSetup().isEmpty()){
               final Script setup = new Script(roleName+"-setup");
               setup.then(new RoleEnv(role,true));
               role.getSetup().forEach(cmd->{
                   setup.then(cmd);
               });
               setup.then(new RoleEnv(role,false));
               role.getHosts().forEach(host->{
                   connectSessions.add(()->{
                       SshSession session = new SshSession(
                               host,
                               config.getKnownHosts(),
                               config.getIdentity(),
                               config.getPassphrase(),
                               config.getTimeout(),
                               "", getDispatcher().getScheduler());
                       //TODO configure session delay
                       session.setDelay(SuffixStream.NO_DELAY);
                       ScriptContext scriptContext = new ScriptContext(
                               session,
                               config.getState(),
                               this,
                               profiles.get(roleName+"-setup@"+host.getHostName()),
                               setup
                       );
                       getDispatcher().addScriptContext(scriptContext);                                           return session.isOpen();
                   });
               });
            }

        });
        boolean ok = true;
        if(!connectSessions.isEmpty()) {
            ok = connectAll(connectSessions, 60);
            if (!ok) {
                abort();
            }

        }else{

        }
        long stop = System.currentTimeMillis();
        System.out.println("setup="+(stop-start)+"ms");
        return ok;
    }

    private boolean queueRunScripts(){
        long start = System.currentTimeMillis();
        logger.debug("{}.queueRunScripts",this);

        List<Callable<Boolean>> connectSessions = new LinkedList<>();

        for(String roleName : config.getRoleNames()){
            Role role = config.getRole(roleName);
            if(!role.getRun().isEmpty()){
                for(ScriptCmd script : role.getRun()){
                    for(Host host : role.getHosts()){
                        State hostState = config.getState().getChild(host.getHostName(),State.HOST_PREFIX);
                        State scriptState = hostState.getChild(script.getName()).getChild("id="+script.getUid());
                        Profiler profiler = profiles.get(script.getName()+"-"+script.getUid()+"@"+host);
                        String setupCommand = role.hasEnvironment(host) ? role.getEnv(host).getDiff().getCommand() : "";
                        connectSessions.add(()->{
                            profiler.start("connect:"+host.toString());
                            SshSession session = new SshSession(
                                host,
                                config.getKnownHosts(),
                                config.getIdentity(),
                                config.getPassphrase(),
                                config.getTimeout(),
                                setupCommand,
                                getDispatcher().getScheduler());
                            profiler.start("context:"+host.toString());
                            ScriptContext scriptContext = new ScriptContext(
                                session,
                                scriptState,
                                this,
                                profiler,
                                script
                            );

                            getDispatcher().addScriptContext(scriptContext);
                            boolean rtrn = session.isOpen();
                            profiler.start("waiting for start");
                            return rtrn;
                        });
                    }
                }
            }
        }
        boolean ok = true;

        if(!connectSessions.isEmpty()) {
            ok = false; // it better be set by dispatcher then
//            long connectAllStart = System.currentTimeMillis();
//            ok = connectAll(connectSessions,60);
//            long connectAllStop = System.currentTimeMillis();
//            System.out.println("run.connect="+(connectAllStop-connectAllStart)+"ms");
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
                abort();
            }

        }else{

        }
        long stop = System.currentTimeMillis();
        System.out.println("run="+(stop-start)+"ms");
        return ok;
    }
    private boolean queueCleanupScripts(){
        long start = System.currentTimeMillis();
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
                role.getHosts().forEach(host->{
                    String setupCommand = role.hasEnvironment(host) ? role.getEnv(host).getDiff().getCommand() : "";
                    connectSessions.add(()->{
                        SshSession session = new SshSession(
                                host,
                                config.getKnownHosts(),
                                config.getIdentity(),
                                config.getPassphrase(),
                                config.getTimeout(),
                                setupCommand,
                                getDispatcher().getScheduler());
                        ScriptContext scriptContext = new ScriptContext(
                                session,
                                config.getState(),
                                this,
                                profiles.get(roleName+"-setup@"+host.getHostName()),
                                cleanup
                        );
                        getDispatcher().addScriptContext(scriptContext);
                        return session.isOpen();
                    });
                });
            }
        });
        boolean ok = true;
        if(!connectSessions.isEmpty()){
            long connectAllStart = System.currentTimeMillis();
            ok = connectAll(connectSessions,60);
            long connectAllStop = System.currentTimeMillis();
            System.out.println("cleanup.connect="+(connectAllStop-connectAllStart)+"ms");
            if(!ok){
                abort();
            }

        }else{

        }
        long stop = System.currentTimeMillis();
        System.out.println("cleanup="+(stop-start)+"ms ");
        return ok;
    }
    private void postRun(){
        logger.debug("{}.postRun",this);
        runLogger.info("{} closing state:\n{}",config.getName(),config.getState().tree());

        runLatch.countDown();
    }
    public Dispatcher getDispatcher(){return dispatcher;}
    public Coordinator getCoordinator(){return coordinator;}
    public String getOutputPath(){ return outputPath;}

}
