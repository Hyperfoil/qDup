package perf.ssh.config;

import org.yaml.snakeyaml.Yaml;
import perf.ssh.*;
import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.Script;
import perf.ssh.cmd.impl.*;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;


public class YamlLoader {

    private RunConfig runConfig;
    private List<String> errors;
    private Yaml yaml;

    private Function<Object,Host> hostRef = (Object value) ->{
        Host rtrn = null;
        if(value instanceof String){
            String hostString = (String)value;
            rtrn = runConfig.getHost(hostString);
            if(rtrn==null){
                if(hostString.contains("@")){
                    String username = hostString.substring(0,hostString.indexOf("@"));
                    String hostname = hostString.substring(hostString.indexOf("@")+1);
                    int port = 22;
                    if(hostname.contains(":")){
                        port = Integer.parseInt(hostname.substring(hostname.indexOf(":")+1));
                        hostname = hostname.substring(0,hostname.indexOf(":"));
                    }
                    rtrn = new Host(username,hostname,port);
                }
            }
        }else if (value instanceof Map){
            Map map = (Map)value;
            Object username = map.get("username");
            Object hostname = map.get("hostname");
            int port = map.containsKey("port") ? Integer.parseInt(map.get("port").toString()) : 22;
            rtrn = new Host(username.toString(),hostname.toString(),port);

        }
        return rtrn;
    };


    public YamlLoader(){
        yaml = new Yaml();
        runConfig = new RunConfig();
        errors = new LinkedList<>();
    }

    public boolean hasErrors(){
        return !errors.isEmpty();
    }
    public List<String> getErrors(){
        return Collections.unmodifiableList(errors);
    }

    public RunConfig getRunConfig() {
        return runConfig;
    }

    public String dump(){
        StringBuilder sb = new StringBuilder();
        sb.append("hosts:\n");
        for(String host : runConfig.getHostsInRole().toList()){
            sb.append("  "+runConfig.getHost(host));
            sb.append(System.lineSeparator());
        }
        sb.append("scripts:\n");
        for(String scriptName : runConfig.getRepo().getNames()){
            sb.append("  "+scriptName+"\n");
            sb.append(runConfig.getScript(scriptName).tree(4,false));
        }
        sb.append("roles:\n");
        for(String roleName : runConfig.getRoleNames()){
            sb.append("  "+roleName+"\n");
            HostList role = runConfig.getRole(roleName);
            for(String h : role.toList()){
                sb.append("    "+h+"\n");
                List<Script> runScripts = runConfig.getRunScripts(h);
                runScripts.forEach((runScript)->{
                    sb.append("      "+runScript.getName()+"\n");
                });
            }
        }
        sb.append("state:\n");
        runConfig.getState().tree(2,sb);
        return sb.toString();
    }

    public void load(String yamlPath){
        try {
            load( yamlPath, new FileReader(yamlPath) );
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            errors.add("failed to load "+yamlPath);
        }
    }

    public void load(String fileName, Reader yamlReader){
        for(Object obj : yaml.loadAll(yamlReader)){
            readObject(obj,fileName);
        }
    }

