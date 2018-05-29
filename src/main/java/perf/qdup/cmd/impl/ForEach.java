package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.CommandResult;
import perf.qdup.cmd.Context;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ForEach extends Cmd.LoopCmd {

    private String name;
    private String input;
    private List<String> split;
    private int index;

    public ForEach(String name){
        this(name,"");
    }
    public ForEach(String name,String input){
        this.name = name;
        this.input = input;
        this.split = null;
        this.index = -1;
    }

    @Override
    public void run(String input, Context context, CommandResult result) {
        if(split == null){
            String toSplit = this.input.isEmpty() ? input.trim() : Cmd.populateStateVariables(this.input,this,context.getState());

            split = Collections.emptyList();

            if(toSplit.contains("\n")){
                split = Arrays.asList(toSplit.split("\r?\n"));
            }else {
                //TODO [a, b, c]
                //TODO [a b c]
                //TODO a, b, c
                //TODO a b c
            }
        }

        if(split!=null && !split.isEmpty()){
            String populatedName = Cmd.populateStateVariables(this.name,this,context.getState());
            index++;
            if(index < split.size()){
                String value = split.get(index).replaceAll("\r|\n","");//defensive against trailing newline characters
                with(populatedName,value);
                result.next(this,value);
            }else{
                result.skip(this,input);
            }
        }
    }

    @Override
    protected Cmd clone() {
        return new ForEach(this.name,this.input).with(this.with);
    }


    @Override
    public String toString(){
        return "for-each: "+name+(this.input!=null?this.input:"");
    }
}
