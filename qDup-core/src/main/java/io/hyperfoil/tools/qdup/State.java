package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.PatternValuesMap;
import io.hyperfoil.tools.qdup.cmd.impl.JsCmd;
import io.hyperfoil.tools.yaup.json.Json;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by wreicher
 * Conceptually a tree of maps where new [KEY,VALUE] pairs default to the current map
 * but will be placed in a parent map if the KEY starts WITH the parent's prefix
 * (checking all the way up the tree before using the current State).
 *
 * This is used to maintain a global run state (WITH prefix RUN_PREFIX) with child host states
 * (with prefix HOST_PREFIX) and child script states (no prefix). Scripts run with a unique script state so by default their keys do not conflict unless the
 * KEY intentionally uses a parent prefix.
 *
 * Considerations
 *   A State with an empty prefix (prefix.isEmpty()==true) will effectively make the parent States read only from that perspective and will use any prefix as part of the key.
 *     All KEY's will match the prefix check so no set(KEY,VALUE) operations will modify a parent State.
 *   A State with a null prefix will never match a prefix check so it becomes read only from a child perspective but the parents are still mutable.
 *
 */
public class State {

    private static Pattern IntegerPattern = Pattern.compile("-?\\d{1,16}+");
    private static Pattern DoublePattern = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    public static final String CHILD_DELIMINATOR = ".";
    public static final String RUN_PREFIX = "RUN"+CHILD_DELIMINATOR;
    public static final String HOST_PREFIX = "HOST"+CHILD_DELIMINATOR;


    private State parent;
    private Json json;
    private Map<String,State> childStates;
    private String prefix;
    private SecretFilter secretFilter;

    public static class CmdState extends State {
        private final Cmd cmd;
        public CmdState(State parent, Cmd cmd){
            super(parent,null);
            this.cmd = cmd;
        }
        @Override
        public Object get(String key){
            String populatedKey = Cmd.populateStateVariables(key,cmd,this,null,null);
            if(cmd.hasWith(populatedKey)){
                return cmd.getWith(populatedKey);
            }else{
                return parent().get(populatedKey);
            }
        }
        @Override
        public void set(String key,Object value){
            parent().set(key,value);
        }
    }
    public static String removeStatePrefix(String input){
        if(input == null || input.isEmpty()){
            return "";
        }else if (input.startsWith(RUN_PREFIX)){
            return input.substring(RUN_PREFIX.length());
        }else if (input.startsWith(HOST_PREFIX)){
            return input.substring(HOST_PREFIX.length());
        }else{
            return input;
        }
    }

    public static Object convertType(Object input){
        if(input == null){
            return "";
        }
        if(input instanceof String){
            String a = (String)input;
            if (IntegerPattern.matcher(a).matches()) {
                return Long.parseLong(a);
            } else if (DoublePattern.matcher(a).matches()) {
                return Double.parseDouble(a);
            } else if (Json.isJsonLike(a)) {
                return Json.fromString(a);
            }
        }
        return input;
    }

    public boolean hasParent(){return parent()!=null;}

    public State rootState(){
        State rtrn = this;
        while(rtrn.hasParent()){
            rtrn = rtrn.parent();
        }
        return rtrn;
    }
    State parent(){return parent;}

    public State(String prefix){
        this(null,new SecretFilter(),prefix);
    }
    public State(State parent,String prefix){
        //assert  parent != null;
        this(parent,parent.secretFilter,prefix);
    }
    private State(State parent, SecretFilter filter, String prefix){
        this.parent = parent;
        this.secretFilter = filter;
        this.json = new Json();
        this.childStates = new ConcurrentHashMap<>();
        this.prefix = prefix;
    }

    public SecretFilter getSecretFilter(){return secretFilter;}

    public void merge(State state){
        if(this.prefix == state.prefix){
            state.getKeys().forEach(key->{
                if(!this.json.has(key)){
                    this.json.set(key,state.get(key));
                }
            });
            state.getChildNames().forEach(childName->{
                State childState = state.getChild(childName);
                addChild(childName,childState.prefix).merge(childState);
            });
        }else if (state.prefix!=null){
            addChild(state.prefix,state.prefix).merge(state);
        }else{
            //WTF to do with a miss-match prefix

        }
        secretFilter.loadSecrets(state.getSecretFilter());
    }

    public void load(Json json){
        load(json,true); //default is to auto-convert
    }
    public void load(Json json,boolean autoConvert){
        json.forEach((key,value)->{
            if(value instanceof Json){
                if (((Json)value).has("value") && ((Json)value).has("autoConvert") && ((Json)value).size() == 2) {
                    String stateValue = ((Json)value).getString("value");
                    boolean autoConvertThis = ((Json)value).getBoolean("autoConvert");
                    set(key.toString(), stateValue, autoConvertThis);
                } else {
                    set(key.toString(),value.toString(), autoConvert);
                }
            }else{
                set(key.toString(),value.toString(), autoConvert);
            }
        });
    }

