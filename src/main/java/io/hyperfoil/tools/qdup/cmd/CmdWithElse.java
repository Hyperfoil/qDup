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
}
