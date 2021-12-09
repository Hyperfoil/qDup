package io.hyperfoil.tools.qdup.config.rule;

import io.hyperfoil.tools.qdup.Stage;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.impl.SetSignal;
import io.hyperfoil.tools.qdup.cmd.impl.Signal;
import io.hyperfoil.tools.qdup.cmd.impl.WaitFor;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.RunRule;
import io.hyperfoil.tools.qdup.config.RunSummary;
import io.hyperfoil.tools.yaup.Counters;
import io.hyperfoil.tools.yaup.HashedLists;

import java.util.*;

/*
 Get the number of signals for each phase and ensure wait-fors have a matching signal
 */
public class SignalCounts implements RunRule {

    private Counters<String> signals;
    private HashedLists<String,RSSCRef> waits;


    public SignalCounts(){
        signals = new Counters<>();
        waits = new HashedLists<>();
    }

    public Counters<String> getCounts(){return signals;}
    public Set<String> getWaiters(){return waits.keys();}
    @Override
    public void scan(String role, Stage stage, String script, String host, Cmd command, Location location, Cmd.Ref ref, RunConfigBuilder config, RunSummary summary) {
        if(command instanceof WaitFor){
            String populated = Cmd.populateStateVariables(((WaitFor) command).getName(), command, config.getState(), null, null, ref);
            if(Cmd.hasStateReference(populated,command)){
                //TODO do we warn about wait-for something that cannot resolve at compile time
            }
            waits.put(populated,new RSSCRef(role,stage,script,command));
        }else if (command instanceof Signal){
            String populated = Cmd.populateStateVariables(((Signal) command).getName(), command, config.getState(), null, null, ref);
            if(Cmd.hasStateReference(populated,command)){
                //TODO do we warn about signal something that cannot resolve at compile time
            }
            //make sure signal does not occur after wait-for in same script
            if(!signals.contains(populated) && waits.containsKey(populated)){

                boolean found = false;
                if(Location.OnTimer.equals(location)){
                    Cmd target = command;
                    while (!found && target != null && target.hasStateParent() ){
                        target = target.getStateParent();
                        if(target instanceof WaitFor ){
                            WaitFor waitParent = (WaitFor)target;
                            String waitSignal = Cmd.populateStateVariables(waitParent.getName(), waitParent, config.getState(), null, null, ref);
                            if(waitSignal.equals(populated)){
                                found = true;
                            }
                        }
                    }
                }

                if(!found) {
                    RSSCRef signalRef = new RSSCRef(role, stage, script, command);
                    waits.get(populated).stream().filter(rssc -> {
                        return signalRef.isSameScript(rssc) ||
                                rssc.isBeforeOrSequentiallyWith(signalRef);
                    }).forEach(rssc -> {
                        summary.addError(
                                rssc.getRole(),
                                rssc.getStage(),
                                rssc.getScript(),
                                rssc.getCommand().toString(),
                                "wait-for occurs before signal"
                        );
                    });
                }
            }
            signals.add(populated);
        }else if (command instanceof SetSignal){
            String populated = Cmd.populateStateVariables(((SetSignal)command).getName(),command, config.getState(), null, null, ref);
            if(Cmd.hasStateReference(populated,command)){
                //TODO do we warn about signal something that cannot resolve at compile time
            }
            signals.add(populated);
        }
    }

    @Override
    public void close(RunConfigBuilder config, RunSummary summary) {
        waits.keys().stream().filter(name->!signals.contains(name) && name!=null && !name.isBlank()).forEach(name->{
            waits.get(name).forEach(ref->{
                summary.addError(
                    ref.getRole(),
                    ref.getStage(),
                    ref.getScript(),
                    ref.getCommand().toString(),
                    "waiting for "+name+" without a signal"
                );
            });
        });
    }

    public List<String> getSignalNames(){return signals.entries();}
    public long getSignalCount(String name){
        return signals.count(name);
    }
    public boolean hasSignal(String name){
        return signals.contains(name);
    }
    public Set<String> getWaitNames(){return waits.keys();}
    public boolean hasWaitName(String name){
        return waits.keys().contains(name);
    }
}
