package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;

public class Exec extends Cmd {

    private String command;

    public Exec(String command){
        this.command = command;
    }

    public String getCommand(){return command;}

    @Override
    public void run(String input, Context context) {
        String populatedCommand = Cmd.populateStateVariables(getCommand(),this,context.getState());

        context.getSession().exec(populatedCommand,(response)->{
            context.next(response);
        });

    }

    @Override
    public Cmd copy() {
        return new Exec(getCommand());
    }
}
