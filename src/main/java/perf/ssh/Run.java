package perf.ssh;

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
import perf.ssh.cmd.*;
import perf.util.Counters;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

/**
 * Created by wreicher
 *
 */
public class Run implements Runnable {

    final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

    class HostScripts {
        LinkedHashSet<Script> setup;
        HashSet<Script> run;
        LinkedHashSet<Script> cleanup;
        public HostScripts(){
            setup = new LinkedHashSet<>();
            run = new HashSet<>();
        }
        public void addRunScript(Script script){
            run.add(script);
        }
        public void removeRunScript(Script script){
            run.remove(script);
        }
        public void addSetupScript(Script script){
            setup.add(script);
        }
        public void removeSetupScript(Script script){
            setup.remove(script);
        }
        public void addCleanupScript(Script script){cleanup.add(script);}
        public void removeCleanupScript(Script script){cleanup.remove(script);}
        public List<Script> setupScripts(){return Collections.unmodifiableList(Arrays.asList(setup.toArray(new Script[0])));}
        public List<Script> runScripts(){return Collections.unmodifiableList(Arrays.asList(run.toArray(new Script[0])));}
        public List<Script> cleanupScripts(){return Collections.unmodifiableList(Arrays.asList(cleanup.toArray(new Script[0])));}
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

    private String name;
    private String outputPath;
    private ScriptRepo repo;
    private State state;
    private HashMap<String,Role> roles;
    private HashMap<Host,HostScripts> hostScripts;
    private Coordinator coordinator;
    private CommandDispatcher dispatcher;
    private Map<Host,State> hostStates;
    private Profiles profiles;

    private Map<Host,List<PendingDownload>> pendingDownloads;

    private CountDownLatch runLatch = new CountDownLatch(1);
    private Logger runLogger;

    public Run(String name,String outputPath,CommandDispatcher dispatcher){
        this.name = name;
        this.outputPath = outputPath;
        this.dispatcher = dispatcher;
        this.roles = new HashMap<>();
        this.hostScripts = new HashMap<>();
        this.state = new State();
        this.repo = new ScriptRepo();
        this.profiles = new Profiles();
        this.coordinator = new Coordinator();

        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        PatternLayoutEncoder consoleLayout = new PatternLayoutEncoder();
        consoleLayout.setPattern("%red(%date) %highlight(%msg) %n");
        consoleLayout.setContext(lc);
        consoleLayout.start();
        ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
        consoleAppender.setEncoder(consoleLayout);
        consoleAppender.setContext(lc);
        consoleAppender.start();

        PatternLayoutEncoder fileLayout = new PatternLayoutEncoder();
        fileLayout.setPattern("%date %msg%n");
        fileLayout.setContext(lc);
        fileLayout.start();
        fileLayout.setOutputPatternAsHeader(true);
        FileAppender<ILoggingEvent> fileAppender = new FileAppender<ILoggingEvent>();

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
            runLogger.info("{} reached {}",getName(),signal_name);
        });

        this.hostStates = new HashMap<>();

