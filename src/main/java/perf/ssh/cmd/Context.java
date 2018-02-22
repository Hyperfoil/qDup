package perf.ssh.cmd;

import org.slf4j.Logger;
import org.slf4j.profiler.Profiler;
import perf.ssh.*;

import java.util.concurrent.TimeUnit;

/**
 * Created by wreicher
 * The context for executing the command
 * consists of the current session, the coordinator, and the state
 */
public final class Context {

    private SshSession session;
    private State state;
    private Run run;
    private Profiler profiler;

    public Context(SshSession session, State state, Run run, Profiler profiler){
        this.session = session;
        this.state = state;
        this.run = run;

        this.profiler = profiler;

    }
    public void schedule(Cmd command,Runnable runnable, long delay){
        run.getDispatcher().schedule(command,runnable,delay,TimeUnit.MILLISECONDS);
    }
    public void schedule(Cmd command,Runnable runnable, long delay, TimeUnit timeUnit){
        run.getDispatcher().schedule(command,runnable,delay,timeUnit);
    }
    public Logger getRunLogger(){return run.getRunLogger();}
    public Profiler getProfiler(){return profiler;}
    public String getRunOutputPath(){
        return run.getOutputPath();
    }
    public Script getScript(String name,Cmd command){
        return run.getConfig().getScript(name,command,this.getState());
    }
    public SshSession getSession(){return  session;}
    public Coordinator getCoordinator(){return run.getCoordinator();}
    public State getState(){return state;}
    public void addPendingDownload(String path,String destination){
        run.addPendingDownload(session.getHost(),path,destination);
    }
    public void notifyTailMod(Cmd oldTail,Cmd newTail){
        run.getDispatcher().onTailMod(oldTail,newTail);
    }
    public void abort(){
        run.abort();
    }
    public Local getLocal(){return run.getLocal();}
}
