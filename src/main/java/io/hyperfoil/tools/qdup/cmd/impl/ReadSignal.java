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
    private int remaining = Integer.MAX_VALUE;

    public ReadSignal(String name){
        this.name = name;
        this.elses = new LinkedList<>();
    }

    public String getName(){return name;}

    private int getRemaining(){return remaining;}
    private void setRemaining(int remaining){
        this.remaining = remaining;
    }

    private boolean missingName(){
        return populatedName == null || populatedName.isEmpty();
    }

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
        if(missingName()){
            if(hasElse()){
                context.next(input);
            }else {
                context.skip(input);
            }
        }else{
            setRemaining(context.getCoordinator().getSignalCount(populatedName));
            if(getRemaining() <= 0) {
                context.next(input);
            }else{
                if(hasElse()){
                    context.next(input);
                }else {
                    context.skip(input);
                }
            }
        }
    }

    @Override
    public Cmd getNext(){
        if(missingName()){
            if(hasElse()){
                return getElses().get(0);
            }else{
                return super.getNext();
            }
        }else{
            if(getRemaining() <= 0){
                return super.getNext();
            }else{
                if(hasElse()){
                    return getElses().get(0);
                }else{
                    return super.getNext();
                }
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
