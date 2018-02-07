package perf.ssh.config;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import perf.ssh.*;
import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.CommandSummary;
import perf.ssh.cmd.Script;
import perf.ssh.cmd.impl.ScriptCmd;
import perf.util.HashedList;
import perf.util.HashedLists;
import perf.util.HashedSets;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Immutable representation of the Configuration for the Run
 * This includes all Hosts, Scripts, and State
 */
public class RunConfig {

    private final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

    public String getKnownHosts() {
        return knownHosts;
    }
    public boolean hasCustomKnownHosts(){
        return !RunConfigBuilder.DEFAULT_KNOWN_HOSTS.equals(knownHosts);
    }

    public String getIdentity() {
        return identity;
    }
    public boolean hasCustomIdentity(){
        return !RunConfigBuilder.DEFAULT_IDENTITY.equals(identity);
    }

    public String getPassphrase() {
        return passphrase;
    }
    public boolean hasCustomPassphrase(){
        return passphrase!= RunConfigBuilder.DEFAULT_PASSPHRASE;//use != because DEFAULT_PASSPHRASE is null
    }

    public Boolean isColorTerminal() {
        return colorTerminal;
    }
    public void setColorTerminal(Boolean colorTerminal) {
        this.colorTerminal = colorTerminal;
    }

    private String name;
    private String knownHosts;
    private String identity;
    private String passphrase;

    private Map<String,Script> scripts;
    private State state;

    private Map<Host,Cmd> setupCmds;
    private HashedLists<Host,ScriptCmd> runScripts;
    private Map<Host,Cmd> cleanupCmds;

    private Boolean colorTerminal = false;
    private List<String> errors;

    protected RunConfig(String name,List<String> errors){
        this.errors = errors;
    }
    protected RunConfig(
            String name,
            Map<String,Script> scripts,
            State state,
            Map<Host,Cmd> setupCmds,
            HashedLists<Host,ScriptCmd> runScripts,
            Map<Host,Cmd> cleanupCmds,
            String knownHosts,
            String identity,
            String passphrase){
        this.name = name;
        this.scripts = scripts;
        this.state = state;
        this.setupCmds = setupCmds;
        this.runScripts = runScripts;
        this.cleanupCmds = cleanupCmds;
        this.knownHosts = knownHosts;
        this.identity = identity;
        this.passphrase = passphrase;
    }

    public boolean hasErrors(){return !errors.isEmpty();}
    public List<String> getErrors(){return Collections.unmodifiableList(errors);}
    public void addError(String error){
        errors.add(error);
    }

    public String getName(){return name;}

    public State getState(){return state;}

    public Script getScript(String name){
        return scripts.get(name);
    }

    public Set<Host> getSetupHosts(){return setupCmds.keySet();}
    public Cmd getSetupCmd(Host host){return setupCmds.get(host);}

    public Set<Host> getRunHosts(){return runScripts.keys();}
    public List<ScriptCmd> getRunCmds(Host host){return runScripts.get(host);}

    public Set<Host> getCleanupHosts(){return cleanupCmds.keySet();}
    public Cmd getCleanupCmd(Host host){return cleanupCmds.get(host);}

}
