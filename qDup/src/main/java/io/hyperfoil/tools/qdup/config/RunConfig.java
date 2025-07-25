package io.hyperfoil.tools.qdup.config;

import io.hyperfoil.tools.qdup.*;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Script;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import io.hyperfoil.tools.qdup.config.yaml.YamlFile;
import io.hyperfoil.tools.yaup.Counters;
import io.hyperfoil.tools.yaup.json.Json;
import org.jboss.logging.Logger;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Immutable representation of the Configuration for the Run
 * This includes all Hosts, Scripts, and State
 */
public class RunConfig {


    public static final String TRACE_NAME = "TRACE_NAME";

    public static final String MAKE_TEMP_KEY = "MKTEMP";
    public static final String REMOVE_TEMP_KEY = "RMTEMP";

    private final static Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass());

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

    private Map<String, Script> scripts;
    private State state;

    private Map<String,Role> roles;

    private Counters<String> signalCounts;

    private Boolean colorTerminal = false;
    private List<RunError> errors;

    private List<Stage> skipStages;

    private Set<String> tracePatterns;

    private int timeout = 10;
    private boolean streamLogging;
    private Globals globals;
    private String consoleFormatPattern;

    protected RunConfig(
            String name,
            List<RunError> errors,
            Map<String,Script> scripts,
            State state,
            Counters<String> signalCounts,
            Map<String,Role> roles,
            String knownHosts,
            String identity,
            String passphrase,
            Integer timeout,
            Set<String> tracePatterns,
            List<Stage> skipStages,
            Globals globals,
            boolean streamLogging,
            String consoleFormatPattern){
        this.name = name;
        this.errors = errors;
        this.scripts = scripts;
        this.signalCounts = signalCounts;
        this.state = state;
        this.roles = roles;
        this.knownHosts = knownHosts;
        this.identity = identity;
        this.passphrase = passphrase;
        if ( timeout != null ) {
            this.timeout = timeout;
        }
        this.tracePatterns = new HashSet<>(tracePatterns);
        this.skipStages = skipStages;
        this.globals = globals;
        this.streamLogging = streamLogging;
        this.consoleFormatPattern = consoleFormatPattern;
    }

    public String getConsoleFormatPattern(){return consoleFormatPattern;}
    public boolean isStreamLogging(){return streamLogging;}
    public boolean hasSkipStages(){return !skipStages.isEmpty();}
    public List<Stage> getSkipStages(){return skipStages;}

    public Counters<String> getSignalCounts(){return signalCounts;}
    public Globals getGlobals(){return globals;}

    public Set<String> getTracePatterns(){return tracePatterns;}
    public Set<String> getRoleNames(){return roles.keySet();}
    public Map<String, Role> getRoles(){ return roles;}
    public Role getRole(String name){
        return roles.get(name);
    }
    public Collection<Role> getRolesValues(){return roles.values();}

    /**
     * Gets all the hosts that are used in a role
     * @return
     */
    public Set<Host> getAllHostsInRoles(){
        return roles.values().stream().flatMap(role->role.getDeclaredHosts().stream()).collect(Collectors.toSet());
    }




    public String debug(){
        return debug(false);
    }
    public String debug(boolean full){
        if(hasErrors()){
            return getErrors().stream().map(RunError::toString).collect(Collectors.joining("\n"));
        }
        StringBuilder  sb = new StringBuilder();
        BiConsumer<String,List<? extends Cmd>> printScripts = (phase, scripts)->{
            sb.append(String.format("    %s%n",phase));
            scripts.forEach(script->{
                sb.append(String.format("      %s%n",script.toString()));
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
                if(!role.getHosts(this).isEmpty()){
                    sb.append("    HOSTS\n");
                    role.getHosts(this).forEach(host->{
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
    public List<RunError> getErrors(){return Collections.unmodifiableList(errors);}
    public List<String> getErrorStrings(){
        return getErrors().stream().map(RunError::toString).collect(Collectors.toList());
    }

    public String getName(){return name;}
    public State getState(){return state;}

    public int getTimeout() {
        return timeout;
    }

    public Set<String> getScriptNames(){return scripts.keySet();}

    /**
     * get a script using the global state and no command variables
     * @param name name of script to retrieve
     * @return {@link io.hyperfoil.tools.qdup.cmd.Script} matching name
     */
    public Script getScript(String name){
        return getScript(name,null,state);
    }

    /**
     * get a script using the target state but no command variables
     * @param name name of script to retrieve
     * @param state target {@link io.hyperfoil.tools.qdup.State} to populate command
     * @return {@link io.hyperfoil.tools.qdup.cmd.Script} matching name and target {@link  io.hyperfoil.tools.qdup.State}
     */
    public Script getScript(String name,State state){
        return getScript(name,null,state);
    }

    /**
     * get a script using the target state and the commands variables
     * @param name name of script to retrieve
     * @param command {@link io.hyperfoil.tools.qdup.cmd.Cmd}
     * @param state target {@link io.hyperfoil.tools.qdup.State} to populate command
     * @return {@link io.hyperfoil.tools.qdup.cmd.Script} matching name, target {@link io.hyperfoil.tools.qdup.State} and {@link io.hyperfoil.tools.qdup.cmd.Cmd}
     */
    public Script getScript(String name,Cmd command,State state){

        String populatedName = Cmd.populateStateVariables(name,command,state,null,null);
        return scripts.get(populatedName);
    }

    /**
     * return a json representation of the current config
     * @return
     */
    public Json toJson(){
        Json rtrn = Json.fromString("{\"roles\":{}}");
        Parser p = Parser.getInstance();
        if(!getGlobals().getSettings().isEmpty()){
            rtrn.set("globals",getGlobals().getSettings());
        }
        getRoleNames().forEach(roleName->{
            Json roleJson = new Json();
            rtrn.getJson("roles").add(roleName,roleJson);
            Role role = getRole(roleName);
            roleJson.add("host",new Json(true));
            role.getHosts(this).forEach(host->{
                roleJson.getJson("host").add(host.toString());
            });
            if(!role.getSetup().isEmpty()){
                roleJson.add("setup-scripts",new Json(true));
                role.getSetup().forEach(cmd->{
                    roleJson.getJson("setup-scripts").add(cmd.toJson());
                });
            }
            if(!role.getRun().isEmpty()){
                roleJson.add("run-scripts",new Json(true));
                role.getRun().forEach(cmd->{
                    roleJson.getJson("run-scripts").add(cmd.toJson());
                });
            }
            if(!role.getCleanup().isEmpty()){
                roleJson.add("cleanup-scripts",new Json(true));
                role.getCleanup().forEach(cmd->{
                    roleJson.getJson("cleanup-scripts").add(cmd.toJson());
                });
            }
        });
        return rtrn;
    }
}
