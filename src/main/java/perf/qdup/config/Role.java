package perf.qdup.config;

import perf.qdup.Env;
import perf.qdup.Host;
import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.impl.ScriptCmd;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Role {

    private String name;
    private HostExpression hostExpression;
    private Set<String> hostRefs;
    private List<Host> hosts;
    private Map<Host,Env> environments;
    private List<Cmd> setup;
    private List<Cmd> run;
    private List<Cmd> cleanup;

    public Role(){
        this("");
    }
    public Role(String name){
        this.name = name;
        this.hostExpression=null;
        this.hostRefs = new HashSet<>();
        this.hosts = new ArrayList<>();
        this.setup = new ArrayList<>();
        this.run = new ArrayList<>();
        this.cleanup = new ArrayList<>();
        this.environments = new ConcurrentHashMap<>();
    }
    public boolean hasHostExpression(){return hostExpression!=null;}
    public void setHostExpression(HostExpression expression){
        this.hostExpression = expression;
    }
    public HostExpression getHostExpression(){return hostExpression;}

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
    public List<Cmd> getSetup(){return Collections.unmodifiableList(setup);}
    public List<Cmd> getRun(){return Collections.unmodifiableList(run);}
    public List<Cmd> getCleanup(){return Collections.unmodifiableList(cleanup);}

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
    public void addHostRef(String name){
        this.hostRefs.add(name);
    }
    public boolean hasHostRefs(){return !hostRefs.isEmpty();}
    public Set<String> getHostRefs(){return Collections.unmodifiableSet(hostRefs);}



}
