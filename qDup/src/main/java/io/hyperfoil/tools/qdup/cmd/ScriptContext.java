package io.hyperfoil.tools.qdup.cmd;

import io.hyperfoil.tools.qdup.*;
import io.hyperfoil.tools.qdup.cmd.impl.ScriptCmd;
import io.hyperfoil.tools.qdup.cmd.impl.Sh;
import io.hyperfoil.tools.qdup.shell.AbstractShell;
import io.hyperfoil.tools.yaup.AsciiArt;
import io.hyperfoil.tools.yaup.PopulatePatternException;
import io.hyperfoil.tools.yaup.StringUtil;
import io.hyperfoil.tools.yaup.json.Json;
import io.hyperfoil.tools.yaup.time.SystemTimer;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Supplier;

/**
 * Created by wreicher
 * The context for executing the command and provides the run api to the commands
 */
public class ScriptContext implements Context, Runnable{

    //final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());
    final static Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass());

    private class SharedScriptContext extends ScriptContext{
        public SharedScriptContext(SystemTimer timer,Cmd root, boolean checkExitCode){
            super(ScriptContext.this.getShell(),
               ScriptContext.this.getState(),
               ScriptContext.this.getRun(),
               timer,
               root,checkExitCode);
        }
        @Override
        public void close(){
            ScriptContext.this.checkClose();
        }
    }

    /**
     * Command that calls next if the target command is the active command for the context
     */
    private class ActiveCheckCmd extends Cmd{

        private Cmd target;

        public ActiveCheckCmd(Cmd target){
            this.target = target;
        }

        @Override
        public void run(String input, Context context) {
            if(target.equals(currentCmd)){
                context.next(input);
            }else{
                context.skip(input);
            }
        }
        //NOOPs to prevent default behavior
        @Override
        public void postRun(String output,Context context){}
        @Override
        public String getLogOutput(String output, Context context) {return null;}

        @Override
        public Cmd copy() {
            return new ActiveCheckCmd(target);
        }
    }

    private static final AtomicReferenceFieldUpdater<ScriptContext,Cmd> currentCmdUpdater =
            AtomicReferenceFieldUpdater.newUpdater(ScriptContext.class,Cmd.class,"currentCmd");
    private static final String CLOSE_QUEUE = "CLOSE_LINE_QUEUE_"+System.currentTimeMillis();

    private final AbstractShell shell;
    private final Cmd rootCmd;
    private boolean checkExitCode;
    private final State state;
    private final Run run;
    private final SystemTimer timer;
    private SystemTimer cmdTimer = null;
    private ContextObserver observer = null;
    private Semaphore lineQueueSemaphore;
    private BlockingQueue<String> lineQueue;

    private AtomicInteger sessionCounter = new AtomicInteger(1);

    private List<ScheduledFuture<?>> timeouts;

    private volatile Cmd currentCmd;
    private final Map<String,Cmd> signalCmds = new HashMap<>();

    long startTime = -1;
    long updateTime = -1;

    private String roleName="";
    private String cwd="";
    private String homeDir="";
    private boolean isAborted=false;
    @Override
    public String getCwd(){return cwd;}
    @Override
    public void setCwd(String cwd){
        this.cwd = cwd;
    }

    @Override
    public void setHomeDir(String dir){
        this.homeDir = dir;
    }
    @Override
    public String getHomeDir(){return homeDir;}

    public boolean checkExitCode(){return checkExitCode;}

    public String getContextId(){
        //TODO use a StringBuilder to correctly handle missing session or root
        Cmd root = getRootCmd();
        String cmdName = "";
        if(root != null){
            if( root instanceof ScriptCmd){
                cmdName = ((ScriptCmd)root).getName();
            }else if (root instanceof Script){
                cmdName = ((Script)root).getName();
            }else{
                cmdName = root.toString();
            }
        }
        return (cmdName.isEmpty() ? "" : cmdName+":"+getRootCmd().getUid()+"@") + (getShell()!=null ? getShell().getHost().getShortHostName() : "");
    }

    public ScriptContext(AbstractShell session, State state, Run run, SystemTimer timer, Cmd rootCmd, boolean checkExitCode){
        this(session,state,run,timer,rootCmd,null,checkExitCode);
    }
    private ScriptContext(AbstractShell shell, State state, Run run, SystemTimer timer, Cmd rootCmd,Cmd setCurrentCmd, boolean checkExitCode){
        this.shell = shell;
        this.rootCmd = rootCmd;
        this.currentCmd = null;
        setCurrentCmd(null,setCurrentCmd==null?rootCmd:setCurrentCmd);
        this.state = state;
        this.run = run;
        this.timer = timer;
        this.cmdTimer = timer;
        this.checkExitCode = checkExitCode;

        if(this.shell !=null){
            this.shell.addLineObserver(
                getClass().getSimpleName(),
                (line)->{
                    this.update(line);
                }
            );
        }
        this.lineQueueSemaphore = new Semaphore(1);
        this.lineQueue = new LinkedBlockingQueue<>();
        this.timeouts = new LinkedList<>();
    }
    public String getRoleName(){return roleName;}
    public void setRoleName(String roleName){
        this.roleName = roleName;
    }

    private void clearTimers(){
        timeouts.forEach(timeout->timeout.cancel(true));
    }

    public ScriptContext newChildContext(SystemTimer timer,Cmd root){
        sessionCounter.incrementAndGet();
        return new SharedScriptContext(timer,root,false);
    }

    public void setObserver(ContextObserver observer){
        this.observer = observer;
    }
    protected long getStartTime(){return startTime;}
    protected void setStartTime(long startTime){this.startTime = startTime;}
    protected long getUpdateTime(){return updateTime;}
    protected void setUpdateTime(long updateTime){this.updateTime = updateTime;}

    protected Cmd getRootCmd(){return rootCmd;}
    public Cmd getCurrentCmd(){return currentCmd;}

    public Run getRun(){return run;}

    public Logger getRunLogger(){
        return run.getRunLogger();
    }

    public SystemTimer getContextTimer(){return timer;}
    public SystemTimer getCommandTimer(){return cmdTimer;}
    public String getRunOutputPath(){
        return run.getOutputPath();
    }
    public Script getScript(String name,Cmd command){
        return run.getConfig().getScript(name,command,this.getState());
    }
    public AbstractShell getShell(){
        return shell;
    }

    @Override
    public Globals getGlobals(){return run.getConfig().getGlobals();}

    @Override
    public Json getTimestamps(){
        return Json.fromMap(run.getTimestamps());
    }

    @Override
    public Host getHost() {
        return shell.getHost();
    }

    public Coordinator getCoordinator(){return run.getCoordinator();}

    @Override
    public void close() {
        checkClose();
    }
    @Override
    public boolean isAborted(){
        return isAborted;
    }

    private void checkClose(){
        int currentCount = this.sessionCounter.decrementAndGet();
        if(currentCount==0) {
            shell.close(false);
        }
    }

    public State getState(){return state;}
    @Override
    public void addPendingDownload(String path,String destination, Long maxSize){
        run.addPendingDownload(shell.getHost(),path,destination, maxSize);
    }
    @Override
    public void addPendingDelete(String path){
        run.addPendingDelete(shell.getHost(),path);
    }
    public void abort(Boolean skipCleanup){
        isAborted=true;
        close();
        run.abort(skipCleanup);
    }
    public void done(){
        run.done();
    }
    public Local getLocal(){return run.getLocal();}

    @Override
    public void schedule(Runnable runnable, long delayMs) {
        run.getDispatcher().getScheduler().schedule(runnable,delayMs,TimeUnit.MILLISECONDS);
    }

    protected ContextObserver getObserver(){return observer;}

    private void observerPreStart(Cmd command){
        if(observer!=null){
            observer.preStart(this,command);
        }
    }
    private void observerPreNext(Cmd command,String output){
        if(observer!=null){
            observer.preStop(this,command,output);
            observer.preNext(this,command,output);
        }
    }
    private void observerPreSkip(Cmd command,String output){
        if(observer!=null){
            observer.preStop(this,command,output);
            observer.preSkip(this,command,output);
        }
    }
    private void observerUpdate(Cmd command,String output){
        if(observer!=null){
            observer.onUpdate(this,command,output);
        }
    }
    private void observerDone(){
        if(observer!=null){
            observer.onDone(this);
        }
    }
    @Override
    public void terminal(String output){
        String filteredMessage = state.getSecretFilter().filter(output);
        run.getRunLogger().info(filteredMessage);
    }
    @Override
    public boolean isColorTerminal(){
        return run.getConfig().isColorTerminal();
    }

    public void log(String message){
        String rootString;
        if(rootCmd instanceof Script){
            rootString = ((Script)rootCmd).getName();
        }else if (rootCmd instanceof ScriptCmd){
            rootString = ((ScriptCmd)rootCmd).getName();
        }else{
            rootString = rootCmd.toString();
        }
        String filteredMessage = state.getSecretFilter().filter(message);
        MDC.put("host",getHost().getShortHostName());
        MDC.put("script",rootString);
        MDC.put("scriptId",rootCmd.getUid());
        MDC.put("role",getRoleName());
        //getRunLogger().infof("%s:%s@%s: %s",rootString,rootCmd.getUid(),getHost().getShortHostName(),filteredMessage);
        getRunLogger().info(filteredMessage);
    }
    public void error(String message){
        String rootString;
        if(rootCmd instanceof Script){
            rootString = ((Script)rootCmd).getName();
        }else if (rootCmd instanceof ScriptCmd){
            rootString = ((ScriptCmd)rootCmd).getName();
        }else{
            rootString = rootCmd.toString();
        }
        String filteredMessage = state.getSecretFilter().filter(message);

        MDC.put("host",getHost().getShortHostName());
        MDC.put("script",rootString);
        MDC.put("scriptId",rootCmd.getUid());
        MDC.put("role",getRoleName());

        if(isColorTerminal()){
            //getRunLogger().errorf(AsciiArt.ANSI_RED+"%s:%s@%s: %s"+AsciiArt.ANSI_RESET,rootString,rootCmd.getUid(),getHost().getShortHostName(),filteredMessage);
            getRunLogger().errorf(AsciiArt.ANSI_RED+filteredMessage+AsciiArt.ANSI_RESET);
        }else{
            //getRunLogger().errorf("%s:%s@%s: %s",rootString,rootCmd.getUid(),getHost().getShortHostName(),filteredMessage);
            getRunLogger().error(filteredMessage);
        }
    }

    public void closeLineQueue(){
        //only close if something is listening
        //may need to send close before listener has the semaphore (e.g. tests)
        lineQueue.add(CLOSE_QUEUE);
    }

    @Override
    public void next(String output) {
        getContextTimer().start("next");
        clearTimers();
        Cmd cmd = getCurrentCmd();
        if(!signalCmds.isEmpty()){
            signalCmds.forEach((name,onsignal)->{
                getCoordinator().removeWaiter(name,onsignal);
            });
            signalCmds.clear();
        }
        observerPreNext(cmd,output);
        if(cmd!=null) {
            if(cmd.hasWatchers()){
                closeLineQueue();
            }
            cmd.setOutput(output);
            cmd.postRun(output,this);
            Cmd toCall = cmd.getNext();
            boolean changed = setCurrentCmd(cmd,toCall);
            if(changed) {
                startCurrentCmd();
            }else{
                //TODO how to handle failing to change?
            }
        }
    }

    @Override
    public void skip(String output) {
        getContextTimer().start("skip");
        clearTimers();
        Cmd cmd = getCurrentCmd();

        if(!signalCmds.isEmpty()){
            signalCmds.forEach((name,onsignal)->{
                getCoordinator().removeWaiter(name,onsignal);
            });
            signalCmds.clear();
        }
        observerPreSkip(cmd,output);
        if(cmd!=null) {
            if(cmd.hasWatchers()){
                closeLineQueue();
            }
            cmd.setOutput(output);
            cmd.postRun(output,this);
            boolean changed = setCurrentCmd(cmd,cmd.getSkip());

            if(changed) {
                startCurrentCmd();
            }else{

            }
        }else{

        }
    }
    protected void startCurrentCmd(){
        Run run = getRun();
        if(run!=null && !isAborted()) {
            getContextTimer().start("waiting in run queue");
            run.getDispatcher().submit(this);
        }
    }

    @Override
    public void update(String output) {
        long timestamp = System.currentTimeMillis();
        setUpdateTime(timestamp);
        //updateTime = timestamp;
        Cmd cmd = getCurrentCmd();
        if(cmd!=null){
            observerUpdate(cmd,output);
            lineQueue.add(output);
        }
    }
    public boolean setCurrentCmd(Cmd current,Cmd next){
        currentCmd = next;
        boolean changed = true;//currentCmdUpdater.compareAndSet(this,current,next);

        if(logger.isTraceEnabled()) {
            StringBuffer sb = new StringBuffer();
            sb.append("setCurrent\n  current:" + current + "\n  next:" + next + "\n  changed:" + changed);
            Arrays.asList(Thread.currentThread().getStackTrace()).forEach(ste -> {
                sb.append("\n    " + ste.toString());
            });
            logger.trace(sb.toString());
        }

        if(changed){

        }else{

        }
        return changed;
    }

    private void addTimer(Cmd toWatch,Cmd toRun,long timeout){
        ScheduledFuture future = run.getDispatcher().getScheduler().schedule(()->{
            if(toWatch.equals(getCurrentCmd())){
                toRun.doRun(""+timeout,new SyncContext(
                    this.getShell(),
                    this.getState(),
                    this.getRun(),
                    this.getContextTimer(),
                    toRun,
                   this
                ));
            }
        },timeout,TimeUnit.MILLISECONDS);
        timeouts.add(future);
    }

    @Override
    public void run() {
        Cmd cmd = getCurrentCmd();
        Cmd previous = cmd !=null ? cmd.getPrevious() : null;
        String input = previous != null ? previous.getOutput() : "";
        run(cmd,input);
    }
    public void run(Cmd cmd,String input){
        if (cmd == null) {
            observerDone();//this context is finished
        } else {
            observerPreStart(cmd);
            cmdTimer = getContextTimer().start(Cmd.populateStateVariables(cmd.toString(),cmd,this));
            if (!lineQueue.isEmpty()) {//clear any unhandled output lines
                //TODO log that we are clearing orphaned lines
                //need to make sure we don't clear if another thread needs to pickup up the CLOSE_QUEUE
                try{
                    lineQueueSemaphore.acquire();
                    lineQueue.clear();
                } catch (InterruptedException e) {
                    System.out.printf("Interrupted cmd=%s%n",cmd.toString());
                    e.printStackTrace();
                } finally {
                    lineQueueSemaphore.release();
                }

            }
            long timestamp = System.currentTimeMillis();
            setStartTime(timestamp);
            setUpdateTime(timestamp);
            if (cmd.hasSignalWatchers()){
                Supplier<String> inputSupplier = ()-> getShell().peekOutput();
                for(String name : cmd.getSignalNames()){
                    String populatedName = null;
                    try {
                        populatedName = StringUtil.populatePattern(name,new PatternValuesMap(cmd,this,null));
                    } catch (PopulatePatternException e) {
                        logger.warn(e.getMessage());
                        populatedName = "";
                    }
                    List<Cmd> toCall = cmd.getSignal(name);
                    Cmd root = new ActiveCheckCmd(getCurrentCmd());
                    SyncContext syncContext = new SyncContext(this.getShell(),this.getState(),this.getRun(),this.getContextTimer(),root,this);
                    toCall.forEach(root::then);
                    signalCmds.put(name,root);
                    getCoordinator().waitFor(populatedName,root,syncContext,inputSupplier);
                }
            }
            if (cmd.hasTimers()) {
                for (Long timeout : cmd.getTimeouts()) {
                    List<Cmd> toCall = cmd.getTimers(timeout);
                    Cmd noOp = Cmd.NO_OP(""+timeout);
                    noOp.setStateParent(cmd);
                    toCall.forEach(noOp::then);
                    addTimer(
                        cmd,
                        noOp,
                        timeout
                    );
                }
            }
            if (cmd.hasWatchers()) {
                String line = "";
                try {
                    getContextTimer().start("watch.acquire:"+cmd.toString());
                    lineQueueSemaphore.acquire();
                    getContextTimer().start("watch.start:"+cmd.toString());
                    assert lineQueueSemaphore.availablePermits() == 0;

                    cmd.doRun(input, this);
                    while (!CLOSE_QUEUE.equals(line = lineQueue.take())) {
                        logger.tracef("watch.line: %s",line);
                        for (Cmd watcher : cmd.getWatchers()) {
                            SyncContext watcherContext = new SyncContext(
                               this.getShell(),
                               this.getState(),
                               this.getRun(),
                               this.getCommandTimer(),
                               watcher,
                               this
                            );
                            try {

                                logger.tracef("watcher.run %s",watcher);
                                watcherContext.forceCurrentCmd(watcher);
                                watcher.doRun(line, watcherContext);
                            } catch (Exception e) {
                                logger.warn("Exception from watcher " + watcher + "\n  currentCmd=" + watcherContext.getCurrentCmd(), e);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    getContextTimer().start("watch.release:"+cmd.toString());
                    lineQueueSemaphore.release();
                    assert lineQueueSemaphore.availablePermits() == 1;
                }
            } else {
                cmd.doRun(input, this);
            }
        }
    }
}
