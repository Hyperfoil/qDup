package perf.ssh.config;

import org.yaml.snakeyaml.Yaml;
import perf.ssh.*;
import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.Result;
import perf.ssh.cmd.Script;
import perf.ssh.cmd.impl.*;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.net.URL;
import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;


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
        for(Host host : runConfig.allHosts().toList()){
            sb.append("  "+host);
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
            for(Host h : role.toList()){
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
            for(Object obj : yaml.loadAll(new FileReader(yamlPath))){
                readObject(obj,yamlPath);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void loadScriptCategory(String key,Object scriptObject,Consumer<String> addScript){
        if(scriptObject instanceof List){
            List scriptList = (List)scriptObject;
            for(Object scriptEntry : scriptList){
                if(scriptEntry instanceof String){
                    addScript.accept(scriptEntry.toString());
                    //runConfig.allHosts().addSetupScript( runConfig.getScript(setupEntry.toString()) );
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
                                                if(roleValue instanceof String || roleValue instanceof Map){
                                                    Host h = hostRef.apply(roleValue);
                                                    if(h!=null){
                                                        role.add(h);
                                                    }else{
                                                        errors.add(roleNameString+" could not parse host : "+roleValue);
                                                    }
                                                }else if (roleValue instanceof List){
                                                    List roleHostList = (List)roleValue;
                                                    for(Object roleHostEntry : roleHostList){
                                                        if(roleHostEntry instanceof String || roleHostEntry instanceof Map){
                                                            Host h = hostRef.apply(roleValue);
                                                            if(h!=null){
                                                                role.add(h);
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
                                                if(roleValue instanceof List){
                                                    List scriptList = (List)roleValue;
                                                    scriptList.forEach((scriptObj)->{
                                                        role.addRunScript(runConfig.getScript(scriptObj.toString()));
                                                    });
                                                }else if (roleValue instanceof String){
                                                    role.addRunScript(runConfig.getScript(roleValue.toString()));
                                                }else{
                                                    errors.add("run-scripts for "+roleNameString+" unexpected value :"+roleValue);
                                                }

                                                break;
                                            case "setup-scripts":
                                                if(roleValue instanceof List){
                                                    List scriptList = (List)roleValue;
                                                    scriptList.forEach((scriptObj)->{
                                                        role.addSetupScript(runConfig.getScript(scriptObj.toString()));
                                                    });
                                                }else if (roleValue instanceof String){
                                                    role.addSetupScript(runConfig.getScript(roleValue.toString()));
                                                }else{
                                                    errors.add("setup-scripts for "+roleNameString+" unexpected value :"+roleValue);
                                                }
                                                break;
                                            case "cleanup-scripts":
                                                if(roleValue instanceof List){
                                                    List scriptList = (List)roleValue;
                                                    scriptList.forEach((scriptObj)->{
                                                        role.addCleanupScript(runConfig.getScript(scriptObj.toString()));
                                                    });
                                                }else if (roleValue instanceof String){
                                                    role.addCleanupScript(runConfig.getScript(roleValue.toString()));
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
                    case "setup-scripts":
                        loadScriptCategory(keyString,map.get(key),(scriptName)->{runConfig.allHosts().addSetupScript(runConfig.getScript(scriptName));});
                        break;
                    case "run-scripts":
                        loadScriptCategory(keyString,map.get(key),(scriptName)->{runConfig.allHosts().addRunScript(runConfig.getScript(scriptName));});

                        break;
                    case "cleanup-scripts":
                        loadScriptCategory(keyString,map.get(key),(scriptName)->{runConfig.allHosts().addCleanupScript(runConfig.getScript(scriptName));});
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
                                                runConfig.getState().set(runKey.toString(),runValue.toString());
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
                                                            State scriptState = hostState.addChild(scriptName,null);
                                                            for(Object scriptEntry : scriptMap.keySet()){
                                                                String scriptEntryString = scriptEntry.toString();
                                                                Object scriptEntryValue = scriptMap.get(scriptEntry);
                                                                if(scriptEntryValue instanceof String){
                                                                    scriptState.set(scriptEntryString,(String)scriptEntryValue);
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
    private Cmd getCmd(Map map){
        Cmd rtrn = Cmd.NOOP();
        for(Object key : map.keySet()){
            String keyString = key.toString();
            Object value = map.get(key);
            switch (keyString){
                case "abort":
                    rtrn = new Abort(map.get(key).toString());
                    break;
                case "code":
                    errors.add("cannot load code from config ... yet");
                    rtrn = new CodeCmd((input,state)-> Result.next(input));
                    break;
                case "countdown":
                    if(value instanceof String){
                        String split[] = ((String)value).split(" ");
                        rtrn = new Countdown(split[0],Integer.parseInt(split[1]));
                    }else if (value instanceof Map){//name,initial
                        Object name = ((Map)value).get("name");
                        Object initial = ((Map)value).get("initial");
                        if(name == null || initial == null){
                            errors.add("countdown map syntax expects name and initial value but had "+((Map)value).keySet());
                        }else{
                            rtrn = new Countdown(name.toString(),Integer.parseInt(initial.toString()));
                        }
                    }else{
                        errors.add("unknown countdown value:"+value);
                    }
                    break;
                case "ctrlC":
                    rtrn = new CtrlC();
                    break;
                case "download":
                    if(value instanceof String) {
                        String split[] = ((String) value).trim().split(" ");
                        if(split.length==2){
                            rtrn = new Download(split[0],split[1]);
                        }else{
                            rtrn = new Download((String) value);
                        }
                    }else if (value instanceof Map){//path,destination
                        Object path = ((Map)value).get("path");
                        Object destination = ((Map)value).get("destination");
                        if(path == null || destination == null){
                            errors.add("download map syntax expects path and destination value but had "+((Map)value).keySet());
                        }else{
                            rtrn = new Download(path.toString(),destination.toString());
                        }
                    }else{
                        errors.add("unknown download value:"+value);
                    }
                    break;
                case "echo":
                    rtrn = new Echo();
                    break;
                case "invoke"://technically treating it the same as script: but not correct
                case "script":
                    rtrn = new ScriptCmd(value.toString());
                    break;
                case "log":
                    rtrn = new Log(value.toString());
                    break;
                case "queue-download":
                    if(value instanceof String) {
                        String split[] = ((String) value).trim().split(" ");
                        if(split.length==2){
                            rtrn = new QueueDownload(split[0],split[1]);
                        }else{
                            rtrn = new QueueDownload((String) value);
                        }
                    }else if (value instanceof Map){//path,destination
                        Object path = ((Map)value).get("path");
                        Object destination = ((Map)value).get("destination");
                        if(path == null || destination == null){
                            errors.add("queue-download map syntax expects path and destination value but had "+((Map)value).keySet());
                        }else{
                            rtrn = new QueueDownload(path.toString(),destination.toString());
                        }
                    }else{
                        errors.add("unknown queue-download value:"+value);
                    }
                    break;
                case "read-state":
                    rtrn = new ReadState(value.toString());
                    break;
                case "regex":
                    rtrn = new Regex(value.toString());
                    break;
                case "repeat-until":
                    //TODO multiple signal support for repeat-until?
                    rtrn = new RepeatUntilSignal(value.toString());
                    break;
                case "set-state":
                    if(value instanceof String) {
                        String split[] = ((String) value).trim().split(" ");
                        if(split.length==2){
                            rtrn = new SetState(split[0],split[1]);
                        }else{
                            rtrn = new SetState((String) value);
                        }
                    }else if (value instanceof Map){//key,value
                        Object stateKey = ((Map)value).get("key");
                        Object stateValue = ((Map)value).get("value");
                        if(stateKey == null || stateValue == null){
                            errors.add("set-state map syntax expects key and value but had "+((Map)value).keySet());
                        }else{
                            rtrn = new SetState(stateKey.toString(),stateValue.toString());
                        }
                    }else{
                        errors.add("unknown queue-download value:"+value);
                    }
                    break;
                case "sh":
                    rtrn = new Sh(value.toString());
                    break;
                case "signal":
                    rtrn = new Signal(value.toString());
                    break;
                case "sleep":
                    rtrn = new Sleep(Long.parseLong(value.toString()));
                    break;
                case "wait-for":
                    rtrn = new WaitFor(value.toString());
                    break;
                case "xpath":
                    rtrn = new XPath(value.toString());
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
            System.out.println("?? "+o.getClass().getName());
            sb.append(o);
        }
    }


    public static void main(String[] args) {
        String yamlPath = YamlLoader.class.getClassLoader().getResource("specjms.yaml").getFile();

        YamlLoader loader = new YamlLoader();
        loader.load(yamlPath);

        loader.getErrors().forEach(System.out::println);


        System.out.println(loader.dump());

        boolean valid = loader.getRunConfig().validate();

        System.out.println("isValid = "+valid);
    }
}
