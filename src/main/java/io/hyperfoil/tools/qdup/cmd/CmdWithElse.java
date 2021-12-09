package io.hyperfoil.tools.qdup.cmd;

import io.hyperfoil.tools.qdup.cmd.impl.Regex;
import io.hyperfoil.tools.qdup.config.RunRule;

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
    public <T> void walk(BiFunction<Cmd, RunRule.Location,T> converter, RunRule.Location location, List<T> rtrn){
        super.walk(converter,location,rtrn);
        if(hasElse()){
            this.getElses().forEach(child->child.walk(converter,location,rtrn));
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
        Cmd rtrn = null;
        if(thens.contains(child)){
            rtrn = thens.previousCmdOrParent(child);
        }else{
            int cmdIndex = getElses().indexOf(child);
            if (cmdIndex == 0){
                rtrn = this;
            } else if (cmdIndex > 0) {
                rtrn = getElses().get(cmdIndex-1);
            }
        }
        return rtrn;
    }

    @Override
    public Cmd nextChild(Cmd child){
        Cmd rtrn = null;
        if(thens.contains(child)){
            rtrn = thens.getNextSibling(child);
        }else{
            int cmdIndex = elses.indexOf(child);
            if (cmdIndex > -1 && cmdIndex < elses.size()-1){
                rtrn = elses.get(cmdIndex+1);
            }
        }
        return rtrn;
    }

    @Override
    public Cmd deepCopy() {
        Cmd rtrn = super.deepCopy();
        if(rtrn instanceof CmdWithElse){
            CmdWithElse cmdWithElse = (CmdWithElse)rtrn;
            if(hasElse()){
                getElses().forEach(c->cmdWithElse.onElse(c.deepCopy()));
            }
        }
        return rtrn;
    }
}
