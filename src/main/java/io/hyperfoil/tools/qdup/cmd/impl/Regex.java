package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.yaup.AsciiArt;
import io.hyperfoil.tools.yaup.StringUtil;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class Regex extends Cmd {

   private String pattern;
   private String patternString;
   private boolean matched = false;
   private boolean miss = false;
   private List<Cmd> onMiss;
   private Map<String, String> matches;
   private boolean ran = false;

   public Regex(String pattern) {
      this(pattern, false);
   }

   public Regex(String pattern, boolean miss) {
      this.pattern = pattern;
      this.miss = miss;
      this.patternString = StringUtil.removeQuotes(pattern).replaceAll("\\\\\\\\(?=[dDsSwW\\(\\)remo])", "\\\\");
      this.matches = new HashMap<>();
      this.onMiss = new LinkedList<>();
   }

   @Override
   public <T> void walk(Function<Cmd,T> converter, List<T> rtrn){
      super.walk(converter,rtrn);
      if(hasOnMiss()){
         this.onMiss().forEach(child->child.walk(converter,rtrn));
      }
   }

   public boolean isMiss() {
      return miss;
   }

   public boolean hasOnMiss() {
      return !onMiss.isEmpty();
   }

   public List<Cmd> onMiss() {
      return Collections.unmodifiableList(onMiss);
   }

   public Regex onMiss(Cmd command) {
      command.setParent(this);
      onMiss.add(command);
      return this;
   }

   public String getPattern() {
      return patternString;
   }

   @Override
   public void run(String input, Context context) {
      ran = true;
      String populatedPattern = populateStateVariables(patternString, this, context.getState());
      String newPattern = populatedPattern;

      matches.clear();

      //key is a regex friendly capture name and message is the user provided capture name
      LinkedHashMap<String, String> renames = new LinkedHashMap<>();
      Matcher fieldMatcher = NAMED_CAPTURE.matcher(populatedPattern);

      while (fieldMatcher.find()) {
         String realName = fieldMatcher.group(1);
         String compName = realName.replaceAll("[\\*\\.\\\\_]", "x");
         if (!compName.equals(realName)) {
            newPattern = newPattern.replace(realName, compName);
         }
         renames.put(compName, realName);

      }
      try {
         Pattern pattern = Pattern.compile(newPattern, Pattern.DOTALL);

         Matcher matcher = pattern.matcher(input);

         //full line matching only if the pattern specifies start of line
         matched = newPattern.startsWith("^") ? matcher.matches() : matcher.find();
         if (matched == !miss) {//if matched and !miss or miss and !match
            logger.trace("{} match {} ", this, input);
            if (!miss) { //cannot populate name capture groups for miss becasue it didn't match
               fieldMatcher = NAMED_CAPTURE.matcher(newPattern);
               List<String> names = new LinkedList<>();
               while (fieldMatcher.find()) {
                  names.add(fieldMatcher.group(1));
               }
               if (!names.isEmpty()) {
                  for (String name : names) {
                     String capturedValue = matcher.group(name);
                     String realName = renames.get(name);
                     matches.put(realName, capturedValue);
                     //context.getState().set(realName,capturedValue);
                  }
               }
               if (!matches.isEmpty()) {
                  for (String key : matches.keySet()) {
                     context.getState().set(key, matches.get(key));
                  }
               }
            }
            String logOutput = getLogOutput(input, context);
            context.log(logOutput);
            context.next(input);
         } else {
            logger.trace("{} NOT match {} ", this, input);

            if (hasOnMiss()) {
               String logOutput = getLogOutput(input, context);
               context.log(logOutput);
               context.next(input);
            } else {
               context.skip(input);
            }
         }
      } catch (PatternSyntaxException e) {
         context.error("failed to parse regex pattern from " + newPattern + "\n" + e.getMessage());
         context.abort(false);
      }

   }

   @Override //disables default logging after the command finishes
   public void postRun(String output, Context context) { }

   @Override
   public Cmd getNext() {
      if(ran && !miss && !matched && hasOnMiss()){
         return onMiss.get(0);
      }else{
         return super.getNext();
      }
//      if (ran && matched == miss && hasOnMiss()) {
//         return onMiss.get(0);
//      } else {
//         return super.getNext();
//      }
   }

   //commenting this out to test that skip means neither then or else
//   @Override
//   public Cmd getSkip(){
//      if(ran && !matched && !miss && hasOnMiss()){
//         return onMiss.get(0);
//      }else{
//         return super.getSkip();
//      }
//   }

   @Override
   public Cmd copy() {
      Regex rtrn = new Regex(this.patternString, this.miss);
      if(hasOnMiss()){
         onMiss().forEach(c->rtrn.onMiss(c.deepCopy()));
      }
      return rtrn;
   }


   @Override
   public Cmd previousChildOrParent(Cmd child){
      boolean inThens = thens.contains(child);
      int cmdIndex = inThens ? thens.indexOf(child) : onMiss.indexOf(child);
      if(cmdIndex < 0){
         return null;
      }else if (cmdIndex == 0){
         return this;
      }else{
         return inThens ? thens.get(cmdIndex-1) : onMiss.get(cmdIndex-1);
      }
   }

   @Override
   public String toString() {
      return "regex:" + (miss ? "! " : " ") + replaceEscapes(patternString);
   }

   private String replaceEscapes(String input) {
      return input.replace("\n", "\\n")
         .replace("\r", "\\r")
         .replace("\t", "\\t")
         .replace("\b", "\\b")
         .replace("\f", "\\f")
         .replace("\"", "\\\"")
         .replace("\\", "\\\\");

   }

   @Override
   public String getLogOutput(String output, Context context) {
      if (matched == !miss) {
         StringBuffer sb = new StringBuffer();
         sb.append("regex:");
         sb.append((miss ? "! " : " "));
         sb.append(replaceEscapes(patternString));
         if (!matches.isEmpty()) {
            for (String key : matches.keySet()) {
               sb.append("\n");
               sb.append("  " + key + "=" + matches.get(key));

            }
         }
         return sb.toString();
      } else {
         return "";
      }
   }
}
