package io.hyperfoil.tools.qdup.cmd;

import io.hyperfoil.tools.qdup.cmd.impl.Regex;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public abstract class CmdWithElse extends Cmd{

    protected List<Cmd> elses;

    public CmdWithElse(){
        this.elses = new LinkedList<>();
    }

    public boolean hasElse() {
        return !elses.isEmpty();
    }

    public List<Cmd> getElses() {
        return Collections.unmodifiableList(elses);
    }

    public CmdWithElse onElse(Cmd command) {
        command.setParent(this);
        elses.add(command);
        return this;
    }

    @Override
    public <T> void walk(BiFunction<Cmd,Boolean,T> converter, boolean isWatching, List<T> rtrn){
        super.walk(converter,isWatching,rtrn);
        if(hasElse()){
            this.getElses().forEach(child->child.walk(converter,isWatching,rtrn));
        }
    }

    @Override
    public Cmd getSkip(){
        if(hasElse()){
            return getElses().get(0);
        }else{
            return super.getSkip();
        }
    }

    @Override
    public Cmd previousChildOrParent(Cmd child){
        boolean inThens = thens.contains(child);
        int cmdIndex = inThens ? thens.indexOf(child) : getElses().indexOf(child);
        if(cmdIndex < 0){
            return null;
        }else if (cmdIndex == 0){
            return this;
        }else{
            return inThens ? thens.get(cmdIndex-1) : getElses().get(cmdIndex-1);
        }
    }

    @Override
    public Cmd nextChild(Cmd child){
        Cmd rtrn = null;
        int cmdIndex = thens.indexOf(child);
        if(cmdIndex < 0){
            //not a then child
            cmdIndex = elses.indexOf(child);
            if(cmdIndex < 0){
                //not an else child either
                //TODO throw error because current command is not a child?
            }else if (cmdIndex == elses.size() -1){

            }else{
                rtrn = elses.get(cmdIndex+1);
            }

        }else if (cmdIndex == thens.size() -1 ){
        }else{
            rtrn = thens.get(cmdIndex+1);
        }
        return rtrn;
    }

}