        this.pendingDownloads = new HashMap<>();
    }
    public Logger getRunLogger(){return runLogger;}
    private List<PendingDownload> ensurePendingDownload(Host host){
        //TODO not thread safe
        if(!pendingDownloads.containsKey(host)){
            pendingDownloads.put(host,new LinkedList<>());
        }
        return pendingDownloads.get(host);
    }
    public void addPendingDownload(Host host,String path,String destination){
        List<PendingDownload> list =ensurePendingDownload(host);
        list.add(new PendingDownload(path,destination));
    }
    public void runPendingDownloads(){
        logger.info("{} runPendingDownloads",this.getName());
        if(!pendingDownloads.isEmpty()){
            for(Host host : pendingDownloads.keySet()){
                System.out.println("  Host:"+host.toString());
                List<PendingDownload> downloadList = pendingDownloads.get(host);
                for(PendingDownload pendingDownload : downloadList){
                    Local.get().download(pendingDownload.getPath(),pendingDownload.getDestination(),host);
                }
            }
            pendingDownloads.clear();
        }
    }
    private HostScripts ensureHostScripts(Host host){
        //TODO not thread safe
        if(!hostScripts.containsKey(host)){
            hostScripts.put(host,new HostScripts());
        }
        return hostScripts.get(host);
    }
    public void abort(){
        //TODO how to interrupt watchers
        logger.trace("abort");
        dispatcher.stop();//interupts working threads and stops dispatching next commands
        runLatch.countDown();

    }

    protected void addRunScript(Host host,Script script){
        logger.trace("{} addRunScript {}@{}",this,script.getName(),host.getHostName());
        ensureHostScripts(host).addRunScript(script);
    }
    protected void removeRunScript(Host host,Script script){
        ensureHostScripts(host).removeSetupScript(script);
    }
    protected void addSetupScript(Host host,Script script){
        logger.trace("{} addSetupScript {}@{}",this,script.getName(),host.getHostName());
        ensureHostScripts(host).addSetupScript(script);
    }
    protected void removeSetupScript(Host host,Script script){
        ensureHostScripts(host).removeSetupScript(script);
    }

    public boolean preRun(){
        boolean rtrn = true;
        Counters<String> signalCounters = new Counters<>();
        HashSet<String> waiters = new HashSet<>();
        HashSet<String> signals = new HashSet<>();
        for(Host host : allHosts().toList()){
            for( Script script : hostScripts.get(host).runScripts() ){
                CommandSummary summary = CommandSummary.apply(script,this.getRepo());

                if(!summary.getWarnings().isEmpty()){
                    rtrn = false;
                    for(String warning : summary.getWarnings()){
                        logger.error("{} {}",script.getName(),warning);
                    }
                }
                for(String signalName : summary.getSignals()){
                    logger.trace("{} {}@{} signals {}",this,script.getName(),host.getHostName(),signalName);
                    signalCounters.add(signalName);
                }
                waiters.addAll(summary.getWaits());
                signals.addAll(summary.getSignals());
            }

        }
        signalCounters.entries().forEach((signalName)->{
            int count = signalCounters.count(signalName);
            logger.trace("{} coordinator {}={}",this,signalName,count);
            this.getCoordinator().initialize(signalName,count);
        });
        List<String> noSignal = waiters.stream().filter((waitName)->!signals.contains(waitName)).collect(Collectors.toList());
        List<String> noWaiters = signals.stream().filter((signalName)->!waiters.contains(signalName)).collect(Collectors.toList());
        if(!noSignal.isEmpty()){
            logger.error("{} missing signals for {}",this,noSignal);
            rtrn = false;
        }
        if(!noWaiters.isEmpty()){
            logger.trace("{} nothing waits for {}",this,noWaiters);
        }
        return rtrn;
    }
    public void setup(){

        logger.debug("{}.setup",this);
        CommandDispatcher.Observer setupObserver = new CommandDispatcher.Observer() {
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
                queueRunScripts();
            }
        };
        dispatcher.addObserver(setupObserver);



        for(Host host : hostStates.keySet()){
            if(!hostScripts.get(host).setupScripts().isEmpty()){
                State hostState = hostStates.get(host);
                Script hostSetup = new Script(host.getHostName()+"-setup");
                for(Script script : hostScripts.get(host).setupScripts()){
                    hostSetup.then(script.deepCopy());
                }
                long start = System.currentTimeMillis();
                Profiler profiler = profiles.get(hostSetup.getName()+"@"+host.getHostName());
                logger.info("{} connecting {} to {}@{}",this,hostSetup.getName(),host.getUserName(),host.getHostName());
                profiler.start("connect:"+host.toString());
                SshSession scriptSession = new SshSession(host);
                profiler.start("waiting for start");
                if(!scriptSession.isOpen()){
                    logger.error("{} failed to connect {} to {}@{}. Aborting",this.getName(),hostSetup.getName(),host.getUserName(),host.getHostName());
                    abort();
                    return;
                }
                long stop = System.currentTimeMillis();

                CommandContext commandContext = new CommandContext(scriptSession,hostState.newScriptState(),this,profiler);
                logger.debug("{} setup addScript {}\n{}",this,hostSetup,hostSetup.tree());
                dispatcher.addScript(hostSetup,commandContext);
            }
        }
        dispatcher.start();
    }

    @Override
    public String toString(){return name;}

    @Override
    public void run() {


        runLogger.info("{} starting run state:\n{}",this.getName(),state.getRunState());
        for(Map.Entry<Host,State> entries : hostStates.entrySet()){
            runLogger.info("{} host state:\n{}",entries.getKey(),entries.getValue().getHostState());
        }

        boolean validated = preRun();
        if(!validated){
            //TODO raise warnings if not validated
            return;
        }
        setup();

        try {
            runLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        runPendingDownloads();

        //dispatcher.closeSessions(); //was racing the close sessions from checkActiveCount
    }
    private void queueRunScripts(){
        logger.debug("{}.queueRunScripts",this);
        CommandDispatcher.Observer runObserver = new CommandDispatcher.Observer() {
            @Override
            public void onStart(Cmd command) {}

            @Override
            public void onStop(Cmd command) {}

            @Override
            public void onStart() {}

            @Override
            public void onStop() {
                dispatcher.removeObserver(this);
                postRun();
            }
        };
        //TODO parallel connect with ExecutorService.invokeAll(Callable / Runnable)
        for(Host host : hostStates.keySet()){
            State hostState = hostStates.get(host);
            for(Script script : hostScripts.get(host).runScripts()){
                State scriptState = hostState.newScriptState();
                long start = System.currentTimeMillis();
                Profiler profiler = profiles.get(script.getName()+"@"+host.getHostName());
                logger.info("{} connecting {} to {}@{}",this,script.getName(),host.getUserName(),host.getHostName());
                profiler.start("connect:"+host.toString());
                SshSession scriptSession = new SshSession(host); //this can take some time, hopefully it isn't a problem
                profiler.start("waiting for start");
                if(!scriptSession.isOpen()){
                    logger.error("{} failed to connect {} to {}@{}. Aborting",this.getName(),script.getName(),host.getUserName(),host.getHostName());
                    abort();
                    return;
                }

                long stop = System.currentTimeMillis();

                CommandContext commandContext = new CommandContext(scriptSession,scriptState,this,profiler);
                logger.debug("{} connected {}@{} in {}s",this,script.getName(),host.getHostName(),((stop-start)/1000));
                dispatcher.addScript(script,commandContext);
            }
        }
        dispatcher.addObserver(runObserver);
        dispatcher.start();
    }
    public void postRun(){
        logger.debug("{}.postRun",this);
        runLogger.info("{} closing state:\n{}",this.getName(),state.getRunState());
        for(Map.Entry<Host,State> entries : hostStates.entrySet()){
            runLogger.info("host {}:\n{}",entries.getKey(),entries.getValue().getHostState());
        }

        //TODO run cleanups before coordinator.signal
        runLatch.countDown();


    }
    public CommandDispatcher getDispatcher(){return dispatcher;}
    public Coordinator getCoordinator(){return coordinator;}
    public State getState(){return state;}
    public void addRole(Role role){
        roles.put(role.getName(),role);
    }
    public List<String> getRoleNames(){return Arrays.asList(roles.keySet().toArray(new String[0]));}
    public Role getRole(String name){
        if(!roles.containsKey(name)){
            roles.put(name,new Role(name,this));
        }
        return roles.get(name);
    }

    public List<Script> getRunScripts(Host host){
        return Collections.unmodifiableList(ensureHostScripts(host).runScripts());
    }
    public List<Script> getSetupScripts(Host host){
        return Collections.unmodifiableList(ensureHostScripts(host).setupScripts());
    }
    public ScriptRepo getRepo(){return repo;}
    public String getOutputPath(){ return outputPath;}
    public String getName(){return name;}
    public HostList allHosts(){
        return new HostList(Arrays.asList(hostStates.keySet().toArray(new Host[0])),this);
    }
    protected void addHost(Host host){
        if(!hostStates.containsKey(host)){
            State previous = hostStates.put(host,getState().newHostState());
            if(previous!=null){
                //TODO more than one thread tried to add a host to a run, that is not supported
            }
        }

    }
    protected void addAllHosts(List<Host> hosts){
        for(Host host : hosts){
            addHost(host);
        }
    }

}
