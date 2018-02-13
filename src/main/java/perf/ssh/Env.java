package perf.ssh;

import java.util.*;

public class Env {

    public static class Diff {
        private Map<String,String> data;
        private Set<String> unset;

        public Diff(Map<String,String> data,Set<String> unset){
            this.data = data;
            this.unset = unset;
        }

        public Set<String> keys(){return data.keySet();}
        public Set<String> unset(){return unset;}
        public String get(String key){return data.get(key);}
        public boolean hasUnset(){return !unset.isEmpty();}
        public boolean isEmpty(){return data.isEmpty() && !hasUnset();}
    }

    private Map<String,String> data;

    public Env(Map<String,String> data){
        this.data = data;
    }
    public Env(String input){
        this(parse(input));
    }

    public Set<String> keys(){return data.keySet();}
    public String get(String key){return data.get(key);}
    public boolean isEmpty(){return data.isEmpty();}
    public int size(){return data.size();}

    public static Map<String,String> parse(String input){
        Map<String,String> rtrn = new LinkedHashMap<>();
        if(input!=null && !input.isEmpty()){
            String split[] = input.split("\n");
            String prevKey="";
            for(int i=0; i<split.length; i++){
                String line = split[i];
                int equalsIndex=line.indexOf("=");
                int spaceIndex=line.indexOf(" ");
                if(equalsIndex>-1 && (spaceIndex==-1 || equalsIndex < spaceIndex)){//we think the line is a key=var
                    String key = line.substring(0,equalsIndex);
                    String value = equalsIndex < line.length() ? line.substring(equalsIndex+1) : "";
                    prevKey = key;
                    rtrn.put(key,value);
                }else{
                    if(!prevKey.isEmpty()){//the line is probably a continuation of the previous entry
                        rtrn.put(prevKey,rtrn.get(prevKey)+System.lineSeparator()+line);
                    }
                }
            }

        }

        return rtrn;
    }

    /**
     * Return a diff representing the changes to get this to env
     * @param env
     * @return
     */
    public Diff diffTo(Env env){
        Map<String,String> sets = new LinkedHashMap<>();

        Set<String> unsets = new LinkedHashSet<>(this.keys());
        unsets.removeAll(env.keys());

        Set<String> addSet = new HashSet<>(env.keys());
        addSet.removeAll(this.keys());

        Set<String> common = new HashSet<>(this.keys());
        common.retainAll(env.keys());

        common.forEach(key->{
            String thisValue = this.get(key);
            String thatValue = env.get(key);
            if(!thisValue.equals(thatValue)){
                sets.put(key,thatValue);
            }
        });
        addSet.forEach(key->{
            sets.put(key,env.get(key));
        });
        return new Diff(sets,unsets);
    }
}
