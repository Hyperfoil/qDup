package perf.qdup;

import org.slf4j.profiler.Profiler;
import org.slf4j.profiler.ProfilerRegistry;

/**
 * Created by wreicher
 * A convenience wrapper around slf4j Profiles.
 * May need to change the api to no longer have an external dependency on slf4j extension
 */
public class Profiles {

    private ProfilerRegistry registry;

    public Profiles(){
        registry = new  ProfilerRegistry();
    }

    public Profiler get(String name){
        Profiler rtrn = registry.get(name);
        if(rtrn==null){
            rtrn = new Profiler(name);
            rtrn.registerWith(registry);
        }
        return rtrn;
    }


}
