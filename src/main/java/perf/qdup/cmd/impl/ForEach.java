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
    private String populatedName;

    private String declaredInput;
    private String loadedInput;
    private final List<String> split = new ArrayList<>();
    private int index = -1;

    public ForEach(String name){
        this(name,"");
    }
    public ForEach(String name,String input){
        this.name = name;
        this.populatedName = name;
        this.declaredInput = input==null?"":input;
        this.loadedInput = declaredInput;
    }

    public String getName(){return name;}
    public String getDeclaredInput(){return declaredInput;}

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
                toSplit = toSplit.substring(found.length());
                toSplit = toSplit.replaceAll("^[,\\s]+","");//remove the ", " that separated found
                split.add(found);
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
        try {
            if(split.isEmpty()){

                if(!declaredInput.isEmpty()){
                    String populatedDeclaredInput = Cmd.populateStateVariables(declaredInput,this,context.getState());

                    split.addAll(split(populatedDeclaredInput));
                    this.loadedInput = populatedDeclaredInput;
                } else {
                    split.addAll(split(input));
                    this.loadedInput = input;
                }
            }
            if (this.declaredInput.isEmpty() && !this.loadedInput.equals(input)) {//for-each under a for-each needs to identify when the input changed
                split.clear();
                split.addAll(split(input));
                index=-1;
                this.loadedInput = input;
                logger.debug("for-each:{} input={} split={}", name, input, split);
            }
            if (!split.isEmpty()) {
                populatedName = Cmd.populateStateVariables(this.name, this, context.getState());
                index++;
                if (index < split.size()) {
                    String value = split.get(index).replaceAll("\r|\n", "");//defensive against trailing newline characters
                    with(populatedName, value);
                    context.next(value);
                } else {
                    context.skip(input);
                }
            } else {
                context.skip(input);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public Cmd copy() {
        return new ForEach(this.name,this.declaredInput);
    }


    @Override
    public String toString(){
        return "for-each: "+name+" "+(this.declaredInput !=null?this.declaredInput :"");
    }

    @Override
    public String getLogOutput(String output,Context context){
        if(!split.isEmpty() && index < split.size()){
            return "for-each: "+name+" = "+split.get(index);
        }else{
            return "for-each: "+name;
        }
    }
}
