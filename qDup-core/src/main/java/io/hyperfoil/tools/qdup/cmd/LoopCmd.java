package io.hyperfoil.tools.qdup.cmd;

public abstract class LoopCmd extends Cmd {

   protected class Callback extends Cmd {

      private int runCount = 0;

      public int getRunCount(){return  runCount;}
      public void resetRunCount(){runCount = 0;}

      @Override
      public Cmd getNext(){
         return LoopCmd.this;
      }

      @Override
      public void run(String input, Context context) {
         runCount++;
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

   protected Callback callback;

   protected Callback getCallback(){return callback;}

   public LoopCmd(boolean silent){
      super(silent);
      this.callback = new Callback();
      then(callback);
   }

   @Override
   public void ensureNewSet(){
      thens.remove(callback);
      super.ensureNewSet();
      thens.add(callback);
   }

   @Override
   public Cmd then(Cmd command) {
      if(command!=null) {
         command.setParent(this);
         if (hasThens()) {
            thens.addBeforeLast(command);
         } else {
            thens.add(command);
         }
      }
      assert callback.equals(thens.getLast());
      return this;
   }

}
