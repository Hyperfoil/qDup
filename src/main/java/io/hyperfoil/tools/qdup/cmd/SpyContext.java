package io.hyperfoil.tools.qdup.cmd;

import io.hyperfoil.tools.qdup.Coordinator;
import io.hyperfoil.tools.qdup.Globals;
import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.Local;
import io.hyperfoil.tools.qdup.SshSession;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.yaup.json.Json;
import io.hyperfoil.tools.yaup.time.SystemTimer;

import java.util.ArrayList;
import java.util.List;

public class SpyContext implements Context {

    List<String> updates;
    List<String> log;
    List<String> error;
    String next;
    String skip;
    private boolean aborted = false;
    private State state;
    private Coordinator coordinator;

    private Context context;

    public SpyContext(){
        this(null,new State(""),new Coordinator(new Globals()));
    }

    public SpyContext(Context context,State state, Coordinator coordinator){
        this.context = context;
        updates = new ArrayList<>();
        next = null;
        skip = null;
        log = new ArrayList<>();
        error = new ArrayList<>();
        this.state = state;
        this.coordinator = coordinator;
    }

    public void clear(){
        next=null;
        skip=null;
        updates.clear();
    }

    @Override
    public Json getTimestamps(){
        return new Json(false);
    }

    @Override
    public void next(String output) {
        next = output;
        if(context!=null){
            context.next(output);
        }
    }

    @Override
    public void skip(String output) {
        skip = output;
        if(context!=null){
            context.skip(output);
        }
    }

    @Override
    public void update(String output) {
        updates.add(output);
        if(context!=null){
            context.update(output);
        }
    }

    @Override
    public void log(String message) {
        log.add(message);
        if(context!=null){
            context.log(message);
        }
    }

    @Override
    public void error(String message) {
        error.add(message);
        if(context!=null){
            context.error(message);
        }
    }

    @Override
    public void terminal(String output) {
        if(context!=null){
            context.terminal(output);
        }
    }

    @Override
    public boolean isColorTerminal() {
        return false;
    }

    @Override
    public SystemTimer getContextTimer() {
        if(context!=null){
            return context.getContextTimer();
        }
        return null;
    }

    @Override
    public SystemTimer getCommandTimer() {
        if(context!=null){
            return context.getCommandTimer();
        }
        return null;
    }

    @Override
    public Host getHost(){
        if(context!=null){
            return context.getHost();
        }
        return null;
    }
    @Override
    public String getRunOutputPath() {
        if(context!=null){
            return context.getRunOutputPath();
        }
        return null;
    }

    @Override
    public Cmd getCurrentCmd() {
        if(context!=null){
            return context.getCurrentCmd();
        }
        return null;
    }

    @Override
    public Script getScript(String name, Cmd command) {
        if(context!=null){
            return context.getScript(name,command);
        }
        return null;
    }

    @Override
    public SshSession getSession() {
        if(context!=null){
            return context.getSession();
        }
        return null;
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public void addPendingDownload(String path, String destination, Long maxSize) {

    }
    @Override
    public void addPendingDelete(String path){

    }
    @Override
    public void abort(Boolean skipCleanup) {

        aborted = true;
        if(context!=null){
            context.abort(skipCleanup);
        }
    }

    @Override
    public void done() {
        if(context!=null){
            context.done();
        }
    }

    @Override
    public Local getLocal() {
        if(context!=null){
            return context.getLocal();
        }

        return null;
    }

    @Override
    public void schedule(Runnable runnable, long delayMs) {
        if(context!=null){
            context.schedule(runnable,delayMs);
        }
    }

    @Override
    public Coordinator getCoordinator() {
        if(context!=null){

            return context.getCoordinator();
        }
        return coordinator;
    }

    @Override
    public void close() {
        if(context!=null){
            context.close();
        }
    }

    public boolean isAborted(){return aborted;}
    public String getNext(){return next;}
    public boolean hasNext(){return next!=null;}
    public String getSkip(){return skip;}
    public boolean hasSkip(){return skip!=null;}
    public List<String> getUpdates(){return updates;}

    public List<String> getLogs(){return log;}
    public List<String> getErrors(){return error;}

    @Override
    public String toString(){return "SpyContext next="+next+" skip="+skip;}
}
