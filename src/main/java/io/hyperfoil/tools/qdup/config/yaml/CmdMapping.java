package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.yaup.Sets;
import io.hyperfoil.tools.yaup.StringUtil;
import io.hyperfoil.tools.yaup.json.Json;
import io.hyperfoil.tools.yaup.yaml.Defer;
import io.hyperfoil.tools.yaup.yaml.Mapping;
import io.hyperfoil.tools.yaup.yaml.WithDefer;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

public class CmdMapping<T extends Cmd> implements Mapping, WithDefer {
    public static final String WITH = "with";
    public static final String THEN = "then";
    public static final String TIMER = "timer";
    public static final String ON_SIGNAL = "on-signal";
    public static final String WATCH = "watch";
    public static final String SILENT = "silent";
    public static final String PREFIX = "prefix";
    public static final String SUFFIX = "suffix";
    public static final String SEPARATOR = "separator";
    public static final String JS_PREFIX = "js-prefix";




    public static final Set<String> COMMAND_KEYS = Collections.unmodifiableSet(Sets.of(WITH,THEN,TIMER, ON_SIGNAL,WATCH,SILENT,PREFIX,SUFFIX,SEPARATOR,JS_PREFIX));

    private Defer defer = null;
    final String key;
    final CmdEncoder<T> encoder;

    public CmdMapping(String key,CmdEncoder<T> encoder){
        this.key = key;
        this.encoder = encoder;
    }

    public CmdEncoder<T> getEncoder(){return encoder;}
    public String getKey(){return key;}

    private void addCmd(Cmd cmd,List<Object> encoded){
        if(cmd.copy() == null){
            //commands that do not clone should not be included
        }else if(cmd instanceof Cmd.NO_OP){
            Queue<Cmd> toAdd = new LinkedBlockingQueue<>();
            toAdd.add(cmd);
            while(!toAdd.isEmpty()){
                Cmd target = toAdd.poll();
                if(target instanceof Cmd.NO_OP){
                    target.getThens().forEach(toAdd::add);
                }else {
                    Object obj = defer(target);
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
        if(cmd.hasPatternPrefix()){
            rtrn.put(PREFIX,cmd.getPatternPrefix());
        }
        if(cmd.hasPatternSuffix()){
            rtrn.put(SUFFIX,cmd.getPatternSuffix());
        }
        if(cmd.hasPatternSeparator()){
            rtrn.put(SEPARATOR,cmd.getPatternSeparator());
        }
        if(cmd.hasPatternJavascriptPrefix()){
            rtrn.put(JS_PREFIX,cmd.getPatternJavascriptPrefix());
        }
        if(!cmd.getWith().isEmpty()){
            rtrn.put(WITH, Json.toObjectMap(cmd.getWith()));
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
            rtrn.put(ON_SIGNAL,map);
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
                if(then.copy()!=null) {//a command that does not copy should not be saved
                    thens.add(defer(then));
                }
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
