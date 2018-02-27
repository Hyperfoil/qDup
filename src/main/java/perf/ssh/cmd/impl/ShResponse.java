package perf.ssh.cmd.impl;

import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.CommandResult;
import perf.ssh.cmd.Context;

public class ShResponse extends Cmd {

    private String response;
    public ShResponse(String response){
        super(true);
        this.response = response;
    }

    public String getResponse(){return response;}

    @Override
    protected void run(String input, Context context, CommandResult result) {
        String populatedResponse = populateStateVariables(response,this,context.getState());
        context.getSession().response(populatedResponse);
        result.next(this,input);
    }

    @Override
    protected Cmd clone() {
        return new ShResponse(this.response).with(this.with);
    }
}
