package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.CmdWithElse;
import io.hyperfoil.tools.qdup.cmd.Context;

import java.util.LinkedList;
import java.util.List;

public class ReadState extends CmdWithElse {

    private String key;
    private String populatedKey;
    private boolean ran = false;
    public ReadState(String key){
        this.key = key;
        setStateScan(false); //disable state-scan so un-used state can be checked
    }

    public String getKey(){return key;}

    @Override
    public String toString(){
        return "read-state: "+key;
    }


    private boolean stateMissing(){
        return populatedKey == null || populatedKey.isEmpty() || Cmd.hasStateReference(populatedKey,this);
    }

    @Override
    public void run(String input, Context context) {
        ran = true;
        //use getStateValue in case it is in WITH or contest
        Object value = Cmd.populateStateVariables(key, this, context.getState(), null);
        populatedKey = value == null ? "" : value.toString();
        if(stateMissing()){
            if(hasElse()){
                context.next(input);
            }else {
                context.skip(input);
            }
        }else{
//TODO should we also support read-state: KEY in addition to read-state: ${{KEY}}
//            if(context.getState().has(populatedKey,true)){
//                context.next(context.getState().get(populatedKey).toString());
//            }
            context.next(populatedKey);
        }
    }

    @Override
    public Cmd getNext(){
        if(ran && stateMissing()){
            if(hasElse()){
                return getElses().get(0);
            }else{
                return super.getNext();
            }
        }else{
            return super.getNext();
        }
    }

    @Override
    public Cmd copy() {
        return new ReadState(key);
    }

    @Override
    public String getLogOutput(String output,Context context){
        return "read-state: "+ (populatedKey == null ? key : populatedKey);
    }
}
