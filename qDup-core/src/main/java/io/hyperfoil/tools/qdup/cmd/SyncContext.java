package io.hyperfoil.tools.qdup.cmd;

import io.hyperfoil.tools.qdup.*;
import io.hyperfoil.tools.qdup.cmd.impl.CtrlSignal;
import io.hyperfoil.tools.qdup.shell.AbstractShell;
import io.hyperfoil.tools.yaup.json.Json;
import io.hyperfoil.tools.yaup.time.SystemTimer;
import org.jboss.logging.Logger;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public class SyncContext implements Context, Runnable{

    final static Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass());

    private final static AtomicReferenceFieldUpdater<SyncContext,Cmd> currentCmdUpdater =
            AtomicReferenceFieldUpdater.newUpdater(SyncContext.class,Cmd.class,"currentCmd");

    private final AbstractShell session;
    private final State state;
    private final Run run;
    private final SystemTimer timer;
    private SystemTimer cmdTimer = null;

    private volatile Cmd currentCmd;

    private final ScriptContext scriptContext;
    private final Cmd scriptActiveCmd;

    private String cwd="";
    private String homeDir="";

    public SyncContext(AbstractShell session, State state, Run run, SystemTimer timer, Cmd currentCmd, ScriptContext scriptContext){
        this.session = session;
        this.state = state;
        this.run = run;
        this.timer = timer;
        this.cmdTimer = timer!=null ? timer.start(currentCmd.toString()) : timer;
        this.currentCmd = currentCmd;
        this.scriptContext = scriptContext;
        this.scriptActiveCmd = scriptContext.getCurrentCmd();
    }

    public void setCwd(String cwd){
        this.cwd = cwd;
    }

    public String getCwd(){
        if(scriptContext!=null){
            return scriptContext.getCwd();
        } else {
            return cwd;
        }
    }

    @Override
    public Globals getGlobals(){return run.getConfig().getGlobals();}

    @Override
    public void setHomeDir(String dir) {
        this.homeDir = dir;
    }

    @Override
    public String getHomeDir() {
        return homeDir;
    }

    public Run getRun(){ return run;}

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
        if(cmdTimer!=null){
            cmdTimer.stop();
        }
        if(cmd!=null) {
            Cmd next = cmd.getNext();
            cmd.setOutput(output);
            if (scriptContext != null && scriptContext.getObserver() != null) {
                scriptContext.getObserver().preStop(this, cmd, output);
            }
            cmd.postRun(output,this);
            if(next!=null) {
                while(
                   next!=null &&
                   (next instanceof CtrlSignal) &&
                   scriptContext!=null &&
                   scriptActiveCmd != null &&
                   !scriptActiveCmd.equals(scriptContext.getCurrentCmd())
                ){
                    logger.tracef("not running %s because completed active command %s",next,scriptActiveCmd);
                    next = next.getSkip();
                }
                if(next!=null) {
                    if (scriptContext != null && scriptContext.getObserver() != null) {
                        scriptContext.getObserver().preNext(this, cmd, output);

                    }
                    setCurrentCmd(cmd, next);
                    logger.tracef("synchronously running %s", next);
                    if (scriptContext != null && scriptContext.getObserver() != null) {
                        scriptContext.getObserver().preStart(this, next);
                    }
                    cmdTimer = getContextTimer().start(Cmd.populateStateVariables(next.toString(),next,this),true);
                    next.doRun(output, this);

                }
            }
        }
    }

    @Override
    public void terminal(String output){
        String filteredMessage = state.getSecretFilter().filter(output);
        run.log(filteredMessage);
    }
    @Override
    public boolean isColorTerminal(){
        return run.getConfig().isColorTerminal();
    }

    @Override
    public void skip(String output) {
        Cmd cmd = getCurrentCmd();

        if(cmdTimer!=null){
            cmdTimer.stop();
        }
        if(cmd!=null) {
            Cmd next = cmd.getSkip();
            cmd.setOutput(output);
            if (scriptContext != null && scriptContext.getObserver() != null) {
                scriptContext.getObserver().preStop(this, cmd, output);
            }
            cmd.postRun(output,this);
            if(next!=null) {
                while(next!=null && (next instanceof CtrlSignal) && scriptContext!=null && scriptActiveCmd != null && !scriptActiveCmd.equals(scriptContext.getCurrentCmd())){
                    logger.infof("not running %s because completed active command %s",next,scriptActiveCmd);
                    next = next.getSkip();
                }
                if(next!=null) {
                    if (scriptContext != null && scriptContext.getObserver() != null) {
                        scriptContext.getObserver().preSkip(this, cmd, output);
                    }
                    setCurrentCmd(cmd, next);
                    logger.tracef("synchronously running %s", next);
                    if (scriptContext != null && scriptContext.getObserver() != null) {
                        scriptContext.getObserver().preStart(this, next);
                    }
                    cmdTimer = getContextTimer().start(Cmd.populateStateVariables(next.toString(),next,this),true);
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
        if(scriptContext !=null){
            scriptContext.log(message);
        }else{
            String filteredMessage = state.getSecretFilter().filter(message);
            run.log(filteredMessage);
        }

    }

    @Override
    public void error(String message) {
        String filteredMessage = state.getSecretFilter().filter(message);
        run.error(filteredMessage);
    }


    private Logger getRunLogger() {
        return run.getRunLogger();
    }
    @Override
    public Json getTimestamps(){
        return Json.fromMap(run.getTimestamps());
    }

    @Override
    public SystemTimer getContextTimer() {
        return timer;
    }

    @Override
    public SystemTimer getCommandTimer() {
        return null;
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
    public AbstractShell getShell() {
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
        close();
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
    public boolean isAborted(){
        //false becuase a sync context cannot yet be closed
        return scriptContext!=null?scriptContext.isAborted() : false;
    }

    @Override
    public void run() {

    }
}
