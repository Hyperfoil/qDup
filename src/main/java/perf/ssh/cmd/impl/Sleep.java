package perf.ssh.cmd.impl;

import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.Context;
import perf.ssh.cmd.CommandResult;

public class Sleep extends Cmd {
    long amount;
    public Sleep(long amount){this.amount = amount;}
    @Override
    protected void run(String input, Context context, CommandResult result) {
        context.schedule(this,() -> result.next(this,input),amount);
//            try {
//                Thread.sleep(amount);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//                Thread.interrupted();
//            } finally {
//                result.next(this,input);
//            }

    }

    @Override
    protected Cmd clone() {
        return new Sleep(this.amount).with(this.with);
    }

    @Override public String toString(){return "sleep: "+amount;}
}
