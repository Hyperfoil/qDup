package io.hyperfoil.tools.qdup.cmd;

import io.hyperfoil.tools.qdup.State;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

class CmdStateRefMap implements Map<Object, Object> {

   private Cmd cmd;
   private State state;
   private Cmd.Ref ref;

   public CmdStateRefMap(Cmd cmd, State state, Cmd.Ref ref) {
      this.cmd = cmd;
      this.state = state;
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
      if (cmd != null) {
         if (cmd.hasWith(key.toString())) {
            return true;
         }
      }
      if (ref != null) {
         Object rtrn = null;
         Cmd.Ref targeetRef = ref;
         do {
            if (targeetRef.getCommand() != null && targeetRef.getCommand().hasWith(key.toString()) ) {
               rtrn = targeetRef.getCommand().getWith().get(key);
            }
         } while ((targeetRef = targeetRef.getParent()) != null && rtrn == null);
         if (rtrn != null) {
            return true;
         }
      }
      if (state != null) {
         if (state.has(key.toString())) {
            return true;
         }
      }
      return false;
   }

   @Override
   public boolean containsValue(Object value) {
      throw new UnsupportedOperationException("this is a read only map");
   }

   @Override
   public Object get(Object key) {
      if (cmd != null) {
         if (cmd.hasWith(key.toString())) {
            return cmd.getWith(key.toString());
         }
      }
      if (ref != null) {
         Object rtrn = null;
         Cmd.Ref targeetRef = ref;
         do {
            if (targeetRef.getCommand() != null && targeetRef.getCommand().getWith().has(key)) {
               rtrn = targeetRef.getCommand().getWith().get(key);
            }
         } while ((targeetRef = targeetRef.getParent()) != null && rtrn == null);
         if( rtrn != null ){
            return rtrn;
         }
      }
      if (state != null) {
         if (state.has(key.toString())) {
            return state.get(key.toString());
         }
      }
      return null;
   }

   @Override
   public Object put(Object key, Object value) {
      throw new UnsupportedOperationException("this is a read only map");
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
      return Collections.EMPTY_SET;
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
}
