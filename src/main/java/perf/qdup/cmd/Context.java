package perf.qdup.cmd;

import org.slf4j.Logger;
import org.slf4j.profiler.Profiler;
import perf.qdup.Coordinator;
import perf.qdup.Local;
import perf.qdup.SshSession;
import perf.qdup.State;

import java.util.concurrent.TimeUnit;

public interface Context {

    void next(String output);
    void skip(String output);
    void update(String output);

    Logger getRunLogger();
    Profiler getProfiler();
    String getRunOutputPath();
    Script getScript(String name,Cmd command);
    SshSession getSession();
    State getState();
    void addPendingDownload(String path,String destination);
    void abort();
    void done();
    Local getLocal();
    void schedule(Runnable runnable, long delayMs);
    Coordinator getCoordinator();


}
