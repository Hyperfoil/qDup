package perf.qdup.config;

import perf.qdup.Env;
import perf.qdup.Host;
import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Script;
import perf.qdup.cmd.impl.ScriptCmd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Role {

    private String name;
    private List<Host> hosts;
    private Map<Host,Env> environments;
    private List<ScriptCmd> setup;
    private List<ScriptCmd> run;
    private List<ScriptCmd> cleanup;

    public Role(String name){
        this.name = name;
        this.hosts = new ArrayList<>();
        this.setup = new ArrayList<>();
        this.run = new ArrayList<>();
        this.cleanup = new ArrayList<>();
        this.environments = new ConcurrentHashMap<>();
    }
    public boolean hasEnvironment(Host host){
        return environments.containsKey(host);
    }
    public Env getEnv(Host host){
        return environments.get(host);
    }
    public void addEnv(Host host,Env env){
        environments.put(host,env);
    }

    public String getName(){
        return name;
    }
    public List<ScriptCmd> getSetup(){return Collections.unmodifiableList(setup);}
    public List<ScriptCmd> getRun(){return Collections.unmodifiableList(run);}
    public List<ScriptCmd> getCleanup(){return Collections.unmodifiableList(cleanup);}

    public List<Host> getHosts(){return Collections.unmodifiableList(hosts);}

    public void addSetup(ScriptCmd script){
        this.setup.add(script);
    }
    public void addRun(ScriptCmd script){
        this.run.add(script);
    }
    public void addCleanup(ScriptCmd script){
        this.cleanup.add(script);
    }
    public void addHost(Host host){
        this.hosts.add(host);
    }

}
