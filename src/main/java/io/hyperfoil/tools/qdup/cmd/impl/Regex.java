package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.CmdWithElse;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.yaup.StringUtil;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class Regex extends CmdWithElse {

   private String pattern;
   private String patternString;
   private boolean matched = false;
   private boolean miss = false;
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
   }


   public boolean isMiss() {
      return miss;
   }

   public Set<String> getCaptureNames(){
      HashSet<String> rtrn = new HashSet<>();
      Matcher matcher = Cmd.NAMED_CAPTURE.matcher(pattern);
      while (matcher.find()) {
         String name = matcher.group(1);
         rtrn.add(name);
      }
      return rtrn;
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

            if (hasElse()) {
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
      if(ran && !miss && !matched && hasElse()){
         return getElses().get(0);
      }else{
         return super.getNext();
      }
//      if (ran && matched == miss && hasOnMiss()) {
//         return onMiss.get(0);
//      } else {
//         return super.getNext();
//      }
   }

   @Override
   public Cmd copy() {
      Regex rtrn = new Regex(this.patternString, this.miss);
      if(hasElse()){
         getElses().forEach(c->rtrn.onElse(c.deepCopy()));
      }
      return rtrn;
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
