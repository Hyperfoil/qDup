package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;

public class ReadState extends Cmd {

    private String key;
    private String value;
    public ReadState(String key){
        this.key = key;
    }

    public String getKey(){return key;}


    @Override
    public String toString(){
        return "read-state: "+key;
    }

    @Override
    public void run(String input, Context context) {
        //use populateVariable in case it is in WITH or context
        value = Cmd.populateVariable(key,this,context.getState(),null);
        if(value==null || value.isEmpty()){
            context.skip(input);
        }
        context.next(value);
    }

    @Override
    public Cmd copy() {
        return new ReadState(key);
    }

    @Override
    public String getLogOutput(String output,Context context){
        if(value!=null){
            return "read-state: "+value;
        }else{
            return "read-state: "+value;
        }
    }
}
