package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.impl.Regex;

import java.util.*;

public class RegexMapping extends CmdMapping {

   public static final String MISS = "miss";
   public static final String PATTERN = "pattern";
   public static final String ELSE = "else";


   public RegexMapping() {
      super("regex", new CmdEncoder() {
         @Override
         public Object encode(Cmd cmd) {
            if(cmd instanceof Regex){
               Regex r = (Regex)cmd;
               if(r.isMiss()){
                  Map<Object,Object> regexMap = new HashMap<>();
                  regexMap.put(PATTERN,r.getPattern());
                  regexMap.put(MISS,r.isMiss());
                  return regexMap;
               }else{
                  return r.getPattern();
               }
            }else{
               return null;
            }
         }
      });
   }
   @Override
   public Map<Object, Object> getMap(Object o) {
      Map<Object, Object> rtrn = super.getMap(o);
      if (o instanceof Regex) {
         Regex r = (Regex) o;
         if (r.hasElse()) {
            List<Object> elses = new ArrayList<>();
            r.getElses().forEach(miss -> {
               Object defered = defer(miss);
               elses.add(defered);
            });
            rtrn.put(ELSE, elses);
         }
      }
      return rtrn;
   }
}
