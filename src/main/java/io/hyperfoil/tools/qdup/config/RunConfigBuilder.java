package io.hyperfoil.tools.qdup.config;

import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.Stage;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Script;
import io.hyperfoil.tools.qdup.cmd.impl.ScriptCmd;
import io.hyperfoil.tools.qdup.config.rule.NonObservingCommands;
import io.hyperfoil.tools.qdup.config.rule.SignalCounts;
import io.hyperfoil.tools.qdup.config.rule.UndefinedStateVariables;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import io.hyperfoil.tools.qdup.config.yaml.YamlFile;
import io.hyperfoil.tools.yaup.HashedLists;
import io.hyperfoil.tools.yaup.HashedSets;
import io.hyperfoil.tools.yaup.PopulatePatternException;
import io.hyperfoil.tools.yaup.StringUtil;
import io.hyperfoil.tools.yaup.json.Json;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class RunConfigBuilder {

   private static final List<Stage> SKIPABLE_STAGES = Arrays.asList(Stage.Setup,Stage.Run,Stage.Cleanup);

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
   public static final String SCRIPT_DIR = "ENV.SCRIPT_DIR";

   public static final String TEMP_DIR = "ENV.TEMP_DIR";
   public static final String MAKE_TEMP_CMD = "mktemp -d -t qdup-XXXXXXXXXX";
   public static final String REMOVE_TEMP_CMD = "rm -r";

   public static final String DEFAULT_KNOWN_HOSTS = System.getProperty("user.home") + "/.ssh/known_hosts";
   public static final String DEFAULT_IDENTITY = System.getProperty("user.home") + "/.ssh/id_rsa";
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

   private HashMap<String, Script> scripts;

   private HashedSets<String, String> roleHosts;
   private HashedLists<String, ScriptCmd> roleSetup;
   private HashedLists<String, ScriptCmd> roleRun;
   private HashedLists<String, ScriptCmd> roleCleanup;

   private HashMap<String, String> roleHostExpression;

   private HashMap<String, String> hostAlias;

   private Set<String> traceTargets;
   private Json settings;

   private List<String> errors;
   private List<Stage> skipStages;

   private boolean isValid = false;

   public RunConfigBuilder() {
      this("run-" + System.currentTimeMillis());
   }

   public RunConfigBuilder(String name) {
      this.name = name;
      scripts = new LinkedHashMap<>();
      state = new State(State.RUN_PREFIX);
      roleHosts = new HashedSets<>();
      roleSetup = new HashedLists<>();
      roleRun = new HashedLists<>();
      roleCleanup = new HashedLists<>();
      roleHostExpression = new HashMap<>();
      hostAlias = new HashMap<>();
      traceTargets = new HashSet<>();
      errors = new LinkedList<>();
      settings = new Json(false);
      settings.set(RunConfig.MAKE_TEMP_KEY,MAKE_TEMP_CMD);
      settings.set(RunConfig.REMOVE_TEMP_KEY,REMOVE_TEMP_CMD);
      skipStages = new ArrayList<>();
   }


   public void trace(String pattern) {
      traceTargets.add(pattern);
   }

   public Set<String> getTracePatterns() {
      return traceTargets;
   }

   public void addError(String error) {
      errors.add(error);
   }

//   public void addErrors(Collection<String> error) {
//      errors.addAll(error);
//   }

   public void addSkipStage(Stage stage){
      if(SKIPABLE_STAGES.contains(stage)) {
         skipStages.add(stage);
      }
   }


   public int errorCount() {
      return errors.size();
   }

   public boolean loadYaml(YamlFile yamlFile) {
      if (yamlFile == null) {
         return false;
      }
      getState().merge(yamlFile.getState());

      yamlFile.getScripts().forEach((name, script) -> {
         addScript(script);
      });
      yamlFile.getHosts().forEach((name, host) -> {
         if (hostAlias.containsKey(name) && !hostAlias.get(name).equals(host)) {
            addError(String.format("%s tried to load host $s:$s but already defined as $s", yamlFile.getPath(), name, host, hostAlias.get(name)));
         } else {
            hostAlias.put(name, host);
         }
      });
      yamlFile.getRoles().forEach((name, role) -> {
         if(role.hasHostExpression()){
            setRoleHostExpession(name,role.getHostExpression().getExpression());
         }
         role.getHostRefs().forEach(hostRef -> {
            addHostToRole(name, hostRef);
         });
         role.getSetup().forEach(cmd -> {
            addRoleSetup(name, ((ScriptCmd) cmd).getName(), cmd.getWith());
         });
         role.getRun().forEach(cmd -> {
            addRoleRun(name, ((ScriptCmd) cmd).getName(), cmd.getWith());
         });
         role.getCleanup().forEach(cmd -> {
            addRoleCleanup(name, ((ScriptCmd) cmd).getName(), cmd.getWith());
         });
      });
      yamlFile.getSettings().forEach((k, v) -> {
         if (settings.has(k)) {
            //TODO alert settings are already set?
         } else {
            settings.set(k, v);
         }
      });
      return errors.isEmpty();
   }

   public String getPathDirectory(String yamlPath) {
      File file = new File(yamlPath);
      String rtrn = yamlPath;
      if (file.exists()) {
         rtrn = FileSystems.getDefault().getPath(yamlPath).normalize().toAbsolutePath().getParent().toString();
      }
      return rtrn;
   }

   public void setName(String name) {
      if (this.name == null) {
         this.name = name;
      }
   }

   public State getState() {
      return state;
   }

   public String getName() {
      return name;
   }

   public int getTimeout() {
      return timeout;
   }

   public YamlFile toYamlFile() {
      YamlFile rtrn = new YamlFile();
      rtrn.setName(getName());

      getHosts().forEach(rtrn::addHost);
      scripts.forEach(rtrn::addScript);
      getRoleNames().forEach(roleName -> {
         rtrn.addRole(roleName, new Role(roleName));
         Role role = rtrn.getRoles().get(roleName);
         if (roleHostExpression.containsKey(roleName)) {
            role.setHostExpression(new HostExpression(roleHostExpression.get(roleName)));
         }
         getRoleHosts(roleName).forEach(role::addHostRef);
         getRoleSetup(roleName).forEach(role::addSetup);
         getRoleRun(roleName).forEach(role::addRun);
         getRoleCleanup(roleName).forEach(role::addCleanup);
      });
      rtrn.getState().merge(getState());

      return rtrn;
   }

   private Set<String> getRoleNames() {
      Set<String> rtrn = new HashSet<>();
      rtrn.addAll(roleHostExpression.keySet());
      rtrn.addAll(roleHosts.keys());
      rtrn.addAll(roleSetup.keys());
      rtrn.addAll(roleRun.keys());
      rtrn.addAll(roleCleanup.keys());

      return rtrn;
   }

   private List<ScriptCmd> getRoleSetup(String name) {
      return roleSetup.containsKey(name) ? roleSetup.get(name) : Collections.emptyList();
   }

   private List<ScriptCmd> getRoleRun(String name) {
      return roleRun.containsKey(name) ? roleRun.get(name) : Collections.emptyList();
   }

   private List<ScriptCmd> getRoleCleanup(String name) {
      return roleCleanup.containsKey(name) ? roleCleanup.get(name) : Collections.emptyList();
   }

   private Map<String, String> getHosts() {
      return Collections.unmodifiableMap(hostAlias);
   }

   private Set<String> getRoleHosts(String name) {
      return roleHosts.has(name) ? roleHosts.get(name) : Collections.emptySet();
   }

   public void setTimeout(int timeout) {
      this.timeout = timeout;
   }

   public String getKnownHosts() {
      return knownHosts;
   }


   public void setKnownHosts(String knownHosts) {
      this.knownHosts = knownHosts;
   }

   public String getIdentity() {
      return identity;
   }

   public void setIdentity(String identity) {
      this.identity = identity;
   }

   public String getPassphrase() {
      return passphrase;
   }


   public void setPassphrase(String passphrase) {
      this.passphrase = passphrase;
   }

   private void setRoleHostExpession(String roleName, String expression) {
      roleHostExpression.put(roleName, expression);
   }

   public void addHostToRole(String name, String hostReference) {
      roleHosts.put(name, hostReference);
   }

   public void addHostAlias(String alias, String host) {
      hostAlias.putIfAbsent(alias, host);
   }

   public void addRoleSetup(String role, String script, Map<String, String> with) {
      addRoleScript(role, script, with, roleSetup);
   }

   public void addRoleRun(String role, String script, Map<String, String> with) {
      addRoleScript(role, script, with, roleRun);
   }

   public void addRoleCleanup(String role, String script, Map<String, String> with) {
      addRoleScript(role, script, with, roleCleanup);
   }

   private void addRoleSetup(String role, String script, Json with) {
      addRoleScript(role, script, with, roleSetup);
   }

   private void addRoleRun(String role, String script, Json with) {
      addRoleScript(role, script, with, roleRun);
   }

   private void addRoleCleanup(String role, String script, Json with) {
      addRoleScript(role, script, with, roleCleanup);
   }

   private void addRoleScript(String role, String script, Json with, HashedLists<String, ScriptCmd> target) {
      ScriptCmd cmd = Cmd.script(script);
      if (with != null && !with.isEmpty()) {
         cmd.with(with);
      }
      target.put(role, cmd);
   }

   private void addRoleScript(String role, String script, Map<String, String> with, HashedLists<String, ScriptCmd> target) {
      ScriptCmd cmd = Cmd.script(script);
      if (with != null && !with.isEmpty()) {
         cmd.with(with);
      }
      target.put(role, cmd);
   }

   public void forceRunState(String key, Object value) {
      state.set(key, value);
   }

   public void setRunState(String key, Object value) {
      if (value instanceof Json && ((Json) value).isEmpty()) {
         return;
      }
      if (value instanceof String && ((String) value).isEmpty()) {
         return;
      }
      if (!state.has(key)) {
         state.set(key, value);
      } else {
         //TODO log the error
         addError(String.format("%s already set to %s then tried to set as %s", key, state.get(key), value));
      }
   }

   public void setHostState(String host, String key, String value) {
      if (key == null || value == null || host == null) {
         return;
      }
      State target = state.getChild(host, State.HOST_PREFIX);
      if (!target.has(key)) {
         target.set(key, value);
      }
      state.getChild(host, State.HOST_PREFIX).set(key, value);
   }

   public boolean addScript(Script script) {
      if (scripts.containsKey(script.getName())) {
         return false;
      } else {
         scripts.put(script.getName(), script);
      }
      return true;
   }

   @SuppressWarnings("WeakerAccess")
   public boolean hasScript(String name) {
      return scripts.containsKey(name);
   }

   public Script getScript(String name) {
      return getScript(name, null);
   }

   public Script getScript(String name, Cmd command) {
      if (Cmd.hasStateReference(name,command)) {
         name = Cmd.populateStateVariables(name, command, state, null,null);
      }
      Script script = scripts.get(name);
      if (script == null) { // we don't find it
      }
      return script;
   }

   public RunConfig buildConfig(){
      return buildConfig(Parser.getInstance());
   }
   public RunConfig buildConfig(Parser yamlParser) {
      Map<String, Host> seenHosts = new HashMap<>();
      Map<String, Role> roles = new HashMap<>();

      //build a Map of alias to Host
      Map<Object, Object> map = getState().toMap();
      Map<String, Host> hostAliases = new HashMap<>();

      for (String alias : hostAlias.keySet()) {
         String value = hostAlias.get(alias);
         String populatedValue = null;
         try {
            populatedValue = StringUtil.populatePattern(value, map);
         } catch (PopulatePatternException e) {
            logger.warn(e.getMessage());
            populatedValue = "";
         }
         if (Cmd.hasStateReference(populatedValue,null)) {
            addError("cannot create host from " + value + " -> " + populatedValue);
         }
         Host host = Host.parse(populatedValue);
         if (host == null) {
            addError("cannot create host from " + value + " -> " + populatedValue);
         }
         hostAliases.put(alias, host);
      }

      //build map of all known hosts
      roleHosts.forEach((roleName, hostSet) -> {
         for (String hostShortname : hostSet) {
            Host resolvedHost = hostAliases.get(hostShortname);
            if (resolvedHost != null) {
               seenHosts.putIfAbsent(hostShortname, resolvedHost);
               seenHosts.put(resolvedHost.toString(), resolvedHost);//could duplicate fullyQualified but guarantees to include port
            } else {
               if(!Cmd.hasStateReference(hostShortname,null)){
                  addError("Role " + roleName + " Host " + hostShortname + " was added without a fully qualified host representation matching user@hostName:port\n hosts:" + hostAlias);
               }else{

               }
               //WTF, how are we missing a host reference?
            }
         }
      });

      //ALL_ROLE automatically includes all the hosts in use for any role
      Set<Host> uniqueHosts = new HashSet<>(seenHosts.values());
      roleHosts.putAll(ALL_ROLE, uniqueHosts.stream().map(Host::toString).collect(Collectors.toList()));

      //roleHostExpessions
      roleHostExpression.forEach((roleName, expession) -> {
         List<String> split = Parser.split(expession);
         Set<Host> toAdd = new HashSet<>();
         Set<Host> toRemove = new HashSet<>();

         for (int i = 0; i < split.size(); i++) {
            String token = split.get(i);
            if (token.equals(HOST_EXPRESSION_PREFIX) || token.equals(HOST_EXPRESSING_INCLUDE)) {
               if (i + 1 < split.size()) {
                  toAdd.addAll(roleHosts.get(split.get(i + 1)).stream().map(seenHosts::get).collect(Collectors.toList()));
                  i++;
               } else {
                  //how does the expression end with an = or +?
                  addError("host expression for " + roleName + " should not end with " + token);
               }
            } else if (token.equals(HOST_EXPRESSION_EXCLUDE)) {
               if (i + 1 < split.size()) {
                  toRemove.addAll(roleHosts.get(split.get(i + 1)).stream().map(seenHosts::get).collect(Collectors.toList()));
                  i++;
               } else {
                  //how does an expression end with -
                  addError("host expression for " + roleName + " should not end with " + token);
               }
            } else {
               if(Cmd.hasStateReference(token,null)){
                  String populatedToken = Cmd.populateStateVariables(token,null,getState(),null,new Json());
                  if(Cmd.hasStateReference(populatedToken,null)){
                     addError("could not fully populate pattern for hosts: "+token);
                  }else {
                     if (Json.isJsonLike(populatedToken)) {
                        Json tokenJson = Json.fromString(populatedToken);
                        if (tokenJson != null) {
                           if (tokenJson.isArray()) {
                              tokenJson.forEach(entry -> {
                                 String str = entry.toString();
                                 if(hostAliases.containsKey(str)){
                                    toAdd.add(hostAliases.get(str));
                                 }else if (Host.parse(str)!=null){
                                    toAdd.add(Host.parse(str));
                                 }
                              });
                           }
                        }
                     } else if (hostAliases.containsKey(populatedToken)) {
                        toAdd.add(hostAliases.get(populatedToken));
                     } else if (Host.parse(populatedToken) != null) {
                        toAdd.add(Host.parse(populatedToken));
                     } else {
                        addError("could not identify a host from "+populatedToken);
                     }
                  }
               }else {
                  addError("host expressions should be = <role> [+-] <role>... or a ${{}} pattern but " + roleName + " could not parse " + token + " in: " + expession);
               }
            }
         }
         toAdd.removeAll(toRemove);
         if (!toAdd.isEmpty()) {
            roleHosts.putAll(roleName, toAdd.stream().map(Host::toString).collect(Collectors.toList()));
            toAdd.forEach(host->seenHosts.putIfAbsent(host.toString(),host));
         } else {
            addError(roleName+" did not create a host from "+expession);
         }
      });

      //unique roles with hosts
      Set<String> roleNames = new HashSet<>();
      roleNames.addAll(roleHosts.keys());
      roleNames.addAll(roleHostExpression.keySet());

      if (roleNames.isEmpty()) {
         addError(String.format("No hosts defined for roles"));
      }
      //create roles
      roleNames.forEach(roleName -> {
         roles.putIfAbsent(roleName, new Role(roleName));
         roleHosts.get(roleName).forEach(hostRef -> {
            if(Cmd.hasStateReference(hostRef,null)){
               hostRef = Cmd.populateStateVariables(hostRef,null,state,null,new Json());
               if(Cmd.hasStateReference(hostRef,null)){
                  addError("could not populate "+roleName+" host from "+hostRef);
               }
            }
            Host host = seenHosts.get(hostRef);
            if (host != null) {
               roles.get(roleName).addHost(host);
            } else {
               List<String> hostRefs = new ArrayList<>();
               if(Json.isJsonLike(hostRef)){
                  Json json = Json.fromString(hostRef);
                  if(json.isArray()){
                     json.values().forEach(v->hostRefs.add(v.toString()));
                  }else{
                     hostRefs.add(hostRef);
                  }
               }else{
                  hostRefs.add(hostRef);
               }
               hostRefs.forEach(ref->{
                  if(hostAliases.containsKey(ref)){
                     Host newHost = hostAliases.get(ref);
                     if(newHost!=null){
                        seenHosts.putIfAbsent(ref,newHost);
                        roles.get(roleName).addHost(newHost);
                     }else{
                        addError("could not load "+roleName+" host from alias "+ref);
                     }
                  }else if (Host.parse(ref)!=null){
                     Host newHost = Host.parse(ref);
                     seenHosts.putIfAbsent(ref,newHost);
                     roles.get(roleName).addHost(newHost);
                  }else {
                     addError("missing host for " + ref);
                  }
               });
            }
         });
      });
      roleSetup.forEach((roleName, cmds) -> {
         if (!cmds.isEmpty()) {
            if (!roles.containsKey(roleName)) {
               //TODO add error if a role has a setup script but not a host
            } else {
               cmds.forEach(cmd -> roles.get(roleName).addSetup(cmd));
            }
         }
      });
      roleRun.forEach((roleName, cmds) -> {
         if (!cmds.isEmpty()) {
            if (!roles.containsKey(roleName)) {
               //TODO add error if a role has a run script but not a host
            } else {
               cmds.forEach(cmd -> roles.get(roleName).addRun(cmd));
            }
         }
      });
      roleCleanup.forEach((roleName, cmds) -> {
         if (!cmds.isEmpty()) {
            if (!roles.containsKey(roleName)) {
               //TODO add error if a role has a setup script but not a host
            } else {
               cmds.forEach(cmd -> roles.get(roleName).addCleanup(cmd));
            }
         }
      });

      //perform static analysis
      RunSummary summary = new RunSummary();
      SignalCounts signalCounts = new SignalCounts();
      summary.addRule("signals",signalCounts);
      summary.addRule("variables",new UndefinedStateVariables(yamlParser));
      summary.addRule("observers",new NonObservingCommands());
      summary.scan(roles.values(),this);

      if(!errors.isEmpty()){
         errors.forEach(error->{
            summary.addError("", Stage.Pending,"","",error);
         });
      }
         return new RunConfig(
            getName(),
            summary.getErrors(),
            scripts,
            state,
            signalCounts.getCounts(),
            roles,
            getKnownHosts(),
            getIdentity(),
            getPassphrase(),
            timeout,
            getTracePatterns(),
            skipStages,
            settings
         );
   }

}
