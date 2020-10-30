package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.LoopCmd;
import io.hyperfoil.tools.qdup.stream.MultiStream;
import io.hyperfoil.tools.yaup.AsciiArt;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.yaup.StringUtil;
import io.hyperfoil.tools.yaup.json.Json;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

public class ForEach extends LoopCmd {

   final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

   private String name;
   private String populatedName;

   private String declaredInput;
   private String loadedInput;
   private String lastInput;
   private final List<Object> split = new ArrayList<>();
   private int index = -1;

   public ForEach(String name) {
      this(name, "");
   }

   public ForEach(String name, String input) {
      super(false);
      this.name = name;
      this.populatedName = name;
      this.declaredInput = input == null ? "" : input;
      this.loadedInput = declaredInput;
      this.lastInput = null;

   }

   public String getName() {
      return name;
   }

   public boolean hasDeclaredInput(){
      return declaredInput!=null && !declaredInput.isBlank();
   }
   public String getDeclaredInput() {
      return declaredInput;
   }

   public static List<Object> split(String toSplit) {
      //fixes bug where '\','n' in json value would be seen as a newline when passing the string to fromJs
      toSplit = toSplit.replaceAll("\\\\n", "\\\\\\\\n");
      final List<Object> split = new ArrayList<>();
      Json json;
      if (Json.isJsonLike(toSplit) && (json = Json.fromJs(toSplit)) != null) {
         if (json.isArray()) {
            split.addAll(json.values());
         } else {
            json.forEach((key, value) -> {
               Json entry = new Json();
               entry.set("key", key);
               entry.set("value", value);
               split.add(entry);
            });
         }
      } else if (toSplit.contains("\n")) {
         split.addAll(Arrays.asList(toSplit.split("\r?\n")));

      } else {
         if (toSplit.startsWith("[") && toSplit.endsWith("]")) {
            toSplit = toSplit.substring(1, toSplit.length() - 1);//remove [ ] around the list
         }
         String found = "";
         while (!(found = StringUtil.findNotQuoted(toSplit, ", ")).isEmpty()) {
            toSplit = toSplit.substring(found.length());
            toSplit = toSplit.replaceAll("^[,\\s]+", "");//remove the ", " that separated found
            split.add(found);
         }
         if (!toSplit.isEmpty()) {
            toSplit = toSplit.replaceAll("^[,\\s]+", "");
            split.add(StringUtil.removeQuotes(toSplit.trim()));
         }
      }
      return split;
   }

   @Override
   public void run(String input, Context context) {
      try {

         int callbackRunCount = getCallback().getRunCount();

         String populatedDeclaredInput = Cmd.populateStateVariables(declaredInput, this, context.getState());
         //if we need to load from declaredInput
         //this only works if the iteration array has not changed, we need to track the index in the array not the array itself?
         if (!declaredInput.isEmpty() && !populatedDeclaredInput.isEmpty() && (split.isEmpty() || !this.loadedInput.equalsIgnoreCase(populatedDeclaredInput))) {
            clearLoopState();
            split.addAll(split(populatedDeclaredInput));
            this.loadedInput = populatedDeclaredInput;//set loadedInput to the string rep of what we loaded
            logger.debug("for-each:{} input={} split={}", name, input, split);
            //if we need to load from input
         } else if ((declaredInput.isEmpty() || populatedDeclaredInput.isEmpty()) && (split.isEmpty() || !this.loadedInput.equals(input))) {
            clearLoopState();
            split.addAll(split(input));
            this.loadedInput = input;
         }
         if (!split.isEmpty()) {
            populatedName = Cmd.populateStateVariables(this.name, this, context.getState());
            index++;
            if (index < split.size()) {
               Object value = split.get(index);
               if (value == null) {
                  value = "";
               }
               if (value instanceof String) {
                  value = ((String) value).replaceAll("\r|\n", "");//defensive agaisnt trailing newline characters
               }
               with(populatedName, value);
               context.next(value.toString());
            } else {
               clearLoopState();//we finished looping, next run() should start a new loop
               context.skip(input);
            }
         } else {
            clearLoopState();//we didn't find anything to loop, next run() should look again
            context.skip(input);
         }
      } catch (Exception e) {
         logger.error(e.getMessage(), e);
      } finally {
         setLastInput(input);
      }
   }

   private boolean isFirstCall() {
      return lastInput == null;
   }

   private boolean isSameInput(String input) {
      return input == lastInput;
   }

   private void setLastInput(String input) {
      this.lastInput = input;
   }

   private void clearLoopState() {
      index = -1;
      split.clear();
   }

   private boolean isEmptyLoopState() {
      return split.isEmpty() && index == -1;
   }

   @Override
   public Cmd copy() {
      return new ForEach(this.name, this.declaredInput);
   }


   @Override
   public String toString() {
      return "for-each: " + name + " " + (this.declaredInput != null ? this.declaredInput : "");
   }

   @Override
   public String getLogOutput(String output, Context context) {
      if (!split.isEmpty() && index < split.size()) {
         return "for-each: " + name + " = " + split.get(index);
      } else {
         return "for-each: " + name;
      }
   }
}
