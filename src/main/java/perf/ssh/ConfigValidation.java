package perf.ssh;

import perf.util.Counters;

import java.util.*;

/**
 * @arthur wreicher
 * Stores the results of validating the RunConfig
 */
public class ConfigValidation {

    private List<String> errors;
    private Counters<String> signalCounters;
    private HashSet<String> waiters;
    private HashSet<String> signals;

    public ConfigValidation(){
        errors = new LinkedList<>();
        signalCounters = new Counters<>();
        waiters = new HashSet<>();
        signals = new HashSet<>();
    }

    protected void addError(String message){
        errors.add(message);
    }
    public List<String> getErrors(){
        return Collections.unmodifiableList(errors);
    }
    protected void addSignal(String name){
        signalCounters.add(name);
        signals.add(name);
    }
    public Set<String> getSignals(){
        return Collections.unmodifiableSet(signals);
    }
    public int getSignalCount(String name){
        return signalCounters.count(name);
    }
    protected void addWait(String name){
        waiters.add(name);
    }
    public Set<String> getWaiters(){
        return Collections.unmodifiableSet(waiters);
    }

}
