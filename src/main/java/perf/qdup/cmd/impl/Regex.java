package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;
import perf.qdup.cmd.CommandResult;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Regex extends Cmd {

    private String patternString;
    public Regex(String pattern){
        this.patternString = pattern;
    }
    public String getPattern(){return patternString;}
    @Override
    protected void run(String input, Context context, CommandResult result) {

        String populatedPattern = populateStateVariables(patternString,this,context.getState());
        String newPattern = populatedPattern;

        //key is a regex friendly capture name and value is the user provided capture name
        LinkedHashMap<String,String> renames = new LinkedHashMap<>();

        Matcher fieldMatcher = NAMED_CAPTURE.matcher(populatedPattern);

        while(fieldMatcher.find()){
            String realName = fieldMatcher.group(1);
            String compName = realName.replaceAll("[\\.\\\\_]","x");
            if(!compName.equals(realName)){
                newPattern = newPattern.replace(realName,compName);
            }
            renames.put(compName,realName);

        }

        Pattern pattern = Pattern.compile(newPattern,Pattern.DOTALL);

        Matcher matcher = pattern.matcher(input);
        if(matcher.matches()){
            logger.trace("{} match {} ",this,input);
            fieldMatcher = NAMED_CAPTURE.matcher(patternString);
            List<String> names = new LinkedList<>();
            while(fieldMatcher.find()){
                names.add(fieldMatcher.group(1));
            }
            if(!names.isEmpty()){
                for(String name : names){
                    String capturedValue = matcher.group(name);
                    String realName = renames.get(name);
                    context.getState().set(realName,capturedValue);
                }
            }
            System.out.println("Regex.next "+newPattern);
            result.next(this,input);
        }else{
            logger.trace("{} NOT match {} ",this,input);

            System.out.println("Regex.skip "+newPattern);
            result.skip(this,input);
        }
    }



    @Override
    protected Cmd clone() {
        return new Regex(this.patternString).with(this.with);
    }

    @Override public String toString(){return "regex: "+replaceEscapes(patternString);}


    private String replaceEscapes(String input){
        return input.replace("\n","\\n")
                .replace("\r","\\r")
                .replace("\t","\\t")
                .replace("\b","\\b")
                .replace("\f","\\f")
                .replace("\"","\\\"")
                .replace("\\","\\\\");

    }
}
