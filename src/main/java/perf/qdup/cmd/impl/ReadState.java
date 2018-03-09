package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;
import perf.qdup.cmd.CommandResult;

public class ReadState extends Cmd {

    private String key;
    public ReadState(String key){
        this.key = key;
    }

    public String getKey(){return key;}


    @Override
    public String toString(){
        return "read-state: "+key;
    }

    @Override
    protected void run(String input, Context context, CommandResult result) {
        //use populateVariable in case it is in WITH or context
        String value = Cmd.populateVariable(key,this,context.getState());
        if(value==null || value.isEmpty()){
            result.skip(this,input);
        }
        result.next(this,value);
    }

    @Override
    protected Cmd clone() {
        return new ReadState(key).with(this.with);
    }
}