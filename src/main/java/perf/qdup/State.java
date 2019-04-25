package perf.qdup;

import perf.qdup.cmd.Cmd;
import perf.yaup.json.Json;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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

    public static final String RUN_PREFIX = "RUN.";
    public static final String HOST_PREFIX = "HOST.";
    public static final String CHILD_DELIMINATOR = ".";

    private State parent;
    private Json json;
    private Map<String,State> childStates;
    private String prefix;


    public static class CmdState extends State {
        private final Cmd cmd;
        public CmdState(State parent, Cmd cmd){
            super(parent,null);
            this.cmd = cmd;
        }
        @Override
        public Object get(String key){
            String populatedKey = Cmd.populateStateVariables(key,cmd,this);
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

    State parent(){return parent;}

    public State(String prefix){
        this(null,prefix);
    }
    public State(State parent,String prefix){
        this.parent = parent;
        this.json = new Json();
        this.childStates = new ConcurrentHashMap<>();
        this.prefix = prefix;
    }

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
    }
    public void load(Json json){
        json.forEach((key,value)->{
            if(value instanceof Json){
                String childPrefix = null;
                if(RUN_PREFIX.equals(this.prefix)){
                    childPrefix = HOST_PREFIX;
                }
                State childState = getChild(key.toString(),childPrefix);
                childState.load((Json)value);
            }else{
                set(key.toString(),value.toString());
            }
        });
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
    public void set(String key,Object value){
        State target = this;
        do {
            if(target.prefix!=null && key.startsWith(target.prefix)){
                String newKey = key.substring(target.prefix.length());
                        //use chain set to break .'s itno child objects
                Json.chainSet(target.json,newKey,value);
                return;
            }
        } while( (target=target.parent)!=null );

        //see if the key starts with a child name
        //should a state be able to push to children???
        for(String childName : childStates.keySet()){
            if(key.startsWith(childName+CHILD_DELIMINATOR)){
                childStates.get(childName).set(key.substring(childName.length()+CHILD_DELIMINATOR.length()),value);
            }
        }
        //at this point there wasn't a prefix match
        Json.chainSet(this.json,key,value);
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
        String currentKey = key;//once we remove the previs it can match any scope above the current scope
        while(!rtrn && target!=null){
            if(currentKey.startsWith(target.prefix)){
                currentKey = currentKey.substring(target.prefix.length());
            }
            rtrn = target.json.has(currentKey) || Json.find(target.json,currentKey.startsWith("$") ? currentKey : "$." + currentKey)!=null;
            target = target.parent;
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
    public Json toJson(){
        Json rtrn = new Json();

        for(Object key : json.keySet()){
            rtrn.set(key,json.get(key));
        }
        for(String child : childStates.keySet()){
            rtrn.set(child,childStates.get(child).toJson());
        }
        return rtrn;
    }
    public void tree(int indent,StringBuilder sb){
        int space = indent>0? indent:1;
        for(String key : getKeys()){
            sb.append(String.format("%"+space+"s%s = %s%n","",key, json.get(key)));
        }
        for(String childName : getChildNames()){
            sb.append(String.format("%"+space+"s%s : %n","",childName));
            getChild(childName).tree(indent+2,sb);
        }
    }

    public State clone() {
        return clone(false);
    }
    public State clone(boolean deep){
        State rtrn = new State(this.parent,this.prefix);
        //break abstraction to avoid prefix checks
        this.json.forEach((k,v)->{
            rtrn.json.set(k,v);
        });
        this.childStates.forEach((k,v)->{
            rtrn.childStates.put(k,deep ? v.clone() : v);
        });
        return rtrn;
    }

}
