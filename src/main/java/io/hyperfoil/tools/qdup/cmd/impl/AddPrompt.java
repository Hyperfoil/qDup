package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;

public class AddPrompt extends Cmd {


   private String prompt;
   private String populatedPrompt = null;
   private boolean isShell = false;

   public AddPrompt(String prompt){
      this(prompt,false);
   }
   public AddPrompt(String prompt,boolean isShell){
      this.prompt = prompt;
      this.isShell = isShell;
   }

   public String getPrompt(){return prompt;}
   public boolean isShell(){return isShell;}

   @Override
   public void run(String input, Context context) {
       populatedPrompt = Cmd.populateStateVariables(prompt,this,context);

       context.getSession().addPrompt(populatedPrompt, isShell);

      context.next(input);
   }


   @Override
   public String getLogOutput(String output, Context context) {
      return populatedPrompt == null ? prompt : populatedPrompt;
   }


   @Override
   public Cmd copy() {
      return new AddPrompt(getPrompt(), isShell());
   }
}
