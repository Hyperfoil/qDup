package perf.qdup.config;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import perf.qdup.*;
import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Script;
import perf.qdup.cmd.impl.ScriptCmd;
import perf.yaup.HashedLists;

import java.lang.invoke.MethodHandles;
import java.util.*;
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


    private StageSummary setupStage;
    private StageSummary runStage;
    private StageSummary cleanupStage;

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
            StageSummary setupStage,
            StageSummary runStage,
            StageSummary cleanupStage,
            String knownHosts,
            String identity,
            String passphrase){
        this.name = name;
        this.scripts = scripts;
        this.state = state;
        this.setupCmds = setupCmds;
        this.runScripts = runScripts;
        this.cleanupCmds = cleanupCmds;
        this.setupStage = setupStage;
        this.runStage = runStage;
        this.cleanupStage = cleanupStage;
        this.knownHosts = knownHosts;
        this.identity = identity;
        this.passphrase = passphrase;
        this.errors = new LinkedList<>();
    }

    public StageSummary getSetupStage() {
        return setupStage;
    }
    public StageSummary getRunStage() {
        return runStage;
    }
    public StageSummary getCleanupStage() {
        return cleanupStage;
    }


    public String debug(){
        if(hasErrors()){
            return getErrors().stream().collect(Collectors.joining("\n"));
        }
        StringBuilder  sb = new StringBuilder();
        if(!scripts.isEmpty()){
            sb.append("SCRIPTS\n");
            for(String scriptName : scripts.keySet()){
                sb.append(""+scriptName+"\n");
                Script script = scripts.get(scriptName);
                sb.append(script.tree(2,false));
            }
        }else{
            sb.append("NO SCRIPTS\n");
        }
        if(!setupCmds.isEmpty()){
            sb.append("SETUP\n");
            for(Host host : setupCmds.keySet()){
                sb.append("  "+host.toString()+"\n");
                Cmd cmd = setupCmds.get(host);
                sb.append(cmd.tree());
                cmd.getThens().forEach(then->{

                });
            }
        }else{
            sb.append("NO SETUP CMDS\n");
        }
        if(!runScripts.isEmpty()){
            sb.append("RUN\n");
            for(Host host : runScripts.keys()){
                sb.append("  "+host.toString()+"\n");
                 List<ScriptCmd> scriptCmds = runScripts.get(host);
                 scriptCmds.forEach(c->sb.append("    "+c.getName()+"\n"));
            }
        }else{
            sb.append("NO RUN SCRIPTS\n");
        }
        if(!cleanupCmds.isEmpty()){
            sb.append("CLEANUP\n");
            for(Host host : cleanupCmds.keySet()){
                sb.append("  "+host.toString()+"\n");
                Cmd cmd = cleanupCmds.get(host);
                sb.append("    "+cmd.getThens().toString()+"\n");
            }
        }else{
            sb.append("NO CLEANUP CMDS\n");
        }

        sb.append("STATE\n");
        sb.append(state.tree());
        return sb.toString();
    }

    public boolean hasErrors(){return !errors.isEmpty();}
    public List<String> getErrors(){return Collections.unmodifiableList(errors);}
    public void addError(String error){
        errors.add(error);
    }

    public String getName(){return name;}

    public State getState(){return state;}


    public Set<String> getScriptNames(){return scripts.keySet();}

    /**
     * get a script using the global state and no command variables
     * @param name
     * @return
     */
    public Script getScript(String name){
        return getScript(name,null,state);
    }

    /**
     * get a script using the target state but no command variables
     * @param name
     * @param state
     * @return
     */
    public Script getScript(String name,State state){
        return getScript(name,null,state);
    }

    /**
     * get a script using the target state and the commands variables
     * @param name
     * @param command
     * @param state
     * @return
     */
    public Script getScript(String name,Cmd command,State state){

        String populatedName = Cmd.populateStateVariables(name,command,state);
        return scripts.get(populatedName);
    }

    /**
     * get the hosts that have setup scripts
     * @return
     */
    public Set<Host> getSetupHosts(){return setupCmds.keySet();}

    /**
     * get the cmd that will invoke all setup scripts for the host
     * @param host
     * @return
     */
    public Cmd getSetupCmd(Host host){return setupCmds.get(host);}

    public Set<Host> getRunHosts(){return runScripts.keys();}
    public List<ScriptCmd> getRunCmds(Host host){return runScripts.get(host);}

    public Set<Host> getCleanupHosts(){return cleanupCmds.keySet();}
    public Cmd getCleanupCmd(Host host){return cleanupCmds.get(host);}

}
