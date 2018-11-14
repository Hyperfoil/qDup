package perf.qdup.cmd;

import org.slf4j.Logger;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.slf4j.profiler.Profiler;
import perf.qdup.*;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public class SyncContext implements Context, Runnable{

    final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

    private final static AtomicReferenceFieldUpdater<SyncContext,Cmd> currentCmdUpdater =
            AtomicReferenceFieldUpdater.newUpdater(SyncContext.class,Cmd.class,"currentCmd");

    private final SshSession session;
    private final State state;
    private final Run run;
    private final Profiler profiler;

    private volatile Cmd currentCmd;

    public SyncContext(SshSession session, State state, Run run, Profiler profiler, Cmd currentCmd){
        this.session = session;
        this.state = state;
        this.run = run;
        this.profiler = profiler;
        this.currentCmd = currentCmd;
    }

    protected Cmd getCurrentCmd(){return currentCmd;}
    protected boolean forceCurrentCmd(Cmd next){
        boolean changed = currentCmdUpdater.compareAndSet(this,getCurrentCmd(),next);
        return changed;
    }
    protected boolean setCurrentCmd(Cmd current,Cmd next){
        boolean changed = currentCmdUpdater.compareAndSet(this,current,next);
        if(!changed){
            //TODO log failed attempt to change
        }
        return changed;
    }

    @Override
    public void next(String output) {
        Cmd cmd = getCurrentCmd();
        if(cmd!=null) {
            Cmd next = cmd.getNext();
            cmd.setOutput(output);
            if(next!=null) {
                setCurrentCmd(cmd, next);
                next.doRun(output,this);
            }
        }
    }

    @Override
    public void skip(String output) {
        Cmd cmd = getCurrentCmd();
        if(cmd!=null) {
            Cmd next = cmd.getSkip();
            cmd.setOutput(output);
            if(next!=null) {
                setCurrentCmd(cmd, next);
                next.doRun(output,this);
            }
        }
    }

    @Override
    public void update(String output) {
        //not supported by SyncContext
    }

    @Override
    public Logger getRunLogger() {
        return run.getRunLogger();
    }

    @Override
    public Profiler getProfiler() {
        return profiler;
    }

    @Override
    public String getRunOutputPath() {
        return run.getOutputPath();
    }

    @Override
    public Script getScript(String name, Cmd command) {
        return run.getConfig().getScript(name,command,this.getState());
    }

    @Override
    public SshSession getSession() {
        return session;
    }

    @Override
    public Host getHost() {
        return session.getHost();
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public void addPendingDownload(String path, String destination) {

    }

    @Override
    public void abort() {
        run.abort();
    }

    @Override
    public void done() {
        run.done();
    }

    @Override
    public Local getLocal() {
        return run.getLocal();
    }

    @Override
    public void schedule(Runnable runnable, long delayMs) {
        run.getDispatcher().getScheduler().schedule(runnable,delayMs,TimeUnit.MILLISECONDS);
    }

    @Override
    public Coordinator getCoordinator() {
        return run.getCoordinator();
    }

    @Override
    public void run() {
        
    }
}
