package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;
import perf.qdup.cmd.CommandResult;

public class Echo extends Cmd {
    public Echo(){}

    @Override
    public void run(String input, Context context, CommandResult result) {
        System.out.println(input); //TODO should echo also go to the run logger?
        result.next(this,input);
    }

    @Override
    public Cmd copy() {
        return new Echo();
    }
    @Override
    public String toString(){return "echo:";}
}
