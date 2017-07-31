package perf.ssh.cmd.impl;

import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.Context;
import perf.ssh.cmd.CommandResult;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Regex extends Cmd {
    private Pattern pattern;
    private String patternString;
    public Regex(String pattern){

        this.patternString = pattern;
        this.pattern = Pattern.compile(pattern,Pattern.DOTALL);

    }
    public String getPattern(){return patternString;}
    @Override
    protected void run(String input, Context context, CommandResult result) {

        Matcher matcher = pattern.matcher(input);
        if(matcher.matches()){
            logger.trace("{} match {} ",this,input);
            Matcher fieldMatcher = NAMED_CAPTURE.matcher(patternString);
            List<String> names = new LinkedList<>();
            while(fieldMatcher.find()){
                names.add(fieldMatcher.group(1));
            }
            if(!names.isEmpty()){
                for(String name : names){
                    context.getState().set(name,matcher.group(name));
                }
            }
            result.next(this,input);
        }else{
            logger.trace("{} NOT match {} ",this,input);

            result.skip(this,input);
        }
    }



    @Override
    protected Cmd clone() {
        return new Regex(this.patternString);
    }

    @Override public String toString(){return "regex "+replaceEscapes(patternString);}


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
