package io.hyperfoil.tools.qdup.cmd;

import io.hyperfoil.tools.qdup.Coordinator;
import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.Local;
import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.SshSession;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.impl.CtrlSignal;
import io.hyperfoil.tools.yaup.time.SystemTimer;
import org.slf4j.Logger;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.slf4j.profiler.Profiler;

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
    private final SystemTimer timer;

    private volatile Cmd currentCmd;

    private final ScriptContext scriptContext;
    private final Cmd scriptActiveCmd;

    public SyncContext(SshSession session, State state, Run run, SystemTimer timer, Cmd currentCmd, ScriptContext scriptContext){
        this.session = session;
        this.state = state;
        this.run = run;
        this.timer = timer;
        this.currentCmd = currentCmd;
        this.scriptContext = scriptContext;
        this.scriptActiveCmd = scriptContext.getCurrentCmd();
    }

    @Override
    public Cmd getCurrentCmd(){return currentCmd;}

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
            cmd.postRun(output,this);
            if(next!=null) {
                while(
                   next!=null &&
                   (next instanceof CtrlSignal) &&
                   scriptContext!=null &&
                   scriptActiveCmd != null &&
                   !scriptActiveCmd.equals(scriptContext.getCurrentCmd())
                ){
                    logger.trace("not running {} because completed active command {}",next,scriptActiveCmd);
                    next = next.getSkip();
                }
                if(next!=null) {
                    setCurrentCmd(cmd, next);
                    logger.trace("synchronously running {}", next);
                    if (scriptContext != null && scriptContext.getObserver() != null) {
                        scriptContext.getObserver().preStart(this, next);
                    }
                    next.doRun(output, this);
                }
            }
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


    @Override
    public void skip(String output) {
        Cmd cmd = getCurrentCmd();
        if(cmd!=null) {
            Cmd next = cmd.getSkip();
            cmd.setOutput(output);
            cmd.postRun(output,this);
            if(next!=null) {
                while(next!=null && (next instanceof CtrlSignal) && scriptContext!=null && scriptActiveCmd != null && !scriptActiveCmd.equals(scriptContext.getCurrentCmd())){
                    logger.info("not running {} because completed active command {}",next,scriptActiveCmd);
                    next = next.getSkip();
                }
                if(next!=null) {
                    setCurrentCmd(cmd, next);
                    logger.trace("synchronously running {}", next);
                    if (scriptContext != null && scriptContext.getObserver() != null) {
                        scriptContext.getObserver().preStart(this, next);
                    }
                    next.doRun(output, this);
                }
            }
        }
    }

    @Override
    public void update(String output) {
        if( scriptContext != null && getCurrentCmd()!=null ){
            scriptContext.getObserver().onUpdate(this,getCurrentCmd(),output);
        }
        //not supported by SyncContext
    }

    @Override
    public void log(String message) {
        String filteredMessage = state.getSecretFilter().filter(message);
        getRunLogger().info(filteredMessage);
    }

    @Override
    public void error(String message) {
        String filteredMessage = state.getSecretFilter().filter(message);
        getRunLogger().error(filteredMessage);
    }


    private Logger getRunLogger() {
        return run.getRunLogger();
    }

    @Override
    public SystemTimer getTimer() {
        return timer;
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
    public void addPendingDownload(String path, String destination, Long maxSize) {
        run.addPendingDownload(session.getHost(),path,destination,maxSize);
    }
    @Override
    public void addPendingDelete(String path){
        run.addPendingDelete(session.getHost(),path);
    }

    @Override
    public void abort(Boolean skipCleanup) {
        run.abort(skipCleanup);
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
    public void close() {
        //TODO should this also close the session
    }

    @Override
    public void run() {

    }
}
