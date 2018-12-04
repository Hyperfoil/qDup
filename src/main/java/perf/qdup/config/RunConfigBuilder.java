package perf.qdup.config;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import perf.qdup.Host;
import perf.qdup.State;
import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.CommandSummary;
import perf.qdup.cmd.Script;
import perf.qdup.cmd.impl.ScriptCmd;
import perf.yaup.HashedLists;
import perf.yaup.HashedSets;
import perf.yaup.json.Json;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static perf.qdup.cmd.Cmd.STATE_PREFIX;
import static perf.qdup.config.YamlParser.*;

public class RunConfigBuilder {

    private final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

    private static final Json EMPTY_ARRAY = new Json();

    private static final String NAME = "name";
    private static final String SCRIPTS = "scripts";
    private static final String ROLES = "roles";
    private static final String HOSTS = "hosts";
    private static final String STATES = "states";
    private static final String SETUP_SCRIPTS = "setup-scripts";
    private static final String RUN_SCRIPTS = "run-scripts";
    private static final String CLEANUP_SCRIPTS = "cleanup-scripts";

    public static final String ALL_ROLE = "ALL";
    private static final String RUN_STATE = "run";
    private static final String SCRIPT_DIR = "ENV.SCRIPT_DIR";

    public static final String DEFAULT_KNOWN_HOSTS = System.getProperty("user.home")+"/.ssh/known_hosts";
    public static final String DEFAULT_IDENTITY = System.getProperty("user.home")+"/.ssh/id_rsa";
    public static final String DEFAULT_PASSPHRASE = null;
    public static final int DEFAULT_SSH_TIMEOUT = 5;

    private static final String HOST_EXPRESSION_PREFIX = "=";
    private static final String HOST_EXPRESSING_INCLUDE = "+";
    private static final String HOST_EXPRESSION_EXCLUDE = "-";


    private String identity = DEFAULT_IDENTITY;
    private String knownHosts = DEFAULT_KNOWN_HOSTS;
    private String passphrase = DEFAULT_PASSPHRASE;

    private String name = null;
    private State state;

    private int timeout = DEFAULT_SSH_TIMEOUT;

    private HashMap<String,Script> scripts;

    private HashedSets<String,String> roleHosts;
    private HashedLists<String,ScriptCmd> roleSetup;
    private HashedLists<String,ScriptCmd> roleRun;
    private HashedLists<String,ScriptCmd> roleCleanup;

    private HashMap<String,String> roleHostExpression;

    private HashMap<String,Host> hostAlias;

    private HashMap<String,String> traceTargets;

    private CmdBuilder cmdBuilder;
    private List<String> errors;

    private boolean isValid = false;

    public RunConfigBuilder(CmdBuilder cmdBuilder){
        this("run-"+System.currentTimeMillis(),cmdBuilder);
    }
    public RunConfigBuilder(String name,CmdBuilder cmdBuilder){
        this.name = name;
        this.cmdBuilder = cmdBuilder;
        scripts = new HashMap<>();
        state = new State(null,State.RUN_PREFIX);
        roleHosts = new HashedSets<>();
        roleSetup = new HashedLists<>();
        roleRun = new HashedLists<>();
        roleCleanup = new HashedLists<>();
        roleHostExpression = new HashMap<>();
        hostAlias = new HashMap<>();
        traceTargets = new HashMap<>();
        errors = new LinkedList<>();
    }

    public void traceSession(String session,String path){
        traceTargets.put(session,path);
    }
    public Set<String> getTraceTargets(){return traceTargets.keySet();}
    public String getTracePath(String target){
        return traceTargets.get(target);
    }

