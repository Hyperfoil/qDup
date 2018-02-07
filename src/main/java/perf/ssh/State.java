package perf.ssh;

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

    public static final String RUN_PREFIX = "RUN";
    public static final String HOST_PREFIX = "HOST";


    private State parent;
    private Map<String,String> state;
    private Map<String,State> childStates;
    private String prefix;

    public State(State parent,String prefix){
        this.parent = parent;
        this.state = new ConcurrentHashMap<>();
        this.childStates = new ConcurrentHashMap<>();
        this.prefix = prefix;
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
            if(target.prefix!=null && !target.prefix.isEmpty() && key.startsWith(target.prefix)){
                return target.state.put(key,value);
            }
        }while( (target=target.parent)!=null);
        //at this point there wasn't a prefix match
        return this.state.put(key,value);
    }
    public String get(String key){
        State target = this;
        String rtrn = null;
        do {
            rtrn = target.state.get(key);
        } while (rtrn == null && (target=target.parent)!=null);
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

}
