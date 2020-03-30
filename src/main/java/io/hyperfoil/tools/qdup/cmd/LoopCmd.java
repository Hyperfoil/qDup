package io.hyperfoil.tools.qdup.cmd;

public abstract class LoopCmd extends Cmd {

   private class Callback extends Cmd {

      @Override
      public Cmd getNext(){
         return LoopCmd.this;
      }

      @Override
      public void run(String input, Context context) {
         context.next(input);
      }

      @Override
      public Cmd copy() {return null;}

      @Override
      public String toString(){
         return "callback-["+LoopCmd.this+"]";
      }

      @Override
      public void postRun(String output,Context context){}
   }

   private Callback callback;
   public LoopCmd(boolean silent){
      super(silent);
      this.callback = new Callback();
      then(callback);
   }

   @Override
   public Cmd then(Cmd command) {
      if(command!=null) {
         command.setParent(this);
         if (hasThens()) {
            thens.add(thens.size() - 1, command);
         } else {
            thens.add(command);
         }
      }
      assert callback.equals(thens.get(thens.size()-1));
      return this;
   }

}
