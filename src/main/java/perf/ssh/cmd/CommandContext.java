package perf.ssh.cmd;

import org.slf4j.Logger;
import org.slf4j.profiler.Profiler;
import perf.ssh.Coordinator;
import perf.ssh.Run;
import perf.ssh.SshSession;
import perf.ssh.State;

/**
 * Created by wreicher
 * The context for executing the command
 * consists of the current session, the coordinator, and the state
 */
public final class CommandContext {


    private SshSession session;
    private State state;
    private Run run;
    private Profiler profiler;

    public CommandContext(SshSession session, State state,Run run,Profiler profiler){
        this.session = session;
        this.state = state;
        this.run = run;

        this.profiler = profiler;


    }
    public Logger getRunLogger(){return run.getRunLogger();}
    public Profiler getProfiler(){return profiler;}
    public String getRunOutputPath(){
        return run.getOutputPath();
    }
    public Script getScript(String name){
        return run.getRepo().getScript(name);
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
    public void delayDownload(String path,String destination){

    }
}
