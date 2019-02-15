package perf.qdup.cmd;

import org.slf4j.Logger;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.slf4j.profiler.Profiler;
import perf.qdup.*;
import perf.qdup.cmd.impl.ScriptCmd;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Created by wreicher
 * The context for executing the command and provides the run api to the commands
 */
public class ScriptContext implements Context, Runnable{

    final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

    private static final AtomicReferenceFieldUpdater<ScriptContext,Cmd> currentCmdUpdater =
            AtomicReferenceFieldUpdater.newUpdater(ScriptContext.class,Cmd.class,"currentCmd");
    private static final String CLOSE_QUEUE = "CLOSE_LINE_QUEUE_"+System.currentTimeMillis();

    private final SshSession session;
    private final Cmd rootCmd;

    private final State state;
    private final Run run;
    private final Profiler profiler;
    private ContextObserver observer = null;
    private BlockingQueue<String> lineQueue;

    private List<ScheduledFuture<?>> timeouts;

    private volatile Cmd currentCmd;

    long startTime = -1;
    long updateTime = -1;

    public ScriptContext(SshSession session, State state, Run run, Profiler profiler, Cmd rootCmd){
        this(session,state,run,profiler,rootCmd.deepCopy(),null);
    }
    private ScriptContext(SshSession session, State state, Run run, Profiler profiler, Cmd rootCmd,Cmd setCurrentCmd){
        this.session = session;
        this.rootCmd = rootCmd;
        this.currentCmd = null;
        setCurrentCmd(null,setCurrentCmd==null?rootCmd:setCurrentCmd);
        this.state = state;
        this.run = run;
        this.profiler = profiler;

        if(this.session!=null){
            session.addLineObserver(
                getClass().getSimpleName(),
                (line)->{
                    this.update(line);
                }
            );
            //TODO does this belong here or in Sh?
//            session.addShObserver(getClass().getSimpleName(),(output)->{
//                this.next(output);
//            });
        }
        this.lineQueue = new LinkedBlockingQueue<>();
        this.timeouts = new LinkedList<>();

    }

    public void setObserver(ContextObserver observer){
        this.observer = observer;
    }

    protected long getStartTime(){return startTime;}
    protected void setStartTime(long startTime){this.startTime = startTime;}
    protected long getUpdateTime(){return updateTime;}
    protected void setUpdateTime(long updateTime){this.updateTime = updateTime;}

    protected Cmd getRootCmd(){return rootCmd;}
    protected Cmd getCurrentCmd(){return currentCmd;}
    protected Run getRun(){return run;}

    public Logger getRunLogger(){return run.getRunLogger();}
    public Profiler getProfiler(){return profiler;}
    public String getRunOutputPath(){
        return run.getOutputPath();
    }
    public Script getScript(String name,Cmd command){
        return run.getConfig().getScript(name,command,this.getState());
    }
    public SshSession getSession(){
        return session;
    }

    @Override
    public Host getHost() {
        return session.getHost();
    }

