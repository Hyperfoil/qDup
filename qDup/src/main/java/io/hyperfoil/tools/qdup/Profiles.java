package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.yaup.time.SystemTimer;
import io.hyperfoil.tools.yaup.json.Json;

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