    public void eachChildArray(Json target, BiConsumer<Integer,Json> consumer){

        Json childArray = target.getJson(CHILD, EMPTY_ARRAY);
        for (int childIndex = 0; childIndex < childArray.size(); childIndex++) {
            Json childEntry = childArray.getJson(childIndex);
            consumer.accept(childIndex,childEntry);
        }
    }
    public void eachChildEntry(Json target, BiConsumer<Integer,Json> consumer){

        Json childArray = target.getJson(CHILD,EMPTY_ARRAY);
        for(int childIndex=0; childIndex<childArray.size(); childIndex++){
            Json childEntry = childArray.getJson(childIndex,EMPTY_ARRAY);
            for(int entryIndx=0; entryIndx < childEntry.size(); entryIndx++){
                Json entry = childEntry.getJson(entryIndx,EMPTY_ARRAY);
                consumer.accept(childIndex,entry);
            }
        }
    }
    public Map<String,String> yamlChildMap(Json json){
        Map<String,String> rtrn = new LinkedHashMap<>();
        eachChildEntry(json,(i,childEntry)->{
            if(childEntry.has(KEY) && childEntry.has(VALUE)) {
                rtrn.put(childEntry.getString(KEY), childEntry.getString(VALUE));
            }

        });
        return rtrn;
    }


    public void addError(String error){
        errors.add(error);
    }
    public void addErrors(Collection<String> error){
        errors.addAll(error);
    }
    public int errorCount(){return errors.size();}

