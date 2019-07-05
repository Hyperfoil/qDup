package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.yaup.StringUtil;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class Regex extends Cmd {

    private String pattern;
    private String patternString;
    private boolean matched = false;
    private Map<String,String> matches;
    public Regex(String pattern){
        this.pattern = pattern;
        this.patternString = StringUtil.removeQuotes(pattern).replaceAll("\\\\\\\\(?=[dDsSwW\\(\\)remo])","\\\\");

        System.out.println("Regex("+pattern+")=>||"+patternString+"||");
        this.matches = new HashMap<>();
    }


    public String getPattern(){return patternString;}
    @Override
    public void run(String input, Context context) {
        String populatedPattern = populateStateVariables(patternString,this,context.getState());
        String newPattern = populatedPattern;

        matches.clear();

        //key is a regex friendly capture name and message is the user provided capture name
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
        try {
            Pattern pattern = Pattern.compile(newPattern, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(input);

        //full line matching only if the pattern specifies start of line
        matched = newPattern.startsWith("^") ? matcher.matches() : matcher.find();
        if(matched){
            logger.trace("{} match {} ",this,input);
            fieldMatcher = NAMED_CAPTURE.matcher(newPattern);
            List<String> names = new LinkedList<>();
            while(fieldMatcher.find()){
                names.add(fieldMatcher.group(1));
            }
            if(!names.isEmpty()){
                for(String name : names){
                    String capturedValue = matcher.group(name);
                    String realName = renames.get(name);
                    matches.put(realName,capturedValue);
                    //context.getState().set(realName,capturedValue);
                }
            }
            if(!matches.isEmpty()){
                for(String key : matches.keySet()){
                    context.getState().set(key,matches.get(key));
                }
            }
            context.next(input);
        }else{
            logger.trace("{} NOT match {} ",this,input);
            context.skip(input);
        }
        }catch(PatternSyntaxException e){
            context.getRunLogger().error("failed to parse regex pattern from {}\n", newPattern);
            context.abort(false);
        }

    }

    @Override
    public Cmd copy() {
        return new Regex(this.patternString);
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

    @Override
    public String getLogOutput(String output,Context context){
        if(matched){
            StringBuffer sb = new StringBuffer();
            sb.append("regex: ");
            sb.append(replaceEscapes(patternString));
            if(!matches.isEmpty()) {
                for (String key : matches.keySet()) {
                    sb.append("\n");
                    sb.append("  " + key + "=" + matches.get(key));

                }
            }
            return sb.toString();
        }else{
            return "regex: "+replaceEscapes(patternString);
        }
    }
}
