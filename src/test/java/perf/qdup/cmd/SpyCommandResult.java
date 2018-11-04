package perf.qdup.cmd;

import org.slf4j.Logger;
import org.slf4j.profiler.Profiler;
import perf.qdup.Coordinator;
import perf.qdup.Local;
import perf.qdup.SshSession;
import perf.qdup.State;

import java.util.ArrayList;
import java.util.List;

public class SpyCommandResult implements Context {

    List<String> updates;
    String next;
    String skip;
    State state = new State("");

    public SpyCommandResult(){
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
    public Logger getRunLogger() {
        return null;
    }

    @Override
    public Profiler getProfiler() {
        return null;
    }

    @Override
    public String getRunOutputPath() {
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
    public void abort() {

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

    public String getNext(){return next;}
    public boolean hasNext(){return next!=null;}
    public String getSkip(){return skip;}
    public boolean hasSkip(){return skip!=null;}
}