    public boolean loadYaml(YamlParser yamlParser) {
        boolean ok = true;
        if(yamlParser.hasErrors()){
            addErrors(yamlParser.getErrors());
            ok = false;
        }else {
            for(String yamlPath : yamlParser.fileNames()){
                Json yamlJson = yamlParser.getJson(yamlPath);
                boolean docOk = loadYamlJson(yamlJson,yamlPath);
                ok = ok && docOk;
            }
        }
        return ok;
    }
    public boolean loadYamlJson(Json yamlJson,String yamlPath){
        boolean ok = true;
        if (yamlJson.isArray()) {
            for (int i = 0; i < yamlJson.size(); i++) {
                Object yamlObj = yamlJson.get(i);
                if (yamlObj instanceof Json) {
                    Json yamlEntry = (Json) yamlObj;
                    String entryKey = yamlEntry.getString(KEY,"");
                    String entryValue = yamlEntry.getString(VALUE);
                    Json entryChildJson = yamlEntry.getJson(CHILD, EMPTY_ARRAY);
                    switch (entryKey) {
                        case NAME:
                            if (entryValue != null && !entryValue.isEmpty()) {
                                setName(entryValue);
                            }
                            break;
                        case SCRIPTS:
                            final List<String> scriptErrors = new LinkedList<>();
                            eachChildEntry(yamlEntry, (entryIndex, scriptEntry) -> {
                                String scriptName = scriptEntry.getString(KEY, "");

                                //Only accept the first definition so make sure the most important yaml is first :)
                                if(hasScript(scriptName)){
                                    logger.warn("{} tried to add script {} which already exists",yamlPath,scriptName);
                                }else {
                                    Script newScript = new Script(scriptName);
                                    File yamlFile = new File(yamlPath);
                                    String scriptDir = yamlFile.exists() && yamlFile.getParentFile()!=null ? yamlFile.getParentFile().getAbsolutePath() : yamlPath;
                                    newScript.with(SCRIPT_DIR,scriptDir);
                                    eachChildArray(scriptEntry, (commandIndex, scriptCommand) -> {
                                        Cmd childCmd = cmdBuilder.buildYamlCommand(scriptCommand, newScript, scriptErrors);
                                        newScript.then(childCmd);
                                    });
                                    addScript(newScript);
                                }
                            });
                            if(!scriptErrors.isEmpty()){
                                scriptErrors.forEach(s->{
                                    addError(String.format("Error: %s in %s",s,yamlPath));
                                });
                            }
                            break;
                        case ROLES:
                            eachChildEntry(yamlEntry, (entryIndex, roleEntry) -> {
                                String roleName = roleEntry.getString(KEY, "");

                                //roles merge so no warning if already defined

                                eachChildEntry(roleEntry, (sectionIndex, roleSection) -> {
                                    String sectionName = roleSection.getString(KEY, "");
                                    if (HOSTS.equals(sectionName)) {
                                        if(roleSection.has(VALUE)){
                                            String roleSectionValue = roleSection.getString(VALUE).trim();
                                            if(roleSectionValue.startsWith(HOST_EXPRESSION_PREFIX)) {
                                                setRoleHostExpession(roleName, roleSection.getString(VALUE, ""));
                                            }else{
                                                //assume the value is the only host
                                                //TODO add test for -hosts: hostName
                                                addHostToRole(roleName,roleSectionValue);
                                            }
                                        }
                                        eachChildEntry(roleSection, (hostIndex, host) -> {
                                            String hostReference = host.getString(KEY, "");
                                            if (hostReference.isEmpty()) {
                                                //TODO log error about parsing the host
                                            } else {
                                                addHostToRole(roleName, host.getString(KEY, ""));
                                            }
                                        });

                                    } else {
                                        eachChildEntry(roleSection, (scriptIndex, scriptRefernce) -> {
                                            String scriptName = scriptRefernce.getString(KEY);

                                            Map<String, String> scriptWiths = new LinkedHashMap<>();
                                            eachChildEntry(scriptRefernce, (childIndex, scriptChild) -> {
                                                String childName = scriptChild.getString(KEY);
                                                if (WITH.equalsIgnoreCase(childName)) {
                                                    Map<String, String> withs = yamlChildMap(scriptChild);
                                                    scriptWiths.putAll(withs);
                                                }
                                            });

                                            switch (sectionName) {
                                                case SETUP_SCRIPTS:
                                                    addRoleSetup(roleName, scriptName, scriptWiths);
                                                    break;
                                                case RUN_SCRIPTS:
                                                    addRoleRun(roleName, scriptName, scriptWiths);
                                                    break;
                                                case CLEANUP_SCRIPTS:
                                                    addRoleCleanup(roleName, scriptName, scriptWiths);
                                                    break;
                                            }
                                        });
                                    }
                                });
                            });

                            break;
                        case HOSTS:
                            eachChildEntry(yamlEntry, (hostIndex, host) -> {
                                String hostName = host.getString(KEY);
                                String hostValue = host.getString(VALUE, "");
                                if (hostValue.isEmpty()) {

                                    Map<String, String> hostMap = yamlChildMap(host);

                                    if (hostMap.containsKey("username") && hostMap.containsKey("hostname")) {
                                        String un = hostMap.get("username");
                                        String hn = hostMap.get("hostname");
                                        int port = hostMap.containsKey("port") ? Integer.parseInt(hostMap.get("port")) : Host.DEFAULT_PORT;
                                        hostValue = un+ "@" + hn + ":" + port;
                                    }else{

                                    }
                                }
                                if (!hostValue.isEmpty()) {

                                    addHostAlias(hostName, hostValue);
                                }
                            });
                            break;
                        case STATES:
                            eachChildEntry(yamlEntry, (stateIndex, stateJson) -> {
                                String stateName = stateJson.getString(KEY, "");
                                if(stateJson.has(VALUE)){
                                    String stateValue = stateJson.getString(VALUE);
                                    setRunState(stateName,stateValue);
                                }else {
                                    eachChildEntry(stateJson, (entryIndex, entry) -> {
                                        if (RUN_STATE.equals(stateName)) {
                                            if (entry.has(VALUE)){
                                                setRunState(entry.getString(KEY), entry.getString(VALUE));
                                            }else if (entry.has(CHILD)){
                                                String hostName = entry.getString(KEY);
                                                eachChildEntry(entry, (hostEntryIndex, hostEntry) -> {
                                                    if (!hostEntry.has(VALUE) && hostEntry.has(CHILD)) {
                                                        String scriptName = hostEntry.getString(KEY);
                                                        eachChildEntry(hostEntry, (scriptEntryIndex, scriptEntry) -> {
                                                            //TODO add script entry under host
                                                        });
                                                    } else {
                                                        //TODO add host entry
                                                    }
                                                });

                                            }else{
                                                logger.trace("setting empty value for state = {}",entry.getString(KEY));
                                                setRunState(entry.getString(KEY),"");
                                            }
                                        } else {
                                            setHostState(stateName, entry.getString(KEY), entry.getString(VALUE));
                                        }
                                    });
                                }
                            });
                            break;
                        default:
                            //umm...what is this?
                    }
                } else {
                    ok = false;
                    break;
                }
            }
        } else {
            ok = false;
        }

        return ok;
    }


    public void setName(String name){
        if(this.name == null) {
            this.name = name;
        }
    }

