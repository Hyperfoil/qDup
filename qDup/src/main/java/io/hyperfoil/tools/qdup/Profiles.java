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
    private ConcurrentHashMap<String,Json> properties;
    public Profiles(){
        names = new ConcurrentHashMap<>();
        properties = new ConcurrentHashMap<>();
    }

    public Json getProperties(String name) { return properties.computeIfAbsent(name,(v)->new Json(false));}

    public SystemTimer get(String name){
        return names.computeIfAbsent(name,(v)-> new SystemTimer(v));
    }

    public Json getJson(){
        Json rtrn = new Json(true);
        names.forEach((name,timer)->{
            Json newEntry = new Json();
            newEntry.set("name",name);
            getProperties(name).forEach(newEntry::set);
            newEntry.set("timer",timer.getJson());
            rtrn.add(newEntry);
        });
        return rtrn;
    }
}
