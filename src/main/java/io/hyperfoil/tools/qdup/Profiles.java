package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.yaup.time.SystemTimer;
import org.slf4j.profiler.Profiler;
import org.slf4j.profiler.ProfilerRegistry;
import io.hyperfoil.tools.yaup.json.Json;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by wreicher
 * A convenience wrapper around SystemTimers.
 */
public class Profiles {

    private ConcurrentHashMap<String,SystemTimer> names;

    public Profiles(){
        names = new ConcurrentHashMap<>();
    }

    public SystemTimer get(String name){
        return names.computeIfAbsent(name,(v)-> new SystemTimer(v));
    }

    public Json getJson(){
        Json rtrn = new Json();
        names.forEach((name,timer)->{
            rtrn.set(name,timer.getJson());
        });
        return rtrn;
    }
}
