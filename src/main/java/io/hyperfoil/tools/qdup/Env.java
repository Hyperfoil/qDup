package io.hyperfoil.tools.qdup;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import io.hyperfoil.tools.yaup.Sets;
import io.hyperfoil.tools.yaup.StringUtil;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.stream.Collectors;

public class Env {
    private final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

    public static Set FILTER = Sets.of(
        "XDG_SESSION_ID",
        "SSH_CLIENT",
        "SSH_TTY",
        "SSH_CONNECTION",
        "HOSTNAME",
        "XDG_SESSION_ID",
        "OLDPWD",
        "PWD",
        "PS1",
        "LOGNAME",
        "SHLVL",
        "HOME",
        "_",
        "XDG_RUNTIME_DIR",
        "SELINUX_LEVEL_REQUESTED",
        "SELINUX_ROLE_REQUESTED",
        "SELINUX_USE_CURRENT_RANGE",
        "HOSTNAME",
        "HISTCMD",
        "__qdup_ec"
    );


    public static class Diff {
        private Map<String, String> data;
        private Set<String> unset;

        public Diff(Map<String, String> data, Set<String> unset) {
            this.data = data;
            this.unset = unset;

            FILTER.forEach(rem -> {
                data.remove(rem);
                unset.remove(rem);
            });
        }

        public Set<String> keys() {
            return data.keySet();
        }

        public Set<String> unset() {
            return unset;
        }

        public String get(String key) {
            return data.get(key);
        }

        public boolean hasUnset() {
            return !unset.isEmpty();
        }

        public boolean isEmpty() {
            return data.isEmpty() && !hasUnset();
        }

        public String debug() {
            return "Env.Diff:" +
                    "\n  set: \n" + data.keySet().stream().map((key) -> {
                return "    " + key + ": " + data.get(key);
            }).collect(Collectors.joining("\n")) +
                    "\n  unset: " + unset.stream().collect(Collectors.joining(", "));
        }

        public String getCommand() {
            final StringBuilder setEnv = new StringBuilder();
            final StringBuilder unsetEnv = new StringBuilder();
            keys().forEach(key -> {
                String keyValue = get(key);
                if (setEnv.length() > 0) {
                    setEnv.append(" ");
                }
                setEnv.append(" " + key + "=" + StringUtil.quote(keyValue));
            });
            unset().forEach(key -> {
                unsetEnv.append(" " + key);
            });
            String rtrn = (setEnv.length() > 0 ? "export" + setEnv.toString() : "") + (unsetEnv.length() > 0 ? ";unset" + unsetEnv.toString() : "");
            return rtrn;
        }
    }


    public String debug(){
        StringBuffer sb = new StringBuffer();
        sb.append("Before: "+before.size()+"\n");
        before.forEach((k,v)->{
            sb.append("  "+k+" = "+v+"\n");
        });
        sb.append("After: "+before.size()+"\n");
        after.forEach((k,v)->{
            sb.append("  "+k+" = "+v+"\n");
        });
        return sb.toString();
    }


    private Map<String,String> before;
    private Map<String,String> after;

    public Env(){
        this(new LinkedHashMap<>(),new LinkedHashMap<>());
    }
    public Env(Map<String,String> before,Map<String,String> after){
        this.before = before;
        this.after = after;
    }

    public void loadBefore(String input){
        parse(input,before);
    }
    public void loadAfter(String input){
        parse(input,after);
    }
    public Set<String> beforeKeys(){return before.keySet();}
    public Set<String> afterKeys(){return after.keySet();}
    public String getBefore(String key){return before.get(key);}
    public String getAfter(String key){return after.get(key);}
    public boolean isEmpty(){return before.isEmpty() && after.isEmpty();}

    public void merge(Env env){
        merge(env,false);
    }
    public void merge(Env env,boolean force){

        Diff from = env.getDiff();
        Diff ours = getDiff();

        from.keys().forEach(fromSet->{
            if(force){
                after.put(fromSet,from.get(fromSet));
            }else{
                if(!ours.unset().contains(fromSet) && !ours.keys().contains(fromSet)){
                    after.put(fromSet,from.get(fromSet));
                }else{
                    //we either set or unset the value so don't merge the change
                }
            }
        });
        from.unset().forEach(fromUnset->{
            if(force){
                after.remove(fromUnset);
            }else{
                if(!ours.keys().contains(fromUnset)){
                    after.remove(fromUnset);
                }else{
                    //we explicitly set it so ignore the unset
                }
            }
        });
    }


    public static void parse(String input,Map<String,String> rtrn){
        logger.debug("Env "+input);
        if(input!=null && !input.isEmpty()){
            String split[] = input.split("\r?\n");
            String prevKey="";
            for(int i=0; i<split.length; i++){
                String line = split[i];
                int equalsIndex=line.indexOf("=");
                int spaceIndex=line.indexOf(" ");
                if(equalsIndex>-1 && (spaceIndex==-1 || equalsIndex < spaceIndex)){//we think the line is a key=var
                    String key = line.substring(0,equalsIndex);
                    String value = equalsIndex < line.length() ? line.substring(equalsIndex+1) : "";
                    value = value.replaceAll("\r|\n",""); //not necessary but defensive
                    prevKey = key;
                    rtrn.put(key,value);
                }else{
                    if(!prevKey.isEmpty()){//the line is probably a continuation of the previous entry
                        line = line.replaceAll("\r|\n",""); //not necessary but defensive
                        rtrn.put(prevKey,rtrn.get(prevKey)+System.lineSeparator()+line);
                    }
                }
            }

        }
    }

    public Diff getDiff(){
        Map<String,String> sets = new LinkedHashMap<>();

        Set<String> unsets = new LinkedHashSet<>(beforeKeys());
        unsets.removeAll(afterKeys());

        Set<String> addSet = new HashSet<>(afterKeys());
        addSet.removeAll(beforeKeys());

        Set<String> common = new HashSet<>(beforeKeys());
        common.retainAll(afterKeys());

        common.forEach(key->{
            String beforeValue = getBefore(key);
            String afterValue = getAfter(key);
            if(!beforeValue.equals(afterValue)){
                sets.put(key,afterValue);
            }
        });
        addSet.forEach(key->{
            sets.put(key,getAfter(key));
        });
        return new Diff(sets,unsets);
    }
}
