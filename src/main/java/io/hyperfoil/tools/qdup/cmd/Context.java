package io.hyperfoil.tools.qdup.cmd;

import io.hyperfoil.tools.qdup.Coordinator;
import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.Local;
import io.hyperfoil.tools.qdup.SshSession;
import io.hyperfoil.tools.qdup.State;

import io.hyperfoil.tools.yaup.time.SystemTimer;
import org.slf4j.Logger;
import org.slf4j.profiler.Profiler;

public interface Context {

    default boolean checkExitCode(){return false;}

    void next(String output);
    void skip(String output);
    void update(String output);

    //Logger getRunLogger();
    void log(String message);
    void error(String message);
    void terminal(String output);

    boolean isColorTerminal();
    SystemTimer getTimer();

    String getRunOutputPath();

    Cmd getCurrentCmd();

    Script getScript(String name,Cmd command);
    SshSession getSession();
    Host getHost();
    State getState();
    void addPendingDownload(String path,String destination, Long maxSize);
    void addPendingDelete(String path);
    void abort(Boolean skipCleanup);
    void done();
    Local getLocal();
    void schedule(Runnable runnable, long delayMs);
    Coordinator getCoordinator();

    void close();

}
