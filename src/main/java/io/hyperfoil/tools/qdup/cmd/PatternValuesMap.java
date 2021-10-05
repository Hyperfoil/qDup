package io.hyperfoil.tools.qdup.cmd;

import io.hyperfoil.tools.qdup.Coordinator;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.yaup.AsciiArt;
import io.hyperfoil.tools.yaup.Sets;
import io.hyperfoil.tools.yaup.json.Json;
import io.hyperfoil.tools.yaup.json.ValueConverter;
import io.hyperfoil.tools.yaup.json.graaljs.JsonProxy;
import io.hyperfoil.tools.yaup.json.graaljs.JsonProxyObject;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provides a Map facade over the command's state and with variables
 * This supports Graaljs treating state as a simple javascript object and lets yaup StringUtil.populatePattern
 * use the current variables from any scope (State or with's)
 *
 * Needs to be a ProxyObject to support state['key']=... in jsCmd
 *
 */
//TODO support javascript map and array functions (push, pop, map, filter, etc)
public class PatternValuesMap implements Map<Object, Object>  , ProxyObject {

   public static final String QDUP_GLOBAL = "ENV";
   public static final String QDUP_GLOBAL_STATE = "state";
   public static final String QDUP_GLOBAL_SIGNALS = "signals";
   public static final String QDUP_GLOBAL_COUNTERS = "counters";
   public static final String QDUP_GLOBAL_TIMESTAMPS = "timestamps";
   public static final Set<String> QDUP_GLOBAL_KEYS = Sets.of(QDUP_GLOBAL_STATE,QDUP_GLOBAL_SIGNALS,QDUP_GLOBAL_COUNTERS,QDUP_GLOBAL_TIMESTAMPS);

   private Cmd cmd;
   private State state;
   private Coordinator coordinator;
   private Json timestamps;
   private Cmd.Ref ref;

   public PatternValuesMap(Cmd cmd, Context context, Cmd.Ref ref) {
      this(cmd,
         context!=null ? context.getState() : null,
         context!=null ? context.getCoordinator() : null,
         context!=null ? context.getTimestamps() : new Json(),
         ref);
   }
   public PatternValuesMap(Cmd cmd, State state, Coordinator coordinator, Json timestamps,Cmd.Ref ref) {
      this.cmd = cmd;
      this.state = state == null ? new State(State.RUN_PREFIX) : state;
      this.coordinator = coordinator;
      this.timestamps = timestamps==null ? new Json(false) : timestamps;
      this.ref = ref;
   }

   @Override
   public int size() {
      return 0;
   }

   @Override
   public boolean isEmpty() {
      return false;
   }

   @Override
   public boolean containsKey(Object key) {
      boolean rtrn = false;
      if (key.toString().equals(QDUP_GLOBAL)) {
         rtrn = true;
      } else if (key.toString().startsWith(QDUP_GLOBAL+".")){
         String subKey = key.toString().substring(QDUP_GLOBAL.length() + 1);//+1 for the .
         Object found = Json.find(populateQdupGlobal(),key.toString().replace(QDUP_GLOBAL,"$"),null);
         boolean isGlobalSubkey = QDUP_GLOBAL_KEYS.stream().anyMatch(global->subKey.startsWith(global));
         rtrn = isGlobalSubkey;
      }
      if (cmd != null && !rtrn) {
         if (cmd.hasWith(key.toString())) {
            rtrn = true;
         }
      }
      if (ref != null) {
         Object tmp = null;
         Cmd.Ref target = ref;
         do {
            if (target.getCommand() != null && target.getCommand().hasWith(key.toString()) ) {
               tmp = target.getCommand().getWith(key.toString(),state);
            }
         } while ((target = target.getParent()) != null && tmp == null);
         if (tmp != null) {
            rtrn = true;
         }
      }
      if (state != null && !rtrn) {
         if (state.has(key.toString(),true)) {
            rtrn = true;
         }
      }
      return rtrn;
   }

   @Override
   public boolean containsValue(Object value) {
      throw new UnsupportedOperationException("this is a read only map");
   }

   private Json populateQdupGlobal(){
      Json rtrn = new Json(false);
      if(state!=null) {
         rtrn.set("state", state.toJson());
      }
      if(coordinator != null) {
         Json latches = new Json(false);
         coordinator.getLatches().forEach((k,v)->latches.set(k,v));
         rtrn.set("signals",latches);

         Json counter = new Json(false);
         coordinator.getCounters().forEach((k, v) -> counter.set(k, v));
         rtrn.set("counters", counter);
      }
      rtrn.set("timestamps",timestamps);
      return rtrn;
   }

   @Override
   public Object get(Object key) {
      Object rtrn = null;
      if(QDUP_GLOBAL.equals(key)){
         rtrn = populateQdupGlobal();
      }else if(key.toString().startsWith(QDUP_GLOBAL)){
         String remainingKey = key.toString().substring(QDUP_GLOBAL.length());
         if(remainingKey.isBlank()){
            return populateQdupGlobal();
         }else if (remainingKey.startsWith(".")){
            remainingKey = remainingKey.substring(1);
            Object found = Json.find(populateQdupGlobal(),remainingKey);
            if(found!=null){
               rtrn = found;
            }
         }
      }
      if (cmd != null && rtrn == null) {
         //only use value from with if it does not contain a reference back to the same key
         //this support with: {FOO: ${{FOO}} } so script variables can be mapped to a state variable with the same name
         //technically that does not need to happen but it should be supported
         if (cmd.hasWith(key.toString())){

            Object found = cmd.getWith(key.toString(),state);
            if(found == null){
               //this is an error, what causes this

            }
            if(!found.toString().contains(cmd.getPatternPrefix()+key.toString())){
               rtrn = found;
            }else{
            }

         }
      }
      if (ref != null && rtrn == null) {
         Object tmp = null;
         Cmd.Ref target = ref;
         do {
            if (target.getCommand() != null && target.getCommand().hasWith(key.toString()) && ! target.getCommand().getWith(key.toString()).toString().contains(cmd.getPatternPrefix()+key.toString())) {
               tmp = target.getCommand().hasWith(key.toString()) ? target.getCommand().getWith(key.toString()) : null;
            }
         } while ((target = target.getParent()) != null && tmp == null);
         if( tmp != null ){
            rtrn = tmp;
         }
      }
      if (state != null && rtrn == null) {
         if (state.has(key.toString())) {
            rtrn =  state.get(key.toString());
         }
      }
      return rtrn;
   }


   @Override
   public Object put(Object key, Object value) {
      if(state!=null){
         state.set(key.toString(),value);
         return null;
      } else {
         throw new UnsupportedOperationException("this is a read only map");
      }
   }

   @Override
   public Object remove(Object key) {
      throw new UnsupportedOperationException("this is a read only map");
   }

   @Override
   public void putAll(Map<?, ?> m) {
      throw new UnsupportedOperationException("this is a read only map");
   }

   @Override
   public void clear() {
      throw new UnsupportedOperationException("this is a read only map");
   }

   @Override
   public Set<Object> keySet() {
      Set<Object> rtrn = new HashSet<>();
      if(cmd!=null){
         cmd.getVisibleWith().keySet().forEach(key->rtrn.add(key));
      }
      if(state!=null){
         rtrn.addAll(state.getVisibleKeys());
      }
      if(ref!=null){
         Cmd.Ref target = ref;
         do {
            rtrn.addAll(target.getCommand().getWith().keys());
         }while( (target=ref.getParent())!=null);
      }

      return rtrn;
   }

   @Override
   public Collection<Object> values() {
      return Collections.EMPTY_SET;
   }

   @Override
   public Set<Entry<Object, Object>> entrySet() {
      return Collections.EMPTY_SET;
   }

   @Override
   public Object getOrDefault(Object key, Object defaultValue) {
      Object rtrn = get(key);
      if (rtrn == null) {
         rtrn = defaultValue;
      }
      return rtrn;
   }

   @Override
   public void forEach(BiConsumer<? super Object, ? super Object> action) {
      throw new UnsupportedOperationException("this is a read only map");
   }

   @Override
   public void replaceAll(BiFunction<? super Object, ? super Object, ?> function) {
      throw new UnsupportedOperationException("this is a read only map");
   }

   @Override
   public Object putIfAbsent(Object key, Object value) {
      throw new UnsupportedOperationException("this is a read only map");
   }

   @Override
   public boolean remove(Object key, Object value) {
      throw new UnsupportedOperationException("this is a read only map");
   }

   @Override
   public boolean replace(Object key, Object oldValue, Object newValue) {
      throw new UnsupportedOperationException("this is a read only map");
   }

   @Override
   public Object replace(Object key, Object value) {
      throw new UnsupportedOperationException("this is a read only map");
   }

   @Override
   public Object computeIfAbsent(Object key, Function<? super Object, ?> mappingFunction) {
      throw new UnsupportedOperationException("this is a read only map");
   }

   @Override
   public Object computeIfPresent(Object key, BiFunction<? super Object, ? super Object, ?> remappingFunction) {
      throw new UnsupportedOperationException("this is a read only map");
   }

   @Override
   public Object compute(Object key, BiFunction<? super Object, ? super Object, ?> remappingFunction) {
      throw new UnsupportedOperationException("this is a read only map");
   }

   @Override
   public Object merge(Object key, Object value, BiFunction<? super Object, ? super Object, ?> remappingFunction) {
      throw new UnsupportedOperationException("this is a read only map");
   }

   // ProxyObject
   //

   @Override
   public Object getMember(String key) {
      Object rtrn = get(key);
      if(rtrn instanceof Json){
         rtrn = JsonProxy.create((Json) rtrn);
      }
      return rtrn;
   }

   @Override
   public Object getMemberKeys() {
      Set rtrn = keySet();
      return rtrn.toArray(new Object[0]);
   }

   @Override
   public boolean hasMember(String key) {
      if (Set.of("containsKey").contains(key)) {
         return true;
      } else {
         return containsKey(key);
      }
   }

   @Override
   public void putMember(String key, Value value)
   {
      state.set(key, ValueConverter.convert(value));
   }



}
