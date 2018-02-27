package perf.qdup.config;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.impl.*;
import perf.yaup.Sets;
import perf.yaup.json.Json;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static perf.qdup.config.YamlParser.*;

public class CmdBuilder {

    private static final List supported = Arrays.asList("long","int","java.lang.String","boolean","java.lang.Boolean");

    public static CmdBuilder getBuilder(){
        CmdBuilder rtrn = new CmdBuilder();
        rtrn.addCmdDefinition("abort",Abort.class,"message");
        rtrn.addCmdDefinition("code",CodeCmd.class,"className");
        rtrn.addCmdDefinition("countdown", Countdown.class,"name","initial");
        rtrn.addCmdDefinition("ctrlC",CtrlC.class);
        rtrn.addCmdDefinition("download", Download.class,"path","destination");
        rtrn.addCmdDefinition("upload",Upload.class,"path","destination");
        rtrn.addCmdDefinition("echo",Echo.class);
        rtrn.addCmdDefinition("invoke",ScriptCmd.class,"name");
        rtrn.addCmdDefinition("script",ScriptCmd.class,"name");
        rtrn.addCmdDefinition("log",Log.class,"message");
        rtrn.addCmdDefinition("queue-download",QueueDownload.class,"path","destination");
        rtrn.addCmdDefinition("read-state",ReadState.class,"name");
        rtrn.addCmdDefinition("regex",Regex.class,"pattern");
        rtrn.addCmdDefinition("repeat-until",RepeatUntilSignal.class,"name");
        rtrn.addCmdDefinition("set-state",SetState.class,"name","VALUE");
        rtrn.addCmdDefinition("sh",Sh.class,"command");
        rtrn.addCmdDefinition("sh",Sh.class,"command","silent");
        rtrn.addCmdDefinition("signal",Signal.class,"name");
        rtrn.addCmdDefinition("sleep",Sleep.class,"ms");
        rtrn.addCmdDefinition("wait-for",WaitFor.class,"name");
        rtrn.addCmdDefinition("wait-for",WaitFor.class,"name","silent");
        rtrn.addCmdDefinition("xpath",XPath.class,"path");

        return rtrn;
    }

    private class Key {
        private String name;
        private String type;

    }
    private class CmdEntry {
        private Class entryClass;
        private Set<String> keys;
        private Map<Integer,Set<String>> expectedArguments;
        private Map<Set<String>,Constructor> keyedConstructors;
        private Map<Integer,Constructor> sizedConstructors;

