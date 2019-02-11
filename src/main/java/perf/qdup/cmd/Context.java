package perf.qdup.cmd;

import org.slf4j.Logger;
import org.slf4j.profiler.Profiler;
import perf.qdup.*;

public interface Context {

    void next(String output);
    void skip(String output);
    void update(String output);

    Logger getRunLogger();

    void terminal(String output);
    boolean isColorTerminal();
    Profiler getProfiler();


    String getRunOutputPath();

    Script getScript(String name,Cmd command);
    SshSession getSession();
    Host getHost();
    State getState();
    void addPendingDownload(String path,String destination);
    void abort(Boolean skipCleanup);
    void done();
    Local getLocal();
    void schedule(Runnable runnable, long delayMs);
    Coordinator getCoordinator();


}
