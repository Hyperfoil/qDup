package perf.qdup.config.yaml;

import perf.qdup.cmd.Cmd;
import perf.yaup.Sets;
import perf.yaup.StringUtil;
import perf.yaup.yaml.Defer;
import perf.yaup.yaml.Mapping;
import perf.yaup.yaml.WithDefer;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

public class CmdMapping<T extends Cmd> implements Mapping, WithDefer {
    public static final String WITH = "with";
    public static final String THEN = "then";
    public static final String TIMER = "timer";
    public static final String ONSIGNAL = "on-signal";
    public static final String WATCH = "watch";
    public static final String SILENT = "silent";




    public static final Set<String> COMMAND_KEYS = Collections.unmodifiableSet(Sets.of(WITH,THEN,TIMER,ONSIGNAL,WATCH));

    private Defer defer = null;
    final String key;
    final CmdEncoder<T> encoder;

    public CmdMapping(String key,CmdEncoder<T> encoder){
        this.key = key;
        this.encoder = encoder;
    }


    private void addCmd(Cmd cmd,List<Object> encoded){
        System.out.println("addCmd("+cmd+")");
        if(cmd instanceof Cmd.NO_OP){
            System.out.println("  NO_OP");
            Queue<Cmd> toAdd = new LinkedBlockingQueue<>();
            toAdd.add(cmd);
            while(!toAdd.isEmpty()){
                Cmd target = toAdd.poll();
                System.out.println("  target="+target);
                if(target instanceof Cmd.NO_OP){
                    target.getThens().forEach(toAdd::add);
                }else {
                    Object obj = defer(target);
                    System.out.println("    "+obj);
                    encoded.add(obj);
                }
            }
        }else {
            Object obj = defer(cmd);
            encoded.add(obj);
        }
    }

    public Object defer(Object data){
        Object rtrn = null;
        if(this.defer!=null){
            rtrn = this.defer.defer(data);
        }else{

        }
        return rtrn;
    }

    @Override
    public Map<Object, Object> getMap(Object o) {
        T cmd = (T)o;
        Map<Object,Object> rtrn = new LinkedHashMap<>();
        if(encoder!=null) {
            Object encoded = encoder.encode(cmd);
            if(encoded instanceof Map){
                Map encodedMap = (Map)encoded;
                if(encodedMap.containsKey(key)){//this map defines top level entries
                    rtrn.putAll(encodedMap);
                }else{//the output map must include key so nest encodedMap under key
                    rtrn.put(key, encodedMap);
                }
            }else {//all other objects are placed under key
                rtrn.put(key, encoded);
            }
        }
        if(!cmd.getWith().isEmpty()){
            rtrn.put(WITH,cmd.getWith());
        }
        if(cmd.hasTimers()){
            LinkedHashMap<Object,Object> timers = new LinkedHashMap<>();
            cmd.getTimeouts().forEach(timeout->{
                List<Object> entries = new LinkedList<>();
                cmd.getTimers(timeout).forEach(entry->{
                    addCmd(entry,entries);
                });
                timers.put(StringUtil.toHms(timeout),entries);
            });
            rtrn.put(TIMER,timers);
        }
        if(cmd.hasSignalWatchers()){
            LinkedHashMap<Object,Object> map = new LinkedHashMap<>();
            cmd.getSignalNames().forEach(name->{
                List<Object> entries = new LinkedList<>();
                cmd.getSignal(name).forEach(entry->{
                    entries.add(defer(entry));
                });
                map.put(name,entries);
            });
            rtrn.put(ONSIGNAL,map);
        }
        if(cmd.hasWatchers()){
            List<Object> watchers = new ArrayList<>();
            cmd.getWatchers().forEach(watcher->{
                watchers.add(defer(watcher));
            });
            rtrn.put(WATCH,watchers);
        }
        if(cmd.hasThens()){
            List<Object> thens = new ArrayList<>();
            cmd.getThens().forEach(then->{
                thens.add(defer(then));
            });
            rtrn.put(THEN,thens);
        }

        return rtrn;
    }

    @Override
    public void setDefer(Defer defer) {
        this.defer = defer;
    }
}