    public Coordinator getCoordinator(){return run.getCoordinator();}
    public State getState(){return state;}
    public void addPendingDownload(String path,String destination){
        run.addPendingDownload(session.getHost(),path,destination);
    }
    public void abort(Boolean skipCleanup){
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

    private void observerPreStart(Cmd command){
        if(observer!=null){
            observer.preStart(this,command);
        }
    }
    private void observerPreNext(Cmd command,String output){
        if(observer!=null){
            //observer.preStop(this,command,output);
            observer.preNext(this,command,output);
        }
    }
    private void observerPreSkip(Cmd command,String output){
        if(observer!=null){
            //observer.preStop(this,command,output);
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
        run.getRunLogger().info(output);
    }
    @Override
    public boolean isColorTerminal(){
        return run.getConfig().isColorTerminal();
    }

    private void log(Cmd command,String output){
        String cmdLogOuptut = command == null ? output : command.getLogOutput(output,this);
        String populatedCommand = Cmd.populateStateVariables(cmdLogOuptut, command, state);
        String rootString;
        if(rootCmd instanceof Script){
            rootString = ((Script)rootCmd).getName();
        }else if (rootCmd instanceof ScriptCmd){
            rootString = ((ScriptCmd)rootCmd).getName();
        }else{
            rootString = rootCmd.toString();
        }
        getRunLogger().info("{}@{}:{}",
            rootString,
            getHost().getShortHostName(),
            populatedCommand
        );
    }

    public void closeLineQueue(){
        lineQueue.add(CLOSE_QUEUE);
    }

    @Override
    public void next(String output) {
        getProfiler().start("next");
        Cmd cmd = getCurrentCmd();
        log(cmd,output);
        observerPreNext(cmd,output);
        if(cmd!=null) {
            if(cmd.hasWatchers()){
                closeLineQueue();
            }
            cmd.setOutput(output);
            boolean changed = setCurrentCmd(cmd,cmd.getNext());
            if(changed) {
                startCurrentCmd();
            }else{}
        }
    }

    @Override
    public void skip(String output) {
        getProfiler().start("skip");
        Cmd cmd = getCurrentCmd();
        log(cmd,output);
        observerPreSkip(cmd,output);
        if(cmd!=null) {
            if(cmd.hasWatchers()){
                lineQueue.add(CLOSE_QUEUE);
            }
            cmd.setOutput(output);
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
        if(run!=null) {
            getProfiler().start("waiting in run queue");
            run.getDispatcher().submit(this);
        }
    }

    @Override
    public void update(String output) {
        long timestamp = System.currentTimeMillis();
        updateTime = timestamp;
        Cmd cmd = getCurrentCmd();
        if(cmd!=null){
            observerUpdate(cmd,output);
            lineQueue.add(output);
        }
    }
    protected boolean setCurrentCmd(Cmd current,Cmd next){
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
        run.getDispatcher().getScheduler().schedule(()->{
            if(toWatch.equals(getCurrentCmd())){
                toRun.doRun(""+timeout,new SyncContext(
                    this.getSession(),
                    this.getState(),
                    this.getRun(),
                    this.getProfiler(),
                    toRun
                ));
            }
        },timeout,TimeUnit.MILLISECONDS);
    }

    @Override
    public void run() {
            Cmd cmd = getCurrentCmd();
            if (cmd == null) {
                observerDone();//this context is finished
            } else {
                observerPreStart(cmd);
                getProfiler().start(cmd.toString());
                if (!lineQueue.isEmpty()) {//clear any unhandled output lines
                    //TODO log that we are clearing orphaned lines
                    lineQueue.clear();
                }
                long timestamp = System.currentTimeMillis();
                setStartTime(timestamp);
                setUpdateTime(timestamp);
                String input = cmd != null && cmd.getPrevious() != null ? cmd.getPrevious().getOutput() : "";
                if (cmd.hasTimers()) {
                    for (Long timeout : cmd.getTimeouts()) {
                        List<Cmd> toCall = cmd.getTimers(timeout);
                        Cmd noOp = Cmd.NO_OP();
                        toCall.forEach(noOp::then);
                        addTimer(
                            cmd,
                            noOp,
                            timeout
                        );
                    }
                }
                long startDoRun = System.currentTimeMillis();
                cmd.doRun(input, this);

                long stopDoRun = System.currentTimeMillis();
                if (cmd.hasWatchers()) {
                    getProfiler().start("watch:"+cmd.toString());
                    String line = "";
                    try {
                        SyncContext watcherContext = new SyncContext(
                            this.getSession(),
                            this.getState(),
                            this.getRun(),
                            this.getProfiler(),
                            cmd
                        );
                        while (!CLOSE_QUEUE.equals(line = lineQueue.take())) {
                            for (Cmd watcher : cmd.getWatchers()) {
                                try {
                                    watcherContext.forceCurrentCmd(watcher);
                                    watcher.doRun(line, watcherContext);
                                } catch (Exception e) {
                                    logger.warn("Exception from watcher " + watcher + "\n  curentCmd=" + watcherContext.getCurrentCmd(), e);
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } finally {

                    }
                }
            }
    }
}