        public CmdEntry(Class entryClass) {
            this.entryClass = entryClass;
            keys = new HashSet<>();
            expectedArguments = new HashMap<>();
            sizedConstructors = new HashMap<>();
            keyedConstructors = new TreeMap<>(Comparator.comparingInt(Set::size));
        }
        public boolean checkArgTypes(List<String> args){
            boolean rtrn = true;
            if(sizedConstructors.containsKey(args.size())){
                Constructor constructor = sizedConstructors.get(args.size());
                Type types[] = constructor.getParameterTypes();
                for(int i=0; i<args.size(); i++){
                    Type type = types[i];
                    Object existing = args.get(i);

                    if(existing==null){
                        rtrn = false;
                    }else{
                        switch (type.getTypeName()){
                            case "long":
                            case "int":
                                rtrn = rtrn && Pattern.matches("\\d+",existing.toString());
                                break;
                            case "java.lang.String":
                                break;
                            case "boolean":
                            case "java.lang.Boolean":
                                rtrn = rtrn && Sets.of("yes","no","true","false").contains(existing.toString().toLowerCase());
                                break;
                        }
                    }
                }
            }
            return rtrn;
        }
        public Cmd create(List<Object> args){
            Cmd rtrn = Cmd.NO_OP();
            if(sizedConstructors.containsKey(args.size())){
                Constructor constructor = sizedConstructors.get(args.size());
                Type types[] = constructor.getParameterTypes();

                //change the args to the appropriate types
                for(int i=0; i<args.size();i++){
                    Type type = types[i];
                    Object existing = args.get(i);
                    switch(type.getTypeName()){
                        case "long":
                            try {
                                if(! (existing instanceof Long) ){
                                    args.remove(i);
                                    args.add(i, Long.parseLong(existing.toString().replaceAll("_", "")));
                                }
                            }catch(NumberFormatException nfe){
                                nfe.printStackTrace();
                            }
                            break;
                        case "int":
                            if(! (existing instanceof Integer) ) {
                                args.remove(i);
                                args.add(i,Integer.parseInt(existing.toString().replaceAll("_","")));
                            }
                            break;
                        case "java.lang.String":
                            if(! (existing instanceof String) ) {
                                args.remove(i);
                                args.add(i,existing.toString());
                            }
                            break;
                        case "boolean":
                        case "java.lang.Boolean":
                            if(! (existing instanceof Boolean) ){
                                args.remove(i);
                                args.add(i,"true".equalsIgnoreCase(existing.toString()) || "yes".equalsIgnoreCase(existing.toString()));
                            }
                            break;
                        default:
                            //TODO how to handle unsupported type?
                    }
                }

                try {
                    rtrn = (Cmd) constructor.newInstance(args.toArray());
                } catch (InstantiationException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }

            }

            return rtrn;
        }
        public int count(){return keyedConstructors.size();}
        public Set<Integer> sizes(){
            return sizedConstructors.keySet();
        }
        public Set<String> getOption(int size){
            if(expectedArguments.containsKey(size)){
                return expectedArguments.get(size);
            }else{
                return Collections.emptySet();
            }
        }
        public boolean addOption(String...args){

            Constructor constructors[] = entryClass.getConstructors();
            for(int c=0; c<constructors.length;c++){
                Constructor constructor = constructors[c];

                Type types[] = constructor.getParameterTypes();
                boolean simple = types.length > 0 ? Stream.of(types)
                        .map(Type::getTypeName)
                        .map(supported::contains).reduce(Boolean::logicalAnd)
                        .get() : true;
                if(types.length == args.length && simple){
                    Set<String> constructorArguments = Sets.of(args);
                    keys.addAll(constructorArguments);
                    keyedConstructors.put(constructorArguments,constructor);
                    sizedConstructors.put(constructorArguments.size(),constructor);
                    expectedArguments.put(constructorArguments.size(),constructorArguments);
                    return true;
                }
            }
            return false;

        }
        public Set<Set<String>> getOptions(){
            return keyedConstructors.keySet();
        }



    }

