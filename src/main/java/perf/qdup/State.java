package perf.qdup;

import perf.yaup.json.Json;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by wreicher
 * Conceptually a tree of maps where new [KEY,VALUE] pairs default to the current map
 * but will be placed in a parent map if the KEY starts WITH the parent's prefix
 * (checking all the way up the tree before using the current State).
 *
 * This is used to maintain a global run state (WITH prefix RUN_PREFIX) WITH CHILD host states
 * (WITH prefix HOST_PREFIX) and CHILD script states (no prefix). Scripts run WITH a reference
 * to their unique script state so by default their keys do not conflict unless the
 * KEY intentionally uses a parent prefix.
 *
 * Considerations
 *   A State WITH an empty prefix (prefix.isEmpty()==true) will effectively make the parent States read only from that perspective.
 *     All KEY's will match the prefix check so no set(KEY,VALUE) operations will modify a parent State.
 *   A State WITH a null prefix will never match a prefix check so it becomes read only from that perspective but the parents are still mutable.
 *
 */
public class State {

    public static final String RUN_PREFIX = "RUN.";
    public static final String HOST_PREFIX = "HOST.";
    public static final String CHILD_DELIMINATOR = ".";

    private State parent;
    private Map<String,String> state;
    private Map<String,State> childStates;
    private String prefix;

    public State(String prefix){
        this(null,prefix);
    }
    public State(State parent,String prefix){
        this.parent = parent;
        this.state = new ConcurrentHashMap<>();
        this.childStates = new ConcurrentHashMap<>();
        this.prefix = prefix;
    }


    public Map<String,String> getOwnState(){
        return Collections.unmodifiableMap(state);
    }
    public Map<String,String> getFullState(){
        Map<String,String> rtrn = new HashMap<>(state);
        State target = this.parent;
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
        if(!hasChild(name)){
            State newChild = new State(this,prefix);
            childStates.put(name,newChild);
        }
        return childStates.get(name);
    }
    public String set(String key,String value){
        State target = this;
        do {
            if(target.prefix!=null && key.startsWith(target.prefix)){
                return target.state.put(key.substring(target.prefix.length()),value);
            }
        } while( (target=target.parent)!=null );

        //see if the key starts with a child name
        for(String childName : childStates.keySet()){
            if(key.startsWith(childName+CHILD_DELIMINATOR)){
                return childStates.get(childName).set(key.substring(childName.length()+CHILD_DELIMINATOR.length()),value);
            }
        }

        //at this point there wasn't a prefix match
        return this.state.put(key,value);
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
        boolean rtrn = false;
        State target = this;
        while(!rtrn && target!=null){
            rtrn = target.state.containsKey(key);
            target = target.parent;
        }
        return rtrn;
    }
    public String get(String key){
        State target = this;
        String rtrn = null;
        //check for a prefix match
        do {
            if(target.prefix!=null && key.startsWith(target.prefix)){
                rtrn = target.state.get(key.substring(target.prefix.length()));
            }
        }while( (target=target.parent)!=null && rtrn==null);

        //if there wasn't a prefix match
        if(rtrn == null) {
            target = this;
            do {
                rtrn = target.state.get(key);
            } while (rtrn == null && (target = target.parent) != null);
        }
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
    public List<String> getKeys(){
        return Collections.unmodifiableList(
            Arrays.asList(
                state.keySet().toArray(new String[0])
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

        for(String key : state.keySet()){
            rtrn.set(key,state.get(key));
        }
        for(String child : childStates.keySet()){
            rtrn.set(child,childStates.get(child).toJson());
        }

        return rtrn;
    }
    public void tree(int indent,StringBuilder sb){
        int space = indent>0? indent:1;
        for(String key : getKeys()){
            sb.append(String.format("%"+space+"s%s = %s%n","",key,state.get(key)));
        }
        for(String childName : getChildNames()){
            sb.append(String.format("%"+space+"s%s : %n","",childName));
            getChild(childName).tree(indent+2,sb);
        }
    }

    public State clone(){
        State rtrn = new State(this.parent,this.prefix);
        //break abstraction to avoid prefix checks
        this.state.forEach((k,v)->{
            rtrn.state.put(k,v);
        });
        this.childStates.forEach((k,v)->{
            rtrn.childStates.put(k,v);
        });
        return rtrn;
    }

}
