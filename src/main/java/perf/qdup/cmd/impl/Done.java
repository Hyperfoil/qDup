package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;

public class Done extends Cmd {
    @Override
    public void run(String input, Context context) {
        context.getRunLogger().info("done");
        context.done();
    }

    @Override
    public Cmd copy() {
        return new Done();
    }

    @Override
    public String toString(){return "done";}
}
