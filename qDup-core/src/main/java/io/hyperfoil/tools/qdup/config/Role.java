package io.hyperfoil.tools.qdup.config;

import io.hyperfoil.tools.qdup.Env;
import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.cmd.impl.ScriptCmd;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Role {

    private String name;
    private HostExpression hostExpression;
    private Set<String> hostRefs;
    private List<Host> hosts;
    private Map<Host,Env> environments;
    private List<ScriptCmd> setup;
    private List<ScriptCmd> run;
    private List<ScriptCmd> cleanup;

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
    public List<ScriptCmd> getSetup(){return Collections.unmodifiableList(setup);}
    public List<ScriptCmd> getRun(){return Collections.unmodifiableList(run);}
    public List<ScriptCmd> getCleanup(){return Collections.unmodifiableList(cleanup);}
    public boolean hasScripts(){
        return !(setup.isEmpty() && run.isEmpty() && cleanup.isEmpty());
    }

    /**
     * @return Hosts that are listed in the role. Empty if the role uses an expression
     */
    public List<Host> getDeclaredHosts(){
        return Collections.unmodifiableList(hosts);
    }

    /**
     * Get the hosts used by this role.
     * @param runConfig the RunConfig to resolve role references
     * @return all hosts that will be used for this role
     */
    public List<Host> getHosts(RunConfig runConfig) {
        if (hasHostExpression()) {
            return Collections.unmodifiableList(hostExpression.getHosts(runConfig));
        } else {
            return getDeclaredHosts();
        }
    }

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