    public boolean has(String shortname){
        return commands.containsKey(shortname);
    }
    public int count(String shortname){
        return has(shortname) ? commands.get(shortname).count():0;
    }
    public Set<Integer> sizes(String shortname){
        return has(shortname) ? commands.get(shortname).sizes() : Sets.of();
    }
    public Set<Set<String>> getOptions(String shortname){
        return has(shortname) ? commands.get(shortname).getOptions() : Sets.of();
    }
    public void addWith(Cmd command,Json json){
        String jsonKey = json.getString(KEY,"");
        if(WITH.equals(jsonKey)){
            if(json.has(CHILD)){
                Json childArray = json.getJson(CHILD);
                for(int i=0; i<childArray.size();i++){
                    Json childEntryList = childArray.getJson(i);
                    for(int c=0;c<childEntryList.size();c++){
                        Json childEntry = childEntryList.getJson(c);
                        if(childEntry.has(KEY) && childEntry.has(VALUE)){
                            String withKey =childEntry.getString(KEY);
                            String withValue =childEntry.get(VALUE).toString();
                            command.with(withKey,withValue);
                        }
                    }
                }
            }
        }else{
            //TODO alert the error?
        }
    }
    public void addWatchers(Cmd command,Json json){

        String jsonKey = json.getString(KEY);
        if(WATCH.equals(jsonKey)){
            if(json.has(CHILD)){
                Json childArray = json.getJson(CHILD);
                for(int i=0; i<childArray.size();i++){
                    Json childEntryList = childArray.getJson(i);
                    for(int c=0;c<childEntryList.size();c++){
                        Json childEntry = childEntryList.getJson(c);
                        Cmd childCmd = buildYamlCommand(childEntry,null);
                        command.watch(childCmd);
                    }
                }

            }
        }else{
            //TODO alert the error?
        }
    }
    public void addTimer(Cmd command,Json json){
        String jsonKey = json.getString(KEY);
        String jsonValue = json.getString(VALUE);
        if(TIMER.equalsIgnoreCase(jsonKey)){
            if(jsonValue!=null){
                long timeout = Sleep.parseToMs(jsonValue);
                if(json.has(CHILD)){
                    Json childArray = json.getJson(CHILD);
                    Cmd timedCmd = Cmd.NO_OP();
                    for(int i=0; i<childArray.size(); i++){
                        Json childEntryList = childArray.getJson(i);
                        for(int c=0; c<childEntryList.size();c++){
                            Json childEntry = childEntryList.getJson(c);
                            Cmd childCmd = buildYamlCommand(childEntry,timedCmd);
                            timedCmd.then(childCmd);
                        }
                    }
                    command.addTimer(timeout,timedCmd);
                }
            }else{
                //TODO warn that timers need a value?
            }
        }
    }
    public Cmd buildYamlCommand(Json json,Cmd parent){
        Cmd rtrn = Cmd.NO_OP();
        final List<Object> args = new ArrayList<>();
        Json target = json;
        String shortname=null;
        int childStartIndex=0;
        if(json.isArray()){

            target = json.getJson(0);
            shortname = target.getString(KEY);

            //System.out.println(shortname+" isArray "+json.toString(2));

            if(target.has(VALUE)){
                List<String> split = split(target.getString(VALUE));
                if(commands.get(shortname).sizes().contains(split.size()) && commands.get(shortname).checkArgTypes(split)){
                    args.addAll(split);
                }else if (commands.get(shortname).sizes().contains(1)){
                    args.add(target.getString(VALUE));
                }else{
                    //ERROR
                }
            }else if(json.size()>1 && args.isEmpty()){
                //the list entry has peers who aren't prepended WITH a -
                Map<String,String> entries = new HashMap<>();
                for(int i=1; i<json.size(); i++){
                    Json arg = json.getJson(i);
                    String argKey = arg.getString(KEY);
                    if(arg.has(VALUE)) {
                        entries.put(argKey, arg.getString(VALUE));
                    }else{
                        //TODO check for watch or WITH? (they really should be nested not same level)
                    }
                }
                if(has(shortname)){//if this is a valid constructor
                    if(commands.get(shortname).sizes().contains(entries.size())){
                       for(String expected : commands.get(shortname).getOption(entries.size())){
                           args.add(entries.get(expected));
                       }
                    }else{
                        entries.clear();
                    }
                }

            }else if (target.has(CHILD) && target.getJson(CHILD).size()>1){
                Json childEntry = target.getJson(CHILD).getJson(0);
                Map<String,String> entries = new HashMap<>();
                for(int i=0; i<childEntry.size(); i++){
                    Json arg = childEntry.getJson(i);
                    String argKey = arg.getString(KEY);
                    if(arg.has(VALUE)) {
                        entries.put(argKey, arg.getString(VALUE));
                    }else{
                        //what sort of craziness was added inline like this?
                    }
                }
                if(commands.get(shortname).sizes().contains(entries.size())){
                    for(String expected : commands.get(shortname).getOption(entries.size())){
                        args.add(entries.get(expected));
                    }
                    childStartIndex=1; //skip the first child. It was used to create the command
                }else{
                    entries.clear();
                }



            }
        }else {
            shortname = target.getString(KEY);
            if (has(shortname)) {
                Set<Integer> sizes = sizes(shortname);
                if (target.has(VALUE)) {//try and build the command from just the VALUE
                    //TODO split approach is error prone, should find a more stable solution
                    List<String> split = split(target.getString(VALUE));
                    if (sizes.contains(split.size())) {
                        args.addAll(split);
                    } else if (sizes.contains(1)) {
                        args.add(target.getString(VALUE));
                    } else {
                        //ERROR
                    }
                } else {
                    List<Object> tmpArgs = new LinkedList<>();
                    final String shortRef = shortname;
                    target.optJson(CHILD).ifPresent(childArray -> {
                        childArray.optJson(0).ifPresent(firstChildList -> {
                            //if the map matches the expected number of arguments for a constructor
                            if (sizes.contains(firstChildList.size())) {
                                Map<String,String> entries = new HashMap<>();
                                if (firstChildList.getJson(0).has(VALUE)) {//treat as a map
                                    Set<String> expected = commands.get(shortRef).getOption(firstChildList.size());
                                    firstChildList.forEach(obj -> {
                                        if (obj instanceof Json) {
                                            Json entry = (Json) obj;
                                            entries.put(entry.getString(KEY), entry.getString(VALUE));
                                        }
                                    });
                                    for (String arg : expected) {
                                        if (entries.containsKey(arg)) {
                                            args.add(entries.get(arg));
                                        } else {
                                            //we are missing a named argument, fail construction
                                            args.clear();
                                            break; // stop iterating, we have an error
                                        }
                                    }
                                } else {//treat as a list
                                    firstChildList.forEach(obj -> {
                                        if (obj instanceof Json) {
                                            Json entry = (Json) obj;
                                            args.add(entry.getString(KEY));
                                        }
                                    });
                                }
                            }
                        });
                    });
                }
            }
        }

        rtrn = commands.get(shortname).create(args);

        if(target.has(CHILD)){
            Json childList = target.getJson(CHILD);
            for(int i=childStartIndex; i<childList.size();i++){
                Json childEntryList = childList.getJson(i);
                for(int c=0; c<childEntryList.size(); c++){
                    Json childEntry = childEntryList.getJson(c);
                    String childKey = childEntry.getString(KEY).toLowerCase();
                    switch (childKey){
                        case WATCH:
                            addWatchers(rtrn,childEntry);
                            break;
                        case WITH:
                            addWith(rtrn.getTail(),childEntry);
                            break;
                        case TIMER:
                            addTimer(rtrn.getTail(),childEntry);
                        default://
                            if(has(childKey)){// a known command
                                Cmd next = buildYamlCommand(childEntry,rtrn);
                                rtrn.then(next);
                            }else{
                                //TODO what do we do WITH unknown commands?
                            }
                    }
                }
            }
        }
        return rtrn;
    }
    public static List<String> split(String input){
        List<String> rtrn = new LinkedList<>();
        int start=0;
        int current=0;
        boolean quoted = false;
        boolean pop = false;
        while(current<input.length()){
            switch (input.charAt(current)){
                case '\"':
                    if(!quoted){
                        quoted=true;
                        if(current>start){
                            pop=true;
                        }
                    }else{
                        if('\\'== input.charAt(current-1)){

                        }else{
                            quoted=false;
                            if(current>start){
                                pop=true;
                            }

                        }
                    }
                    break;
                case ' ':
                case '\t':
                    if(!quoted){
                        if(current>start){
                            pop=true;
                        }
                    }
            }
            if(pop){
                String arg = input.substring(start,current);
                if(arg.startsWith("\"")){
                    arg = arg.substring(1);
                }
                //don't need to check for tailing " because current is not yet incremented

                //drop spaces if not already at end
                if(current+1<input.length()) {
                    int drop = current + 1;
                    while (input.charAt(drop) == ' ' || input.charAt(drop) == '\t') {
                        drop++;
                    }
                    start = drop;
                }
                rtrn.add(arg);
                pop=false;
            }

            current++;
        }

        if(start<current){
            rtrn.add(input.substring(start,current));
        }
        return rtrn;
    }


    private Map<String,CmdEntry> commands;

    private CmdBuilder(){
        commands = new HashMap<>();
    }

    public Set<String> shortnames(){return commands.keySet();}
    public boolean addCmdDefinition(String shortname,Class<? extends Cmd> cmdClass,String...argNames){

        if(RESERVED.contains(shortname)){
            //TODO throw error
            return false;
        }
        if (!commands.containsKey(shortname)) {
            commands.put(shortname,new CmdEntry(cmdClass));
        }
        return commands.get(shortname).addOption(argNames);
    }

}
