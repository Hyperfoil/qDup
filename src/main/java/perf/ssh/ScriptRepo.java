package perf.ssh;

import perf.ssh.cmd.Script;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by wreicher
 * A Map of scripts by name that will return a new, empty script if the name was not already used
 */
public class ScriptRepo {

    private Map<String,Script> scripts;

    public ScriptRepo(){
        scripts = new HashMap<>();
    }

    protected void addScript(Script script){
        scripts.put(script.getName(),script);
    }

    public Script script(String name){
        if(!hasScript(name)){
            scripts.put(name,new Script(name));
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
