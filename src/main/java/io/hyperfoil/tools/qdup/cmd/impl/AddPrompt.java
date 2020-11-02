package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;

public class AddPrompt extends Cmd {


   private String prompt;
   private String populatedPrompt = null;

   public AddPrompt(String prompt){
      this.prompt = prompt;
   }


   public String getPrompt(){return prompt;}


   @Override
   public void run(String input, Context context) {
       populatedPrompt = Cmd.populateStateVariables(prompt,this,context);

       context.getSession().addPrompt(populatedPrompt);

      context.next(input);
   }


   @Override
   public String getLogOutput(String output, Context context) {
      return populatedPrompt == null ? prompt : populatedPrompt;
   }

   @Override
   public Cmd copy() {
      return new AddPrompt(getPrompt());
   }
}
