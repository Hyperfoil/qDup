package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;
import perf.qdup.cmd.CommandResult;

public class SetState extends Cmd {

    String key;
    String value;

    public SetState(String key){
        this(key,null);
    }
    public SetState(String key,String value){
        this.key = key;
        this.value = value;
    }

    public String getKey(){return key;}
    public String getValue(){return value;}


    @Override
    public String toString(){
        return "set-state: "+this.key+(this.value==null?"":" "+this.value);
    }

    @Override
    protected void run(String input, Context context, CommandResult result) {
        String value = this.value==null ? input.trim() : this.value;
        context.getState().set(key,value);
        result.next(this,input);
    }

    @Override
    protected Cmd clone() {
        return new SetState(key,value).with(this.with);
    }
}
