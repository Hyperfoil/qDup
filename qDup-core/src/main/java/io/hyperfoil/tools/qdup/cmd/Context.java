package io.hyperfoil.tools.qdup.cmd;

import io.hyperfoil.tools.qdup.*;

import io.hyperfoil.tools.qdup.shell.AbstractShell;
import io.hyperfoil.tools.yaup.json.Json;
import io.hyperfoil.tools.yaup.time.SystemTimer;

public interface Context {

    default boolean checkExitCode(){return false;}

    void next(String output);
    void skip(String output);
    void update(String output);

    void log(String message);
    void error(String message);
    void terminal(String output);

    boolean isColorTerminal();
    SystemTimer getContextTimer();
    SystemTimer getCommandTimer();

    String getRunOutputPath();

    Cmd getCurrentCmd();

    Script getScript(String name,Cmd command);
    AbstractShell getShell();
    Host getHost();
    State getState();
    void addPendingDownload(String path,String destination, Long maxSize);
    void addPendingDelete(String path);
    void abort(Boolean skipCleanup);
    void done();
    Local getLocal();
    void schedule(Runnable runnable, long delayMs);
    Coordinator getCoordinator();
    Json getTimestamps();
    void close();
    boolean isAborted();

    void setCwd(String dir);
    String getCwd();

    void setHomeDir(String dir);
    String getHomeDir();

    Globals getGlobals();

}
