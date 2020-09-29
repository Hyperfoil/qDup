package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.CmdWithElse;
import io.hyperfoil.tools.qdup.cmd.Context;

import java.util.LinkedList;
import java.util.List;

public class ReadSignal extends CmdWithElse {

    private String name;
    private String populatedName;
    private List<Cmd> elses;
    private boolean ran = false;

    public ReadSignal(String name){
        this.name = name;
        this.elses = new LinkedList<>();
    }

    public String getName(){return name;}

    @Override
    public String toString(){
        return "read-signal: "+ name;
    }

    @Override
    public void run(String input, Context context) {
        ran = true;
        //use getStateValue in case it is in WITH or contest
        Object value = Cmd.populateStateVariables(name, this, context.getState(), null);
        populatedName = value == null ? "" : value.toString();
        if(populatedName == null || populatedName.isEmpty()){
            context.skip(input);
        }else{
            if(context.getCoordinator().getSignalCount(populatedName) <= 0) {
                context.next(input);
            }else{
                context.skip(input);
            }
        }
    }

    @Override
    public Cmd copy() {
        return new ReadSignal(name);
    }

    @Override
    public String getLogOutput(String output,Context context){
        return "read-signal: "+ populatedName == null ? name : populatedName;
    }
}