    private void loadScriptCategory(String key,Object scriptObject,Consumer<String> addScript){
        if(scriptObject instanceof List){
            List scriptList = (List)scriptObject;
            for(Object scriptEntry : scriptList){
                if(scriptEntry instanceof String){
                    addScript.accept(scriptEntry.toString());
                    //runConfig.getHostsInRole().addSetupScript( runConfig.getScript(setupEntry.toString()) );
                }else if (scriptEntry instanceof Map){
                    //TODO implement setup-scripts selection
                    Map entryMap = (Map)scriptEntry;
                    Set<Object> unknown = new HashSet<>(entryMap.keySet());
                    unknown.removeAll(Arrays.asList("select","script"));
                    if(!unknown.isEmpty()){
                        errors.add(key+" entry expects {select:,script:} but saw: "+unknown);
                    }
                }else{
                    errors.add(key+" expects a list of scriptname or {select:,script:} but one entry was: "+scriptObject);
                }
            }
        }else{
            errors.add(key+" expects a list of scriptname or {select:,script:} but saw: "+scriptObject);
        }

    }
    private void readObject(Object obj,String filePath){
        if(obj instanceof Map){
            Map map = (Map)obj;
            for(Object key : map.keySet()){
                String keyString = key.toString();
                switch(keyString){
                    case "name":
                        Object name = map.get(key);
                        if(name == null || !(name instanceof String)){
                            errors.add(filePath+" name needs to be a string");
                        }else {
                            runConfig.setName(name.toString());
                        }
                        break;
                    case "scripts":
                        Object scripts = map.get(key);
                        if(scripts instanceof Map){
                            Map scriptsMap = (Map)scripts;
                            iterateScripts(scriptsMap);
                        }else{
                            errors.add(filePath+" scripts must contain a map where the key is the name of the script and the value is a list of commands");
                        }
                        break;
                    case "hosts":
                        Object hosts = map.get(key);
                        if(hosts instanceof Map){
                            Map hostMap = (Map)hosts;
                            for(Object hostName : hostMap.keySet()){
                                Object hostObj = hostMap.get(hostName);
                                if (hostObj instanceof String || hostObj instanceof Map) {
                                    Host h = hostRef.apply(hostObj);
                                    runConfig.addHost(hostName.toString(),h);
                                } else {
                                    errors.add(hostName+" expected String or {username:,hostname:,port:} but saw:"+hostObj);
                                }
                            }
                        }else{
                            errors.add("hosts expect a map with shortname : username@hostname:port or {username:,hostname:,port:} but saw: "+hosts);
                        }
                        break;
                    case "roles":
                        Object roles = map.get(key);
                        if(roles instanceof Map){
                            Map rolesMap = (Map)roles;
                            for(Object roleName : rolesMap.keySet()){
                                String roleNameString = roleName.toString();
                                Object roleObj = rolesMap.get(roleName);

                                Role role = runConfig.getRole(roleNameString);
                                if(roleObj instanceof Map){
                                    Map roleMap = (Map)roleObj;
                                    for(Object roleKey : roleMap.keySet()){
                                        Object roleValue = roleMap.get(roleKey);
                                        switch (roleKey.toString()){
                                            case "hosts":
                                                if(roleValue instanceof String){
                                                    role.add(roleValue.toString());
                                                } else if (roleValue instanceof Map){
                                                    Host h = hostRef.apply(roleValue);
                                                    if(h!=null){
                                                        //TODO runConfig addHost (make sure it already exists)
                                                        role.add(h.toString());
                                                    }else{
                                                        errors.add(roleNameString+" could not parse host : "+roleValue);
                                                    }
                                                }else if (roleValue instanceof List){

                                                    List roleHostList = (List)roleValue;
                                                    for(Object roleHostEntry : roleHostList){
                                                        if(roleHostEntry instanceof String ){
                                                            role.add(roleHostEntry.toString());
                                                        } else if (roleHostEntry instanceof Map){
                                                            Host h = hostRef.apply(roleValue);
                                                            if(h!=null){
                                                                //TODO runConfig addHost (make sure it already exists)
                                                                role.add(h.toString());
                                                            }else{
                                                                errors.add(roleNameString+" could not parse host : "+roleHostEntry);
                                                            }
                                                        }else{
                                                            errors.add(roleNameString+" could not parse host : "+roleHostEntry);
                                                        }
                                                    }
                                                }else{
                                                    errors.add(roleNameString+" could not parse host : "+roleValue);
                                                }
                                                break;
                                            case "run-scripts":
                                                if(roleValue == null){

                                                }else if(roleValue instanceof List){
                                                    List scriptList = (List)roleValue;
                                                    scriptList.forEach((scriptObj)->{
                                                        role.addRunScript(scriptObj.toString());
                                                    });
                                                }else if (roleValue instanceof String){
                                                    role.addRunScript(roleValue.toString());
                                                }else{
                                                    errors.add("run-scripts for "+roleNameString+" unexpected value :"+roleValue);
                                                }

                                                break;
                                            case "setup-scripts":
                                                if(roleValue == null) {

                                                }else if(roleValue instanceof List){
                                                    List scriptList = (List)roleValue;
                                                    scriptList.forEach((scriptObj)->{
                                                        role.addSetupScript(scriptObj.toString());
                                                    });
                                                }else if (roleValue instanceof String){
                                                    role.addSetupScript(roleValue.toString());
                                                }else{
                                                    errors.add("setup-scripts for "+roleNameString+" unexpected value :"+roleValue);
                                                }
                                                break;
                                            case "cleanup-scripts":
                                                if(roleValue == null){

                                                }else if(roleValue instanceof List){
                                                    List scriptList = (List)roleValue;
                                                    scriptList.forEach((scriptObj)->{
                                                        role.addCleanupScript(scriptObj.toString());
                                                    });
                                                }else if (roleValue instanceof String){
                                                    role.addCleanupScript(roleValue.toString());
                                                }else{
                                                    errors.add("cleanup-scripts for "+roleNameString+" unexpected value :"+roleValue);
                                                }
                                                break;
                                            default:
                                                errors.add(roleNameString+" has unknown property "+roleKey.toString()+" with value: "+roleValue);
                                        }
                                    }
                                }else{
                                    errors.add(roleNameString+" expects a map with {hosts,run-scripts,setup-scripts,cleanup-scripts} but saw: "+roleObj);
                                }

                            }
                        }else{
                            errors.add("roles expect a map with rolename : {role options} but saw "+roles);
                        }
                        break;
                    case "states":
                        Object statesObj = map.get(key);
                        if(statesObj instanceof Map){
                            Map statesMap = (Map)statesObj;
                            for(Object statesKey : statesMap.keySet()){
                                Object statesValue = statesMap.get(statesKey);
                                String statesKeyString = statesKey.toString();
                                switch (statesKeyString){
                                    case "run":
                                        if(statesValue instanceof Map){
                                            Map runMap = (Map)statesValue;
                                            for(Object runKey : runMap.keySet()){
                                                Object runValue = runMap.get(runKey);
                                                if(runValue==null){
                                                    runValue="";
                                                }
                                                try {
                                                    runConfig.getState().set(runKey.toString(), runValue.toString());
                                                }catch (RuntimeException e){
                                                    System.out.println(e.getMessage()+" runKey="+runKey+" runValue+"+runValue);
                                                    throw e;
                                                }
                                            }
                                        }else{
                                            errors.add("states: run: expects a map of {key : value} but saw "+statesValue);
                                        }
                                        break;
                                    case "host":
                                        if(statesValue instanceof Map){
                                            Map hostsMap = (Map)statesValue;
                                            for(Object hostName : hostsMap.keySet()){
                                                String hostNameString = hostName.toString();
                                                State hostState = runConfig.getState().addChild(hostNameString, State.HOST_PREFIX);
                                                Object hostValue = hostsMap.get(hostName);
                                                if(hostValue instanceof Map){
                                                    Map hostMap = (Map)hostValue;
                                                    for(Object hostEntry : hostMap.keySet()){
                                                        String hostEntryString = hostEntry.toString();
                                                        Object hostEntryValue = hostMap.get(hostEntry);
                                                        if(hostEntryValue instanceof Map && hostEntryString.equals("script")){//script
                                                            String scriptName = hostEntryString;
                                                            Map scriptMap = (Map)hostEntryValue;
                                                            for(Object scriptEntry : scriptMap.keySet()){
                                                                String scriptEntryString = scriptEntry.toString();
                                                                Object scriptEntryValue = scriptMap.get(scriptEntry);
                                                                State scriptState = hostState.addChild(scriptEntryString,null);
                                                                if(scriptEntryValue instanceof Map){
                                                                    Map scriptEntryMap = (Map)scriptEntryValue;
                                                                    for(Object scriptEntryKey : scriptEntryMap.keySet()){
                                                                        String scriptEntryKeyString = scriptEntryKey.toString();
                                                                        Object scriptEntryKeyValue = scriptEntryMap.get(scriptEntryKey);
                                                                        if(scriptEntryKeyValue instanceof String){
                                                                            scriptState.set(scriptEntryKeyString,(String)scriptEntryKeyValue);
                                                                        }else{
                                                                            errors.add(hostNameString+" > "+scriptEntryString+" > "+scriptEntryKeyString+" value unknown "+scriptEntryKeyValue);
                                                                        }
                                                                    }
                                                                }else{
                                                                    errors.add(hostNameString+" script: "+scriptName+" entry: "+scriptEntryString+" value unknown: "+scriptEntryValue);
                                                                }
                                                            }
                                                        }else if(hostEntryValue instanceof String){// key : value
                                                            hostState.set(hostEntryString,(String)hostEntryValue);
                                                        }else {
                                                            errors.add(hostNameString+" entry "+hostEntryString+" unknown value "+hostEntryValue);
                                                        }
                                                    }
                                                }
                                            }
                                        }else{
                                            errors.add("states: host: expects a map of {key : value} but saw "+statesValue);
                                        }
                                        break;
                                    default:
                                        errors.add("states expects a map {run:,host:} but saw :"+statesKeyString);
                                }
                            }
                        }else{
                            errors.add("states expects a map {run:,host:} but saw: "+statesObj);
                        }

                        break;
                    default:
                        errors.add("unknown category "+keyString);
                }
            }
        }
    }
    private void iterateScripts(Map scriptsMap){
        for(Object scriptsKey : scriptsMap.keySet()){

            String scriptName = scriptsKey.toString();

            Object scriptObject = scriptsMap.get(scriptsKey);

            Script script = runConfig.getRepo().getScript(scriptName);

            if(! (scriptObject instanceof List) ){
              errors.add(scriptName+" script needs to contain a list of commands");
            }else{
              List commandList = (List)scriptObject;
              buildScript(script,commandList);
            }
        }
    }
    private static Cmd parseCmd(Object input,Class<? extends Cmd> cmdClass,String...args) {
        final List supported = Arrays.asList("long","int","java.lang.String");
        Cmd rtrn = Cmd.NO_OP();
        try {
            Constructor constructor = null;
            Constructor constructors[] = cmdClass.getConstructors();
            for (int i = 0; i < constructors.length; i++) {
                Constructor c = constructors[i];

                if (c.getParameterCount() >= args.length) {
                    Type types[] = c.getParameterTypes();
                    boolean simple = types.length > 0 ? Arrays.asList(types).stream()
                            .map((t) -> t.getTypeName())
                            .map((s)->supported.contains(s))
                            .collect(Collectors.reducing(Boolean::logicalAnd))
                            .get() : true;
                    if(simple) {
                        constructor = c;
                    }
                }
            }

            final List<Object> varArgs = new LinkedList<>();
            if (input instanceof String) {
                if (args.length > 1) {
                    varArgs.addAll(Arrays.asList(((String) input).split(" ")));
                } else {
                    varArgs.add(input);
                }
            } else if (input instanceof List) {
                varArgs.addAll((List) input);
            } else if (input instanceof Map) {
                Map map = (Map) input;
                Arrays.asList(args).forEach((expected) -> {
                    Object inmap = map.get(expected);
                    if (inmap != null) {
                        varArgs.add(inmap);
                    }
                });
            } else if (input instanceof Number){
                varArgs.add(input);
            }

            Type types[] = constructor.getParameterTypes();
            for(int i=0; i<varArgs.size();i++){
                Type t = types[i];
                Object existing = varArgs.get(i);
                switch (t.getTypeName()){
                    case "long":
                        try {
                            if(! (existing instanceof Long) ){
                                varArgs.remove(i);
                                varArgs.add(i, Long.parseLong(existing.toString().replace("_", "")));
                            }
                        }catch(NumberFormatException nfe){
                            nfe.printStackTrace();
                        }
                        break;
                    case "int":
                        if(! (existing instanceof Integer) ) {
                            varArgs.remove(i);
                            varArgs.add(i,Integer.parseInt(existing.toString().replace("_","")));
                        }

                        break;
                    case "java.lang.String":
                        if(! (existing instanceof String) ) {
                            varArgs.remove(i);
                            varArgs.add(i,existing.toString());
                        }
                        break;
                    default:
                        varArgs.add(i,existing);
                }
            }
            for(int i = 0; i<types.length-varArgs.size(); i++){
                varArgs.add(null);
            }

            rtrn = (Cmd) constructor.newInstance(varArgs.toArray());
            return rtrn;
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return rtrn;
    }
    private Cmd getCmd(Map map){
        Cmd rtrn = Cmd.NO_OP();
        for(Object key : map.keySet()){
            String keyString = key.toString();
            Object value = map.get(key);
            switch (keyString){
                case "abort":
                    rtrn = parseCmd(value,Abort.class,"message");

                    break;
                case "code":
                    rtrn = parseCmd(value,CodeCmd.class,"className");

                    break;
                case "countdown":
                    rtrn = parseCmd(value,Countdown.class,"name","initial");
                    break;
                case "ctrlC":
                    rtrn = parseCmd(value,CtrlC.class);

                    break;
                case "download":
                    rtrn = parseCmd(value,Download.class,"path","destination");
                    break;
                case "upload":
                    rtrn = parseCmd(value,Upload.class,"path","destination");
                    break;
                case "echo":
                    rtrn = parseCmd(value,Echo.class);
                    break;
                case "invoke"://technically treating it the same as script: but not correct
                case "script":
                    rtrn = parseCmd(value,ScriptCmd.class,"className");
                    break;
                case "log":
                    rtrn = parseCmd(value,Log.class,"message");
                    break;
                case "queue-download":
                    rtrn = parseCmd(value,QueueDownload.class,"path","destination");
                    break;
                case "read-state":
                    rtrn = parseCmd(value,ReadState.class,"name");
                    break;
                case "regex":
                    rtrn = parseCmd(value,Regex.class,"pattern");
                    break;
                case "repeat-until":
                    rtrn = parseCmd(value,RepeatUntilSignal.class,"name");
                    break;
                case "set-state":
                    rtrn = parseCmd(value,SetState.class,"name","value");
                    break;
                case "sh":
                    rtrn = parseCmd(value,Sh.class,"command");

                    break;
                case "signal":
                    rtrn = parseCmd(value,Signal.class,"name");
                    break;
                case "sleep":
                    rtrn = parseCmd(value,Sleep.class,"ms");

                    break;
                case "wait-for":
                    rtrn = parseCmd(value,WaitFor.class,"name");
                    break;
                case "xpath":
                    rtrn = parseCmd(value,XPath.class,"path");
                    break;
                default:
                    errors.add("unknown command="+keyString+" with value="+value);
            }
        }
        return rtrn;
    }

    private void buildScript(Script script,List commandList){
        for(Object entry : commandList){
            if(entry instanceof Map) {
                Map entryMap = (Map) entry;
                if(entryMap.size()==1){
                    if(entryMap.containsKey("with")){
                        Object withObject = entryMap.get("with");
                        if(withObject instanceof String){
                            String split[] = ((String)withObject).trim().split(" ");
                            if(split.length==2){
                                script.with(split[0],split[1]);
                            }else{
                                errors.add("with expects two space separate arguments but saw: "+withObject.toString());
                            }
                        }
                    }else{
                        Cmd cmd = getCmd(entryMap);
                        script.then(cmd);
                    }
                }else{
                    errors.add(script.getName()+" expected {command : options} but found "+ entry);
                }
            }else if (entry instanceof List) {// this means it is a child of previous entry (script tail)
                List entryList = (List) entry;
                Cmd prev = script.getLastThen();
                buildCmd(prev,entryList);

            }else {
            }
        }
    }
    private void buildWatcher(Cmd command,List commandList){
        for(Object entry : commandList) {
            if (entry instanceof Map) {
                Map entryMap = (Map) entry;
                if(entryMap.size()==1){
                    Cmd childCmd = getCmd(entryMap);
                    command.watch(childCmd);
                }else {
                    errors.add(command.toString()+" watch expect command : options but saw: "+entry);
                }

            }else if (entry instanceof List){
                List entryList = (List) entry;
                Cmd lastWatcher = command.getLastWatcher();
                buildCmd(lastWatcher,entryList);


            }
        }
    }
    private void buildCmd(Cmd command,List commandList){
        for(Object entry : commandList) {
            if(entry instanceof Map) {
                Map entryMap = (Map) entry;
                if(entryMap.size()==1){
                    if(entryMap.containsKey("watch")){
                        Cmd tail = command.getTail();
                        Object watchObj = entryMap.get("watch");
                        if(watchObj instanceof List){
                            buildWatcher(tail,(List)watchObj);
                        }else{
                            errors.add("watch expects a list of {command : options} but found "+watchObj);
                        }
                    }else{
                        Cmd childCmd = getCmd(entryMap);
                        command.then(childCmd);
                    }
                }else{
                    errors.add(command.toString()+" expected {command : options} but found "+ entry);
                }
            }else if (entry instanceof List){
                List entryList = (List) entry;
                Cmd tail = command.getLastThen();
                buildCmd(tail,entryList);


            }
        }

    }
    public static void print(Object o,int indent,StringBuffer sb){
        String pad = indent > 0 ? String.format("%"+indent+"s","") : "";
        //sb.append(pad);
        if(o instanceof String || o instanceof Number){
            sb.append(o);
        }else if (o instanceof List){

            for(Object entry : ((List)o)){
                sb.append(pad);
                print(entry,indent+2,sb);
            }


        }else if (o instanceof  Map){

            Map m = ((Map)o);
            for(Object k : m.keySet()){
                sb.append(System.lineSeparator());
                sb.append(pad);
                sb.append(k);
                sb.append(" : ");
                print(m.get(k),indent+2,sb);
            }
        }else if (o == null) {
            sb.append("null");
        }else{
            sb.append(o);
        }
    }
}
