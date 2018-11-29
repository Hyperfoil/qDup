package perf.qdup.cmd.impl;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;
import perf.yaup.StringUtil;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ForEach extends Cmd.LoopCmd {

    final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

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
    public static List<String> split(String toSplit){
        List<String> split = new ArrayList<>();
        if(toSplit.contains("\n")){
            split = Arrays.asList(toSplit.split("\r?\n"));
        }else {
            if(toSplit.startsWith("[") && toSplit.endsWith("]")){
                toSplit=toSplit.substring(1,toSplit.length()-1);//remove [ ] around the list
            }
            String found = "";
            while( !(found=StringUtil.findNotQuoted(toSplit,", ")).isEmpty() ){
                found = found.replaceAll("^[,\\s]+","");
                split.add(StringUtil.removeQuotes(found.trim()));
                toSplit = toSplit.substring(found.length());
            }
            if(!toSplit.isEmpty()){
                toSplit = toSplit.replaceAll("^[,\\s]+","");
                split.add(StringUtil.removeQuotes(toSplit.trim()));
            }
        }
        return split;
    }

    @Override
    public void run(String input, Context context) {

        if(split == null){
            String toSplit = this.input.isEmpty() ? input.trim() : Cmd.populateStateVariables(this.input,this,context.getState());
            split = split(toSplit);
            logger.debug("for-each:{} input={} split={}",name,input,split);
        }
        if(split!=null && !split.isEmpty()){
            String populatedName = Cmd.populateStateVariables(this.name,this,context.getState());
            index++;
            if(index < split.size()){
                String value = split.get(index).replaceAll("\r|\n","");//defensive against trailing newline characters
                with(populatedName,value);
                context.next(value);
            }else{
                context.skip(input);
            }
        }else{
            context.skip(input);
        }
    }

    @Override
    public Cmd copy() {
        return new ForEach(this.name,this.input);
    }


    @Override
    public String toString(){
        return "for-each: "+name+(this.input!=null?this.input:"");
    }

    @Override
    public String getLogOutput(String output,Context context){
        if(split!=null && !split.isEmpty()){
            return "for-each: "+name+" = "+split.get(index);
        }else{
            return "for-each: "+name;
        }
    }
}