    public State getState(){return state;}

    public String getName(){return name;}

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public String getKnownHosts(){return knownHosts;}
    public void setKnownHosts(String knownHosts){
        this.knownHosts = knownHosts;
    }
    public String getIdentity(){return identity;}
    public void setIdentity(String identity){
        this.identity = identity;
    }
    public String getPassphrase(){return passphrase;}
    public void setPassphrase(String passphrase){
        this.passphrase = passphrase;
    }

    public void setRoleHostExpession(String roleName,String expression){
        roleHostExpression.put(roleName,expression);
    }
    public void addHostToRole(String name,String hostReference){
        roleHosts.put(name,hostReference);
    }
    public void addHostAlias(String alias,String host){
        Host newHost = Host.parse(host);
        if(newHost!=null) {
            hostAlias.put(alias, newHost);
            hostAlias.put(host, newHost);
            hostAlias.put(newHost.toString(), newHost);
        }else{
            addError("Failed to parse host "+alias+":"+host);
        }
    }

    public void addRoleSetup(String role, String script, Map<String,String> with){
        addRoleScript(role,script,with,roleSetup);
    }
    public void addRoleRun(String role, String script, Map<String,String> with){
        addRoleScript(role,script,with,roleRun);
    }
    public void addRoleCleanup(String role, String script, Map<String,String> with){
        addRoleScript(role,script,with,roleCleanup);
    }
    private void addRoleScript(String role,String script,Map<String,String> with,HashedLists<String,ScriptCmd> target){
        ScriptCmd cmd = Cmd.script(script);
        if(with!=null && !with.isEmpty()){
            cmd.with(with);
        }
        target.put(role,cmd);
    }