    private void scanSecrets(){

        Queue<Json> toScan = new LinkedList<>();
        toScan.add(json);
        while(!toScan.isEmpty()){
            Json scan = toScan.remove();
            List<Object> keys = new ArrayList<>();
            scan.forEach((key,value)->{
                if(key.toString().startsWith(SecretFilter.SECRET_NAME_PREFIX)){
                    keys.add(key.toString());
                }
                if(value instanceof Json){
                    toScan.add((Json)value);
                }
            });
            if(!keys.isEmpty()){
                keys.forEach(key->{
                    String newKey = key.toString().substring(SecretFilter.SECRET_NAME_PREFIX.length());
                    Object value = scan.get(key);
                    secretFilter.addSecret(value.toString());
                    scan.remove(key);
                    scan.set(newKey,value);
                });
            }
        }
    }

    public Map<Object,Object> getOwnState(){
        return Collections.unmodifiableMap(Json.toObjectMap(json));
    }
    public Map<Object,Object> getFullState(){
        Map<Object,Object> rtrn = new HashMap<>();
        State target = this;
        while( target!=null ){
            for(String key : target.getKeys()){
                if(!rtrn.containsKey(key)){
                    rtrn.put(key,target.get(key));
                }
            }
            target = target.parent;
        }
        return rtrn;
    }
    public boolean hasChild(String name){
        return childStates.containsKey(name);
    }
    public State getChild(String name){
        return getChild(name,null);
    }
    public State getChild(String name,String prefix){
        return addChild(name,prefix);//default to creating a new CHILD
    }
    public State addChild(String name,String prefix){
        childStates.putIfAbsent(name,new State(this,prefix));
        return childStates.get(name);
    }

    public void set(String key,Object value) {
        this.set( key, value, true);
    }

    public void set(String key, Object value, boolean autoConvert){
        if (autoConvert) {
            value = convertType(value);
        }else if (Json.isJsonLike(value.toString())){
            Json test = Json.fromString(value.toString());
            if ( test!=null ) {
                value = test;
            }
        }
        State target = this;
        //leave this here to detect _RUN. or _HOST.
        boolean isSecret = key.startsWith(SecretFilter.SECRET_NAME_PREFIX);
        if(isSecret){
            key = key.substring(SecretFilter.SECRET_NAME_PREFIX.length());
            secretFilter.addSecret(value.toString());
        }
            do {
                if (target.prefix != null && key.startsWith(target.prefix)) {
                    String newKey = key.substring(target.prefix.length());
                    //use chain set to break .'s into child objects
                    Json.chainSet(target.json,newKey,value);
                    target.scanSecrets();
                    return;
                }
            } while ((target = target.parent) != null);
        //see if the key starts with a child name
        //should a state be able to push to children???
        for(String childName : childStates.keySet()){
            if(key.startsWith(childName+CHILD_DELIMINATOR)){
                String childKey =  key.substring(childName.length()+CHILD_DELIMINATOR.length());
                childStates.get(childName).set(childKey,value);
                return; //Added because should only set value on target state
            }
        }
        //at this point there wasn't a prefix match
        Json.chainSet(this.json,key,value);
        scanSecrets();
    }
    public void set(Json json){
        for(Object key : json.keys()){
            set((String)key,json.get(key));
        }
    }
    public void set(Map<String,String> map){
        for(String key : map.keySet()){
            set(key,map.get(key));
        }
    }
    public boolean has(String key){
        return has(key,false);
    }
    public boolean has(String key,boolean recursive){
        if(key==null){
            return false;
        }
        boolean rtrn = false;
        State target = this;
        String currentKey = key;//once we remove the prefixes it can match any scope above the current scope
        while(!rtrn && target!=null){
            if(target.prefix!=null && currentKey.startsWith(target.prefix)){
                currentKey = currentKey.substring(target.prefix.length());
            }
            rtrn = target.json.has(currentKey) || Json.find(target.json,currentKey.startsWith("$") ? currentKey : "$." + currentKey)!=null;
            if(!rtrn && Json.isJsonSearchPath(currentKey)){
                String keyPrefix = Json.getPreSearchPath(currentKey);
                if(!keyPrefix.isBlank()){
                    rtrn = target.json.has(keyPrefix) || Json.find(target.json,keyPrefix.startsWith("$") ? keyPrefix : "$." + keyPrefix)!=null;
                }
            }
            target = target.parent;
        }
        return rtrn;
    }
    public boolean remove(String key){
        State target = this;
        boolean rtrn = false;
        String currentKey = key;
        //find the key if it starts with a prefix
        do {
            if(target.prefix!=null && currentKey.startsWith(target.prefix)){
                currentKey = currentKey.substring(target.prefix.length());
                if(target.json.has(currentKey)){
                    rtrn = true;
                    target.json.remove(currentKey);
                }
            }
        }while((target=target.parent)!=null && !rtrn);

        if(!rtrn){
            if(this.json.has(key)){
                rtrn = true;
                this.json.remove(key);
            }
        }
        return rtrn;
    }
    public Object get(String key){
        State target = this;
        Object rtrn = null;
        String currentKey = key;
        //check for a prefix match
        do {
            if(target.prefix!=null && currentKey.startsWith(target.prefix)){
                currentKey = currentKey.substring(target.prefix.length());
                String searchKey = currentKey.startsWith("$") ? currentKey : "$."+currentKey;
                rtrn = target.json.has(currentKey) ? target.json.get(currentKey) : Json.find(target.json,searchKey);
            }
        }while( (target=target.parent)!=null && rtrn==null);

        //if there wasn't a prefix match
        if(rtrn == null) {
            target = this;
            currentKey = key;
            do {
                if(target.prefix!=null && currentKey.startsWith(target.prefix)){
                    currentKey = currentKey.substring(target.prefix.length());
                }
                rtrn = target.json.has(currentKey) ? target.json.get(currentKey) : Json.find(target.json,currentKey.startsWith("$") ? currentKey : "$."+currentKey);
                //why was this part added? doesn't it incorrectly skip the filtering for valid filters that don't match?
                //was this copied from has which needed this logic for ensuring jsonpaths exist?
                if(rtrn == null && Json.isJsonSearchPath(currentKey)){
                    String keyPrefix = Json.getPreSearchPath(currentKey);
                    if(!keyPrefix.isBlank()){
                        Object fnd =  Json.find(target.json,keyPrefix.startsWith("$") ? keyPrefix : "$." + keyPrefix);
                        if(fnd != null){ //the search was valid but we didn't find anything
                            rtrn = new Json(true); //is retuning an empty list the correct response for a filter miss?
                            //from https://github.com/json-path/JsonPath
                            //Indefinite paths always returns a list (as represented by current JsonProvider).
                            //and using $( means they are indefined
                        }
                    }
                }
            } while (rtrn == null && (target = target.parent) != null);
        }
        return rtrn;
    }
    public String getString(String key){
        return getString(key,"");
    }
    public String getString(String key,String defaultValue){
        Object found = get(key);
        if(found != null){
            if(found instanceof String){
                return (String)found;
            }else{
                return found.toString();
            }
        }else{
            return defaultValue;
        }
    }
    public Set<String> getVisibleKeys(){
        Set<String> rtrn = new HashSet<>();
        State target = this;
        do {
            rtrn.addAll(getKeys());
        }while ( (target=target.parent) != null);
        return rtrn;
    }
    public Set<String> allKeys(){
        Set<String> rtrn = new HashSet<>();
        rtrn.addAll(getKeys());
        for(State child : childStates.values()){
            rtrn.addAll(child.allKeys());
        }
        return rtrn;
    }
    public String getPrefix(){return prefix;}
    public List<String> getKeys(){
        return Collections.unmodifiableList(
            Arrays.asList(
                json.keySet().toArray(new String[0])
            )
        );
    }
    public List<String> getChildNames(){
        return Collections.unmodifiableList(
            Arrays.asList(
                childStates.keySet().toArray(new String[0])
            )
        );
    }

