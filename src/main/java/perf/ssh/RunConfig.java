package perf.ssh;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import perf.ssh.cmd.CommandSummary;
import perf.ssh.cmd.Script;
import perf.util.Counters;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.stream.Collectors;

public class RunConfig {

    final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

    private String name;
    private ScriptRepo repo;
    private State state;
    private HashMap<String,Role> roles;
    private HashMap<String,Host> hosts;
    private HashMap<Host,HostScripts> hostScripts;

    public RunConfig(){
        repo = new ScriptRepo();
        state = new State(null,State.RUN_PREFIX);
        roles = new HashMap<>();
        hosts = new HashMap<>();
        hostScripts = new HashMap<>();
    }

    public void addRole(Role role){
        roles.put(role.getName(),role);
    }
    public Role getRole(String name){
        if(!roles.containsKey(name)){
            roles.put(name,new Role(name,this));
        }
        return roles.get(name);
    }
    public List<String> getRoleNames(){return Arrays.asList(roles.keySet().toArray(new String[0]));}

    public String getName(){return name;}
    public void setName(String name){this.name = name;}

    public State getState(){return state;}

    public Script getScript(String name){
        return repo.getScript(name);
    }
    public void addScript(Script script){
        repo.addScript(script);
    }

    public ScriptRepo getRepo(){return repo;}

    public HostList allHosts(){
        return new HostList(Arrays.asList(hostScripts.keySet().toArray(new Host[0])),this);
    }

    public void addHost(Host host){
        addHost(host.toString(),host);
    }
    public void addHost(String shortname,Host host){
       ensureHostScripts(host);
       hosts.put(shortname,host);
    }
    public Host getHost(String shortname){
        return hosts.get(shortname);
    }
    public List<String> getHostNames(){
        return Collections.unmodifiableList(Arrays.asList(hosts.keySet().toArray(new String[0])));
    }
    protected void addAllHosts(List<Host> hosts){
        for(Host host : hosts){
            addHost(host);
        }
    }
    private HostScripts ensureHostScripts(Host host){
        if(!hostScripts.containsKey(host)){
            hostScripts.put(host,new HostScripts());
        }
        return hostScripts.get(host);
    }
    protected void addRunScript(Host host,Script script){
        logger.trace("{} addRunScript {}@{}",this,script.getName(),host.getHostName());
        ensureHostScripts(host).addRunScript(script);
    }
    protected void removeRunScript(Host host,Script script){
        ensureHostScripts(host).removeRunScript(script);
    }
    public List<Script> getRunScripts(Host host){
        return ensureHostScripts(host).runScripts();
    }
    public boolean validate(){
        boolean rtrn = true;
        Counters<String> signalCounters = new Counters<>();
        HashSet<String> waiters = new HashSet<>();
        HashSet<String> signals = new HashSet<>();
        for(Host host : allHosts().toList()){
            for( Script script : getRunScripts(host) ){
                CommandSummary summary = CommandSummary.apply(script,getRepo());

                if(!summary.getWarnings().isEmpty()){
                    rtrn = false;
                    for(String warning : summary.getWarnings()){
                        logger.error("{} {}",script.getName(),warning);
                    }
                }
                for(String signalName : summary.getSignals()){
                    logger.trace("{} {}@{} signals {}",this,script.getName(),host.getHostName(),signalName);
                    signalCounters.add(signalName);
                }
                waiters.addAll(summary.getWaits());
                signals.addAll(summary.getSignals());
            }

        }
        List<String> noSignal = waiters.stream().filter((waitName)->!signals.contains(waitName)).collect(Collectors.toList());
        List<String> noWaiters = signals.stream().filter((signalName)->!waiters.contains(signalName)).collect(Collectors.toList());
        if(!noSignal.isEmpty()){
            logger.error("{} missing signals for {}",this,noSignal);
            rtrn = false;
        }
        if(!noWaiters.isEmpty()){
            logger.trace("{} nothing waits for {}",this,noWaiters);
        }
        return rtrn;
    }

    protected void addSetupScript(Host host,Script script){
        logger.trace("{} addSetupScript {}@{}",this,script.getName(),host.getHostName());
        ensureHostScripts(host).addSetupScript(script);
    }
    protected void removeSetupScript(Host host,Script script){
        ensureHostScripts(host).removeSetupScript(script);
    }
    protected List<Script> getSetupScripts(Host host){
        return ensureHostScripts(host).setupScripts();
    }

    protected void addCleanupScript(Host host,Script script){
        ensureHostScripts(host).addCleanupScript(script);
    }
}
