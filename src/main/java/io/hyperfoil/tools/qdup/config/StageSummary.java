package io.hyperfoil.tools.qdup.config;

import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.yaup.Counters;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * @author wreicher
 * Stores the results of validating the RunConfig
 */
public class StageSummary {

    private boolean isSequential = false;

    private RunSummary runSummary;
    private List<String> errors;
    private Counters<String> signalCounters;
    private HashSet<String> waiters;
    private HashSet<String> signals;
    private Set<String> useVariables;
    private Set<String> setVariables;

    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder();
        sb.append("Errors:\n");
        getErrors().forEach(s->sb.append(s+"\n"));
        sb.append("Signals: ");
        getSignals().forEach(s->sb.append(s+" "));
        sb.append("\nWaiters: ");
        getWaiters().forEach(s->sb.append(s+" "));
        sb.append("\nUse Variables: ");
        getUseVariables().forEach(s->sb.append(s+" "));
        sb.append("\nSet Variables: ");
        getSetVariables().forEach(s->sb.append(s+" "));

        return sb.toString();
    }

    public StageSummary(RunSummary runSummary){
        this.runSummary = runSummary;

        errors = new LinkedList<>();
        signalCounters = new Counters<>();
        waiters = new HashSet<>();
        signals = new HashSet<>();
        useVariables = new HashSet<>();
        setVariables = new HashSet<>();
    }

    public void add(CmdSummary summary){
        summary.getWarnings().forEach(this::addError);
        summary.getSignals().forEach(this::addSignal);
        summary.getWaits().forEach(this::addWait);
        summary.getUseVariables().forEach(this::addUseVariable);
        summary.getSetVariables().forEach(this::addSetVariable);
    }

    private void addSetVariable(String name) {
        setVariables.add(name);
    }
    private void addUseVariable(String name) {
        useVariables.add(name);
    }
    public Set<String> getUseVariables() {
        return useVariables;
    }
    public Set<String> getSetVariables() {
        return setVariables;
    }
    public Set<String> getStateDependentVariables() {
        Set<String> rtrn = new HashSet<>(useVariables);
        rtrn.removeAll(setVariables);
        return rtrn;
    }

    protected void addError(String message){
        errors.add(message);
    }

    public List<String> getErrors(){
        return Collections.unmodifiableList(errors);
    }
    public boolean hasErrors(){return !errors.isEmpty();}

    protected void addSignal(String name,long amount){
        signalCounters.add(name,amount);
        signals.add(name);
    }
    public Set<String> getSignals(){
        return Collections.unmodifiableSet(signals);
    }
    public long getSignalCount(String name){
        return signalCounters.count(name);
    }

    protected void addWait(String name){
        waiters.add(name);
    }
    public Set<String> getWaiters(){
        return Collections.unmodifiableSet(waiters);
    }

    public void forEach(BiConsumer<String,Long> consumer){
        signalCounters.entries().forEach(name->consumer.accept(name,signalCounters.count(name)));
    }
}
