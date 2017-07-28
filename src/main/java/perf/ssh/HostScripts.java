package perf.ssh;

import perf.ssh.cmd.Script;

import java.util.*;

class HostScripts {
    LinkedHashSet<Script> setup;
    HashSet<Script> run;
    LinkedHashSet<Script> cleanup;
    public HostScripts(){
        setup = new LinkedHashSet<>();
        run = new HashSet<>();
    }
    public void addRunScript(Script script){
        run.add(script);
    }
    public void removeRunScript(Script script){
        run.remove(script);
    }
    public void addSetupScript(Script script){
        setup.add(script);
    }
    public void removeSetupScript(Script script){
        setup.remove(script);
    }
    public void addCleanupScript(Script script){cleanup.add(script);}
    public void removeCleanupScript(Script script){cleanup.remove(script);}
    public List<Script> setupScripts(){return Collections.unmodifiableList(Arrays.asList(setup.toArray(new Script[0])));}
    public List<Script> runScripts(){return Collections.unmodifiableList(Arrays.asList(run.toArray(new Script[0])));}
    public List<Script> cleanupScripts(){return Collections.unmodifiableList(Arrays.asList(cleanup.toArray(new Script[0])));}
}
