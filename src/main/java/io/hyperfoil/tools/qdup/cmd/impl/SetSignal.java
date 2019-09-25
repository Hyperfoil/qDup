package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;

public class SetSignal extends Cmd {

   private String name;
   private String populatedName;
   private String initial;
   private String populatedInitial;

   public SetSignal(String name,String initial){
      super(true);
      this.name = name;
      this.initial = initial;
   }

   public String getName(){return name;}
   public String getInitial(){return initial;}

   @Override
   public void run(String input, Context context) {
      populatedName = Cmd.populateStateVariables(name,this,context.getState());
      populatedInitial = Cmd.populateStateVariables(initial,this,context.getState());
      try {
         int intialLatches = Integer.parseInt(populatedInitial);
         context.getCoordinator().initialize(populatedName,intialLatches);
      }catch(NumberFormatException e){
         logger.error("set-signal: {} could not initialize {} due to NumberFormatException",populatedName,populatedInitial);
      }
      context.next(input);
   }

   @Override
   public Cmd copy() {
      return new SetSignal(name,initial);
   }

   @Override public String toString(){return "set-signal: "+name+" "+initial;}

   @Override
   public String getLogOutput(String output,Context context){
      String useName = populatedName!=null ? populatedName : name;
      String useInitial = populatedInitial!=null ? populatedInitial : initial;
      if(useName.isEmpty()){
         return "";
      }else {
         return "set-signal: " + useName+" "+useInitial;
      }
   }

}
