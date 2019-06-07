package io.hyperfoil.tools.qdup;

import org.slf4j.profiler.Profiler;
import org.slf4j.profiler.ProfilerRegistry;
import perf.yaup.json.Json;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by wreicher
 * A convenience wrapper around slf4j Profiles.
 * May need to change the api to no longer have an external dependency on slf4j extension
 */
public class Profiles {

    private ProfilerRegistry registry;
    private Set<String> names;

    public Profiles(){
        registry = new  ProfilerRegistry();
        names = new HashSet<>();
    }

    public Profiler get(String name){
        names.add(name);
        Profiler rtrn = registry.get(name);
        if(rtrn==null){
            rtrn = new Profiler(name);
            rtrn.registerWith(registry);
        }
        return rtrn;
    }

    public Json getJson(){
        Json rtrn = new Json();
        names.forEach(name->{
            rtrn.set(name,toJson(registry.get(name)));
        });

        return rtrn;
    }

    private Json toJson(Profiler profile){
        Json rtrn = new Json();
        if(profile!=null) {
            profile.getCopyOfChildTimeInstruments().forEach(timeInstrument -> {
                if(timeInstrument!=null) {
                    Json toAdd = new Json();
                    toAdd.set("name", timeInstrument.getName()!=null ? timeInstrument.getName():"");
                    toAdd.set("ns", timeInstrument.elapsedTime());
                    rtrn.add(toAdd);
                }

            });
        }else{
            
        }

        return rtrn;
    }
}