    public String tree(){
        StringBuilder buffer = new StringBuilder();
        tree(1,buffer);
        return buffer.toString();
    }
    public Map<Object,Object> toMap(){
        return new PatternValuesMap(null,this,null,null,null);
    }
    public Json toJson(){
        Json rtrn = new Json(false);

        for(Object key : json.keySet()){
            rtrn.set(key,json.get(key));
        }
        for(String child : childStates.keySet()){
            rtrn.set(child,childStates.get(child).toJson());
        }
        return rtrn;
    }
    public Json toOwnJson(){
        return json.clone();
    }
    public void tree(int indent,StringBuilder sb){
        int space = indent>0? indent:1;
        Json toUse = filter(json.clone());
        for(String key : getKeys()){
            sb.append(String.format("%"+space+"s%s = %s%n","",key, toUse.get(key)));
        }
        for(String childName : getChildNames()){
            sb.append(String.format("%"+space+"s%s : %n","",childName));
            getChild(childName).tree(indent+2,sb);
        }
    }
    private Json filter(Json input){
        input.forEach((k,v)->{
            if(v == null){
                //do not need to filter null values
            }else if(v instanceof Json){
                filter((Json)v);
            }else {
                input.set(k, v != null ? getSecretFilter().filter(v.toString()) : null );
            }
        });
        return input;
    }

    public State clone() {
        return clone(false);
    }
    public State clone(boolean deep){
        State rtrn = this.parent==null ? new State(this.prefix) :  new State(this.parent,this.prefix);
        //break abstraction to avoid prefix checks
        this.json.forEach((k,v)->{
            rtrn.json.set(k,v);
        });
        this.childStates.forEach((k,v)->{
            rtrn.childStates.put(k,deep ? v.clone() : v);
        });
        rtrn.getSecretFilter().loadSecrets(getSecretFilter());
        return rtrn;
    }

}
