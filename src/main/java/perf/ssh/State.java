package perf.ssh;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by wreicher
 * Stores 3 sets of tiered properties: run properties, host properties, script properties.
 * run properties are shared across all hosts and scripts
 * host properties are shared between all scripts on the host
 * script properties are unique for each script added to the run
 *   scripts called from within a script will interact with the script properties of the calling script
 */
public class State {


    public static final String RUN_PREFIX = "RUN";
    public static final String HOST_PREFIX = "HOST";
    HashMap<String,String> run;
    HashMap<String,String> host;
    HashMap<String,String> script;

    public State(){
        this(new HashMap<>(),new HashMap<>(),new HashMap<>());
    }

    public State newScriptState(){
        return new State(this.run,this.host,new HashMap<>());
    }
    public State newHostState(){
        return new State(this.run,new HashMap<>(),new HashMap<>());
    }

    private State(HashMap<String,String> global,HashMap<String,String> host,HashMap<String,String> script){
        this.run = global;
        this.host = host;
        this.script = script;
    }

    public String get(String key){
        if(script.containsKey(key)) {
            return script.get(key);
        } else if (host.containsKey(key)) {
            return host.get(key);
        } else {
            return run.get(key);
        }
    }
    private void set(Map<String,String> map, String key, String value){
        map.put(key,value);
    }
    public void setRun(String key, String value){
        set(run,key,value);
    }
    public void setHost(String key,String value){
        set(host,key,value);
    }
    public void setScript(String key,String value){
        set(script,key,value);
    }
    public void set(String key, String value){
        if(key.startsWith(RUN_PREFIX)){
            setRun(key,value);
        }else if (key.startsWith(HOST_PREFIX)){
            setHost(key,value);
        }else {
            setScript(key,value);
        }

    }
    private List<String> getKeys(Map<String,String> map){
        return Arrays.asList(map.keySet().toArray(new String[0]));
    }
    public List<String> getRunKeys(){ return getKeys(run); }
    public List<String> getHostKeys(){ return getKeys(host); }
    public List<String> getScriptKeys(){ return getKeys(script); }

}
