package io.hyperfoil.tools.qdup;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class SecretFilter {

   public static final String SECRET_NAME_PREFIX = "_";

   public static final String REPLACEMENT = "********";

   private Set<String> secrets;

   public SecretFilter(){
      this.secrets = new HashSet<>();
   }

   public void addSecret(String secret){
      secrets.add(secret);
   }
   public void loadSecrets(SecretFilter filter){
      secrets.addAll(filter.secrets);
   }

   //This is an easy way to replace a bunch of strings but what is the performance hit?
   //TODO https://stackoverflow.com/questions/1326682/java-replacing-multiple-different-substring-in-a-string-at-once-or-in-the-most
   public String filter(String input){
      String rtrn = input;
      for(String secret : secrets){
         rtrn = rtrn.replaceAll(secret,REPLACEMENT);
      }
      return rtrn;
   }
   public Set<String> getSecrets(){return Collections.unmodifiableSet(secrets);}
   public int size(){return secrets.size();}

}
