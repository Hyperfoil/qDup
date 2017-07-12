package perf.ssh;

import perf.ssh.cmd.Script;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by wreicher
 * A Map of scripts by name that will return a new, empty Script if the name was not already used
 */
public class ScriptRepo {

    private Map<String,Script> scripts;

    public ScriptRepo(){
        scripts = new ConcurrentHashMap<>();
    }

    protected void addScript(Script script){
        scripts.put(script.getName(),script);
    }

    public Script getScript(String name){
        if(!hasScript(name)){
            Script previous = scripts.put(name,new Script(name));
            if(previous!=null){
                //TODO this is bad, means multiple threads clashed for the same getScript name
            }
        }
        return scripts.get(name);
    }
    public boolean hasScript(String name){
        return scripts.containsKey(name);
    }
    public List<String> getNames(){
        return Arrays.asList(scripts.keySet().toArray(new String[0]));
    }
    public int size(){return scripts.size();}

}
