package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;


/**
 * Command to send text to the remote shell while there is a running Sh command. This can be used to control interactive CLI commands like top.
 */
public class SendText extends Cmd {

   private final String text;
   private String populatedText;

   public SendText(String text){
      super(true);   //silent so it does not log
      this.text = text;
      this.populatedText = text;
   }

   public String getText(){return text;}

   @Override
   public void run(String input, Context context) {
      populatedText = Cmd.populateStateVariables(text,this,context.getState());
      context.getSession().response(populatedText);
      context.next(input);
   }

   @Override
   public String getLogOutput(String output,Context context){
      return null;
   }

   @Override
   public Cmd copy() {
      return new SendText(text);
   }
}
