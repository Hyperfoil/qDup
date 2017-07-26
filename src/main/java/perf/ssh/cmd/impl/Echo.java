package perf.ssh.cmd.impl;

import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.CommandContext;
import perf.ssh.cmd.CommandResult;

public class Echo extends Cmd {
    public Echo(){}

    @Override
    protected void run(String input, CommandContext context, CommandResult result) {
        System.out.println(input); //TODO should echo also go to the run logger?
        result.next(this,input);
    }

    @Override
    protected Cmd clone() {
        return new Echo();
    }
    @Override
    public String toString(){return "echo";}
}
