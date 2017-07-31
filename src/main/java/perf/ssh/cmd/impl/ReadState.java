package perf.ssh.cmd.impl;

import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.Context;
import perf.ssh.cmd.CommandResult;

public class ReadState extends Cmd {

    private String key;
    public ReadState(String key){
        this.key = key;
    }

    public String getKey(){return key;}


    @Override
    public String toString(){
        return "read-state "+key;
    }

    @Override
    protected void run(String input, Context context, CommandResult result) {
        String value = context.getState().get(key);
        if(value==null || value.isEmpty()){
            result.skip(this,input);
        }
        result.next(this,value);
    }

    @Override
    protected Cmd clone() {
        return new ReadState(key);
    }
}
