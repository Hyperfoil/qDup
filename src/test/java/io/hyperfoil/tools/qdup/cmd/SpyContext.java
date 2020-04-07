package io.hyperfoil.tools.qdup.cmd;

import io.hyperfoil.tools.qdup.Coordinator;
import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.Local;
import io.hyperfoil.tools.qdup.SshSession;
import io.hyperfoil.tools.qdup.State;

import io.hyperfoil.tools.yaup.time.SystemTimer;
import org.slf4j.Logger;
import org.slf4j.profiler.Profiler;

import java.util.ArrayList;
import java.util.List;

public class SpyContext implements Context {

    List<String> updates;
    String next;
    String skip;
    State state = new State("");



    public SpyContext(){
        updates = new ArrayList<>();
        next = null;
        skip = null;
    }

    public void clear(){
        next=null;
        skip=null;
        updates.clear();
    }

    @Override
    public void next(String output) {
        next = output;
    }

    @Override
    public void skip(String output) {
        skip = output;
    }

    @Override
    public void update(String output) {
        updates.add(output);
    }

    @Override
    public void log(String message) {}

    @Override
    public void error(String message) {}

    @Override
    public void terminal(String output) {}

    @Override
    public boolean isColorTerminal() {
        return false;
    }

    @Override
    public SystemTimer getTimer() {
        return null;
    }

    @Override
    public Host getHost(){return null;}
    @Override
    public String getRunOutputPath() {
        return null;
    }

    @Override
    public Cmd getCurrentCmd() {
        return null;
    }

    @Override
    public Script getScript(String name, Cmd command) {
        return null;
    }

    @Override
    public SshSession getSession() {
        return null;
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public void addPendingDownload(String path, String destination) {

    }

    @Override
    public void abort(Boolean skipCleanup) {

    }

    @Override
    public void done() {

    }

    @Override
    public Local getLocal() {
        return null;
    }

    @Override
    public void schedule(Runnable runnable, long delayMs) {

    }

    @Override
    public Coordinator getCoordinator() {
        return null;
    }

    @Override
    public void close() { }

    public String getNext(){return next;}
    public boolean hasNext(){return next!=null;}
    public String getSkip(){return skip;}
    public boolean hasSkip(){return skip!=null;}
    public List<String> getUpdates(){return updates;}
}
