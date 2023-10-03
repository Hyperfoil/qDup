package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.yaup.json.Json;

import java.util.*;
import java.util.stream.Collectors;

public class SecretFilter {

   public static final String SECRET_NAME_PREFIX = "_";

   public static final String REPLACEMENT = "********";

   private SortedSet<String> secrets;

   public SecretFilter(){
      //sorts longest to shortest then alphabetically
      this.secrets = new TreeSet<>((a,b)->{
         return a.length() != b.length() ? b.length() - a.length() : a.compareTo(b);
      });
   }

   public void addSecret(String secret){
      if(secret == null || secret.isEmpty()){

      } else {
         secrets.add(secret);
      }
   }
   public void loadSecrets(SecretFilter filter){
      secrets.addAll(filter.secrets);
   }

   //This is an easy way to replace a bunch of strings but what is the performance hit?
   //TODO https://stackoverflow.com/questions/1326682/java-replacing-multiple-different-substring-in-a-string-at-once-or-in-the-most
   public String filter(String input){
      String rtrn = input;
      if (input != null) {
         for(String secret : secrets){
            rtrn = rtrn.replace(secret,REPLACEMENT);
         }
      }
      return rtrn;
   }
   public Json filter(Json json){
      Json rtrn = json.clone();
      Queue<Json> todo = new LinkedList<>();
      todo.add(rtrn);
      while(!todo.isEmpty()){
         Json target = todo.remove();
         target.forEach((key,value)->{
            if(value instanceof String){
               target.set(key,filter((String)value));
            }else if (value instanceof Json){
               todo.add((Json)value);
            }
         });
      }
      return rtrn;
   }
   public Set<String> getSecrets(){return Collections.unmodifiableSet(secrets);}
   public int size(){return secrets.size();}

}
