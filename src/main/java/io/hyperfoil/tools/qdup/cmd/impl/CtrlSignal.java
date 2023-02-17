package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.yaup.AsciiArt;

public class CtrlSignal extends Cmd {

   private final String name;
   private final char signal;

   public CtrlSignal(String name,char signal){
      this.name = name;
      this.signal = signal;
   }

   @Override
   public void run(String input, Context context) {
      context.getSession().ctrl(signal);
      context.next(input); //now waits for shell to return prompt

   }

   @Override
   public Cmd copy() {
      return new CtrlSignal(name,signal);
   }

   @Override
   public String getLogOutput(String output, Context context) {
      return name;
   }

   @Override
   public String toString(){return name;}
}
