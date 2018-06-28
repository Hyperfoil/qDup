package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.CommandResult;
import perf.qdup.cmd.Context;

public class Exec extends Cmd {

    private String command;

    public Exec(String command){
        this.command = command;
    }

    public String getCommand(){return command;}

    @Override
    public void run(String input, Context context, CommandResult result) {
        String populatedCommand = Cmd.populateStateVariables(getCommand(),this,context.getState());

        context.getSession().exec(populatedCommand,(response)->{
            result.next(this,response);
        });

    }

    @Override
    public Cmd copy() {
        return new Exec(getCommand());
    }
}