    public void forceRunState(String key,String value){
        state.set(key,value);
    }
    public void setRunState(String key,String value){
        if(!state.has(key)){
            state.set(key, value);
        } else {
            //TODO log the error
            addError(String.format("%s already set to %s then tried to set as %s",key,state.get(key),value));
        }
    }
    public void setHostState(String host,String key,String value){
        State target = state.getChild(host,State.HOST_PREFIX);
        if(!target.has(key)){
            target.set(key,value);
        }else{

        }
        state.getChild(host,State.HOST_PREFIX).set(key,value);
    }
    public boolean addScript(Script script){
        if(scripts.containsKey(script.getName())){
            return false;
        }else{
            scripts.put(script.getName(),script);
        }
        return true;
    }
    public boolean hasScript(String name){
        return scripts.containsKey(name);
    }
    public Script getScript(String name){
        return getScript(name,null);
    }
    public Script getScript(String name, Cmd command){
        if(name.contains(STATE_PREFIX)){
            name = Cmd.populateStateVariables(name,command,state);
        }
        Script script = scripts.get(name);
        if(script==null){ // we don't find it
        }
        return script;
    }
    public RunConfig buildConfig(){
        Map<String,Host> seenHosts = new HashMap<>();
        Map<String,Role> roles = new HashMap<>();

        //build map of all known hosts
        roleHosts.forEach((roleName,hostSet)->{
            for(String hostShortname : hostSet){
                Host resolvedHost = hostAlias.get(hostShortname);
                if(resolvedHost!=null){
                    seenHosts.putIfAbsent(hostShortname,resolvedHost);
                    seenHosts.put(resolvedHost.toString(),resolvedHost);//could duplicate fullyQualified but guarantees to include port
                }else{
                    addError("Role "+roleName+" Host "+hostShortname+" was added without a fully qualified host representation matching user@hostName:port\n hosts:"+hostAlias);
                    //WTF, how are we missing a host reference?
                }
            }
        });

        //ALL_ROLE automatically includes all the hosts in use for any role
        Set<Host> uniqueHosts = new HashSet<>(seenHosts.values());
        roleHosts.putAll(ALL_ROLE,uniqueHosts.stream().map(Host::toString).collect(Collectors.toList()));

        //roleHostExpessions
        roleHostExpression.forEach((roleName,expession)->{
            List<String> split = CmdBuilder.split(expession);
            Set<Host> toAdd = new HashSet<>();
            Set<Host> toRemove = new HashSet<>();

            for(int i=0; i<split.size(); i++){
                String token = split.get(i);
                if( token.equals(HOST_EXPRESSION_PREFIX) || token.equals(HOST_EXPRESSING_INCLUDE) ){
                    if( i + 1 < split.size() ) {
                        toAdd.addAll(roleHosts.get(split.get(i+1)).stream().map(seenHosts::get).collect(Collectors.toList()));
                        i++;
                    }else{
                        //how does the expression end with an = or +?
                        addError("host expresion for "+roleName+" should not end with "+token);
                    }
                } else if (token.equals(HOST_EXPRESSION_EXCLUDE)){
                    if( i + 1 < split.size() ) {
                        toRemove.addAll(roleHosts.get(split.get(i+1)).stream().map(seenHosts::get).collect(Collectors.toList()));
                        i++;
                    }else{
                        //how does an expression end with -
                        addError("host expresion for "+roleName+" should not end with "+token);
                    }
                }else{
                    addError("host expressions should be = <role> [+-] <role>... but "+roleName+" could not parse "+token+" in: "+expession);
                }
            }

            toAdd.removeAll(toRemove);

            if(!toAdd.isEmpty()){
                roleHosts.putAll(roleName,toAdd.stream().map(Host::toString).collect(Collectors.toList()));
            }else{

            }
        });

        //unique roles with hosts
        Set<String> roleNames = new HashSet<>();
        roleNames.addAll(roleHosts.keys());
        roleNames.addAll(roleHostExpression.keySet());

        if(roleNames.isEmpty()){
            addError(String.format("No hosts defined for roles"));
        }
        //create roles
        roleNames.forEach(roleName->{
            roles.putIfAbsent(roleName,new Role(roleName));
            roleHosts.get(roleName).forEach(hostRef->{
                Host host = seenHosts.get(hostRef);
                if(host!=null){
                    roles.get(roleName).addHost(host);
                }else{
                    //TODO error, missing host definition or assume fully qualified name?
                }

            });
        });
        roleSetup.forEach((roleName,cmds)->{
            if(!cmds.isEmpty()){
                if(!roles.containsKey(roleName)){
                    //TODO add error if a role has a setup script but not a host
                }else{
                    cmds.forEach(cmd->roles.get(roleName).addSetup(cmd));
                }
            }
        });
        roleRun.forEach((roleName,cmds)->{
            if(!cmds.isEmpty()){
                if(!roles.containsKey(roleName)){
                    //TODO add error if a role has a run script but not a host
                }else{
                    cmds.forEach(cmd->roles.get(roleName).addRun(cmd));
                }
            }
        });
        roleCleanup.forEach((roleName,cmds)->{
            if(!cmds.isEmpty()){
                if(!roles.containsKey(roleName)){
                    //TODO add error if a role has a setup script but not a host
                }else{
                    cmds.forEach(cmd->roles.get(roleName).addCleanup(cmd));
                }
            }
        });

        //check signal / wait-for
        StageSummary setupStage = new StageSummary();
        StageSummary runStage = new StageSummary();
        StageSummary cleanupStage = new StageSummary();

        BiConsumer<StageSummary,Cmd> addCmd = (stage,cmd)->{
            CommandSummary summary = new CommandSummary(cmd,this);
            stage.add(summary);
        };


        roles.values().forEach(role->{
            role.getHosts().forEach(host->{
                role.getSetup().forEach(c->addCmd.accept(setupStage,c));
                role.getRun().forEach(c->addCmd.accept(runStage,c));
                role.getCleanup().forEach(c->addCmd.accept(cleanupStage,c));
            });

        });

        setupStage.getErrors().forEach(this::addError);
        runStage.getErrors().forEach(this::addError);
        cleanupStage.getErrors().forEach(this::addError);

        if(errorCount() > 0){
            return new RunConfig(getName(),errors);
        }else {
            return new RunConfig(
                    getName(),
                    scripts,
                    state,
                    roles,
                    setupStage,
                    runStage,
                    cleanupStage,
                    getKnownHosts(),
                    getIdentity(),
                    getPassphrase(),
                    timeout
            );
        }
    }

}
