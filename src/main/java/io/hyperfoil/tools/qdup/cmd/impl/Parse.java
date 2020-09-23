package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.parse.JsonConsumer;
import io.hyperfoil.tools.parse.Parser;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.yaup.json.Json;

import java.util.function.Supplier;

public class Parse extends Cmd {

   private String config;

   public Parse(String cofig) {
      this.config = config;
   }

   public String getConfig() {
      return config;
   }

   @Override
   public void run(String input, Context context) {
      String populatedConfig = Cmd.populateStateVariables(getConfig(),this,context.getState());
      Object parserConfig = populatedConfig;
      if(Json.isJsonLike(populatedConfig)){
         parserConfig = Json.fromString(populatedConfig);
      }
      if(parserConfig == null){
         abort("failed to create parser from "+populatedConfig);
      }
      if(Cmd.hasStateReference(populatedConfig,this)){
         abort("failed to populate template pattern in "+populatedConfig);
      }
      Parser p = Parser.fromJson(parserConfig);
      Json result = new Json();
      p.add(json ->result.add(json));

      input.lines().forEach(p::onLine);
      p.close();
      if(result.isEmpty()){
         context.skip(input);
      }else if (result.size()==1){
         context.next(result.get(0).toString());
      }else{
         context.next(result.toString());
      }
   }

   @Override //disables default logging after the command finishes
   public void postRun(String output, Context context) {
   }

   @Override
   public Cmd copy() {
      return new Parse(getConfig());
   }

   @Override
   public String getLogOutput(String output, Context context) {
      return "parse: {...}";
   }
}