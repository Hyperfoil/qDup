package perf.ssh;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import perf.ssh.cmd.CommandSummary;
import perf.ssh.cmd.Script;
import perf.util.HashedList;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RunConfig {

    private final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

    class HostMap {
        private HashMap<String,Host> aliases;
        private HashMap<String,Host> fullyQualified;

        public HostMap(){
            aliases = new HashMap<>();
            fullyQualified = new HashMap<>();
        }
        public void put(String name,Host host){
            if(fullyQualified.containsKey(host.toString())){
                host = fullyQualified.get(host.toString());
            }
            aliases.put(name,host);
        }
        public boolean contains(String name){
            return aliases.containsKey(name);
        }
        public boolean contains(Host host){
            return fullyQualified.containsKey(host.toString());
        }
        public List<String> getFullyQualifiedNames(){
            return Collections.unmodifiableList(Arrays.asList(fullyQualified.keySet().toArray(new String[0])));
        }
        public Host get(String name){
            return aliases.get(name);
        }

    }

    private String name;
    private ScriptRepo repo;
    private State state;

    private HashMap<String,Role> roles;
    private HostMap hosts;

    public RunConfig(){
        this("run-"+System.currentTimeMillis());
    }
    public RunConfig(String name){
        repo = new ScriptRepo();
        state = new State(null,State.RUN_PREFIX);
        roles = new HashMap<>();
        hosts = new HostMap();
        this.name = name;
    }

    public void addRole(Role role){
        roles.put(role.getName(),role);
    }
    public Role getRole(String name){
        if(!roles.containsKey(name)){
            roles.put(name,new Role(name));
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

    public HostList getHostsInRole(){
        HostList rtrn = new HostList();
        roles.forEach(
            (roleName,role)->{
                rtrn.addAll(role);
            }
        );
        return rtrn;
    }

    public void addHost(Host host){
        addHost(host.toString(),host);
    }
    public void addHost(String shortname,Host host){
       hosts.put(shortname,host);
    }
    public Host getHost(String shortname){
        return hosts.get(shortname);
    }
    public List<String> getHostNames(){
        return hosts.getFullyQualifiedNames();
    }
    protected void addAllHosts(List<Host> hosts){
        for(Host host : hosts){
            addHost(host);
        }
    }

    public List<Script> getCleanupScripts(String host){
        return getScripts(host,(role)-> role.getCleanupScripts());
    }
    public List<Script> getRunScripts(String host){
        return getScripts(host,(role)-> role.getRunScripts());
    }
    public List<Script> getSetupScripts(String host){
        return getScripts(host,(role)-> role.getSetupScripts());
    }
    private List<Script> getScripts(String host,Function<Role,List<String>> getScriptNames){
        HashedList<Script> rtrn = new HashedList<>();
        roles.forEach((roleName,role)->{
            if(role.matches(host)){
                getScriptNames.apply(role).forEach((scriptName)->{
                    Script script = getScript(scriptName);
                    rtrn.add(script);
                });
            }
        });
        return rtrn.toList();
    }
    public RunValidation validate(){
        return new RunValidation(
            validate(this::getSetupScripts),
            validate(this::getRunScripts),
            validate(this::getCleanupScripts));
    }
    private ConfigValidation validate(Function<String,List<Script>> getScripts){
        final ConfigValidation rtrn = new ConfigValidation();
        for(String host : getHostsInRole().toList()){
            for( Script script : getScripts.apply(host) ){
                CommandSummary summary = CommandSummary.apply(script,getRepo());

                if(!summary.getWarnings().isEmpty()){
                    for(String warning : summary.getWarnings()){
                        rtrn.addError(warning);
                    }
                }
                summary.getWaits().forEach(rtrn::addWait);
                summary.getSignals().forEach(rtrn::addSignal);
            }

        }
        List<String> noSignal = rtrn.getWaiters().stream().filter((waitName)->!rtrn.getSignals().contains(waitName)).collect(Collectors.toList());
        List<String> noWaiters = rtrn.getSignals().stream().filter((signalName)->!rtrn.getWaiters().contains(signalName)).collect(Collectors.toList());
        if(!noSignal.isEmpty()){
            rtrn.addError("missing signals for "+noSignal);
        }
        return rtrn;
    }
}
