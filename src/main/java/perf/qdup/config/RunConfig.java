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
import java.util.function.BiConsumer;
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

    private Map<String,Role> roles;

    private StageSummary setupStage;
    private StageSummary runStage;
    private StageSummary cleanupStage;

    private Boolean colorTerminal = false;
    private List<String> errors;

    private int timeout = 10;

    protected RunConfig(String name,List<String> errors){
        this.name = name;
        this.errors = errors;
    }
    protected RunConfig(
            String name,
            Map<String,Script> scripts,
            State state,
            Map<String,Role> roles,
            StageSummary setupStage,
            StageSummary runStage,
            StageSummary cleanupStage,
            String knownHosts,
            String identity,
            String passphrase,
            Integer timeout){
        this.name = name;
        this.scripts = scripts;
        this.state = state;
        this.roles = roles;
        this.setupStage = setupStage;
        this.runStage = runStage;
        this.cleanupStage = cleanupStage;
        this.knownHosts = knownHosts;
        this.identity = identity;
        this.passphrase = passphrase;
        this.errors = new LinkedList<>();
        if ( timeout != null )
            this.timeout = timeout;
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

    public Set<String> getRoleNames(){return roles.keySet();}
    public Role getRole(String name){
        return roles.get(name);
    }

    public String debug(){
        return debug(false);
    }
    public String debug(boolean full){
        if(hasErrors()){
            return getErrors().stream().collect(Collectors.joining("\n"));
        }
        StringBuilder  sb = new StringBuilder();
        BiConsumer<String,List<ScriptCmd>> printScripts = (phase,scripts)->{
            sb.append(String.format("    %s%n",phase));
            scripts.forEach(script->{
                sb.append(String.format("      %s%n",script.getName()));
                if(!script.getWith().isEmpty()){
                    sb.append(String.format("        %s%n","WITH"));
                    script.getWith().forEach((k,v)->{
                        sb.append(String.format("          %s:%s%n",k,v));
                    });
                }
            });
        };
        if(!scripts.isEmpty()){
            sb.append("SCRIPTS\n");
            for(String scriptName : scripts.keySet()){
                sb.append(""+scriptName+"\n");
                Script script = scripts.get(scriptName);
                sb.append(script.tree(2,full));
            }
        }else{
            sb.append("NO SCRIPTS\n");
        }
        if(!roles.isEmpty()){
            sb.append("ROLES\n");
            for(String roleName : roles.keySet()){
                sb.append("  "+roleName+"\n");
                Role role = roles.get(roleName);
                if(!role.getHosts().isEmpty()){
                    sb.append("    HOSTS\n");
                    role.getHosts().forEach(host->{
                        sb.append("      "+host.toString()+"\n");
                    });
                }else{
                    sb.append("    NO HOSTS\n");
                }
                printScripts.accept("SETUP",role.getSetup());
                printScripts.accept("RUN",role.getRun());
                printScripts.accept("CLEANUP",role.getCleanup());
            }
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

    public int getTimeout() {
        return timeout;
    }

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

}
