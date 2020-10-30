package io.hyperfoil.tools.qdup.cmd;

import io.hyperfoil.tools.qdup.SecretFilter;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.impl.*;
import io.hyperfoil.tools.yaup.HashedLists;
import io.hyperfoil.tools.yaup.PopulatePatternException;
import io.hyperfoil.tools.yaup.StringUtil;
import io.hyperfoil.tools.yaup.json.Json;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Created by wreicher
 * Base for all the commands than can be added to a Script. Commands are created through the static methods.
 */
public abstract class Cmd {

   final static long DEFAULT_IDLE_TIMER = 30_000; //30s
   final static long DISABLED_IDLE_TIMER = -1;

   public static class Ref {
      private Ref parent;
      private Cmd command;

      public Ref(Cmd command) {
         this.command = command == null ? Cmd.NO_OP() : command;
      }

      public void loadAllWithDefs(State state){
         Cmd.Ref target = this;
         do {
            if(target.getCommand()!=null && target.getCommand().hasVariablePatternInWith()){
               target.getCommand().loadAllWithDefs(state);
            }
         }while( (target = target.getParent())!=null );
      }
      public void loadWithDefs(State state){
         Cmd.Ref target = this;
         do {
            if(target.getCommand()!=null && target.getCommand().hasVariablePatternInWith()){
               target.getCommand().loadWithDefs(state);
            }
         }while( (target = target.getParent())!=null );
      }

      public Ref add(Cmd command) {
         Ref rtrn = new Ref(command);
         rtrn.parent = this;
         return rtrn;
      }

      public boolean hasWith(Object name){
         return getWith(name) != null;
      }
      public Object getWith(Object name){
         Object rtrn = null;
         Cmd.Ref target = this;
         do {
            if(target.getCommand() != null){
               if(target.getCommand().hasWith(name.toString())){
                  rtrn = target.getCommand().getWith(name.toString());
               }
            }
         } while ((target = target.getParent()) != null && rtrn == null);
         return rtrn;
      }

      public Ref getParent() {
         return parent;
      }

      public boolean hasParent() {
         return parent != null;
      }

      public Cmd getCommand() {
         return command;
      }

      @Override
      public String toString(){
         return "ref: "+command.toString()+" parent: "+command.getStateParent()+" with: "+command.getWith();
      }
      public String dump(){
         Cmd.Ref target = this.getParent();
         StringBuilder sb = new StringBuilder();
         sb.append("Ref dump\n");
         sb.append(this.toString());
         while(target!=null){
            sb.append("\n");
            sb.append(target);
            List<Cmd> chain = target.command.getStateLineage();
            for(int i=0; i<chain.size();i++){
               sb.append("\n");
               sb.append(String.format("%"+(i+1)+"s%s with: %s","",chain.get(i).toString(),chain.get(i).getWith().toString()));
            }
            target = target.getParent();
         }
         return sb.toString();
      }
   }

   public static class NO_OP extends Cmd {
      @Override
      public void run(String input, Context context) {
         context.next(input);
      }

      @Override
      public Cmd copy() {
         return new NO_OP().with(this.withDef);
      }

      @Override
      public String toString() {
         return "NO_OP";
      }
   }

   protected final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

   public static final Pattern STATE_PATTERN = Pattern.compile("\\$\\{\\{(?<name>[^${}:]+):?(?<default>[^}]*)}}");

   public static final String ENV_PREFIXx = "${";
   public static final String ENV_SUFFIX = "}";
   public static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{(?<name>[^\\{][^\\}]*)}");

   public static final Pattern NAMED_CAPTURE = java.util.regex.Pattern.compile("\\(\\?<([^>]+)>");

   public static Cmd js(String code) {
      return new JsCmd(code);
   }

   public static Cmd done() {
      return new Done();
   }

   public static Cmd NO_OP() {
      return new NO_OP();
   }

   public static Cmd abort(String message) {
      return new Abort(message);
   }

   public static Cmd abort(String message, Boolean skipCleanup) {
      return new Abort(message, skipCleanup);
   }

   public static Cmd code(Code code) {
      return new CodeCmd(code);
   }

   public static Cmd code(String className) {
      return new CodeCmd(className);
   }

   public static Cmd countdown(String name, int initial) {
      return new Countdown(name, initial);
   }

   public static Cmd ctrlC() {
      return new CtrlC();
   }

   public static Cmd download(String path) {
      return new Download(path);
   }

   public static Cmd download(String path, String destination) {
      return new Download(path, destination);
   }

   public static Cmd upload(String path, String destination) {
      return new Upload(path, destination);
   }

   public static Cmd exitCode() {
      return new ExitCode("");
   }

   public static Cmd exitCode(String expected) {
      return new ExitCode(expected);
   }

   public static Cmd echo() {
      return new Echo();
   }

   public static Cmd forEach(String name) {
      return new ForEach(name);
   }

   public static Cmd forEach(String name, String input) {
      return new ForEach(name, input);
   }

   public static Cmd invoke(Cmd command) {
      return new InvokeCmd(command);
   }

   public static Cmd log(String value) {
      return new Log(value);
   }

   public static Cmd queueDownload(String path) {
      return new QueueDownload(path);
   }

   public static Cmd queueDownload(String path, String destination) {
      return new QueueDownload(path, destination);
   }

   public static Cmd readState(String name) {
      return new ReadState(name);
   }

   public static Cmd reboot(String timeout, String target, String password) {
      return new Reboot(timeout, target, password);
   }

   public static Regex regex(String pattern) {
      return new Regex(pattern);
   }

   public static Cmd repeatUntil(String name) {
      return new RepeatUntilSignal(name);
   }

   public static ScriptCmd script(String name) {
      return new ScriptCmd(name, false,false);
   }

   public static ScriptCmd script(String name, boolean async) {
      return new ScriptCmd(name, async,false);
   }

   public static SetSignal setSignal(String name, String value) {
      return new SetSignal(name,value);
   }

   public static SetState setState(String name) {
      return new SetState(name);
   }

   public static SetState setState(String name, String value) {
      return new SetState(name, value);
   }

   public static Cmd sh(String command) {
      return new Sh(command);
   }

   public static Cmd sh(String command, boolean silent) {
      return new Sh(command, silent);
   }

   public static Cmd signal(String name) {
      return new Signal(name);
   }

   public static Cmd sleep(String amount) {
      return new Sleep(amount);
   }

   public static Cmd waitFor(String name) {
      return new WaitFor(name);
   }

   public static Cmd xml(String path) {
      return new XmlCmd(path);
   }

   public static Cmd xml(String path, String... operations) {
      return new XmlCmd(path, operations);
   }


   public <T> List<T> walk(boolean isWatching,Function<Cmd,T> converter){
      LinkedList<T> rtrn = new LinkedList<>();
      walk((cmd,b)->converter.apply(cmd),isWatching,rtrn);
      return rtrn;
   }
   public <T> List<T> walk(boolean isWatching,BiFunction<Cmd,Boolean,T> converter){
      LinkedList<T> rtrn = new LinkedList<>();
      walk(converter,isWatching,rtrn);
      return rtrn;
   }

   public<T> void walk(boolean isWatching,Function<Cmd,T> converter,List<T> rtrn){
      walk((cmd,b)->converter.apply(cmd),isWatching,rtrn);
   }
   public<T> void walk(BiFunction<Cmd,Boolean,T> converter, boolean isWatching,List<T> rtrn){
      T value = converter.apply(this,isWatching);
      rtrn.add(value);
      if(this.hasThens()){
         this.getThens().forEach(child->child.walk(converter,isWatching,rtrn));
      }
      if(this.hasWatchers()){
         this.getWatchers().forEach(child->child.walk(converter,true,rtrn));
      }
      if(this.hasTimers()){
         this.getTimeouts().forEach(timer->{
            this.getTimers(timer).forEach(child->child.walk(converter,true,rtrn));
         });
      }
      if(this.hasSignalWatchers()){
         this.getSignalNames().forEach(signal->{
            this.getSignal(signal).forEach(child->child.walk(converter,true,rtrn));
         });
      }
   }

   public boolean hasWith(String name) {
      boolean hasIt = false;
      Cmd target = this;
      while (!hasIt && target != null) {
         hasIt = target.withActive.has(name) || Json.find(target.withActive, name.startsWith("$") ? name : "$." + name) != null;
         target = target.stateParent;
      }
      return hasIt;
   }
   public List<Cmd> getStateLineage(){
      List<Cmd> rtrn = new LinkedList<>();
      Cmd target = this;
      do{
         rtrn.add(target);
      }while( (target = target.getStateParent()) !=null);
      return rtrn;
   }
   public Object getWith(String name){
      return getWith(name,new State(State.RUN_PREFIX));
   }
   public Object getWith(String name,State state) {
      Object value = null;
      Cmd target = this;
      while (value == null && target != null) {
         value = target.withActive.has(name) ? target.withActive.get(name) : Json.find(target.withActive, name.startsWith("$") ? name : "$." + name);
         if(value!=null){//we found something
            if(Cmd.hasStateReference(value.toString(),target)){//
               //target.loadWithDefs(state);
               if (value.toString().contains(name)) { //do not use targets's with if it is value is referencing name (avoid loops)
                  value = Cmd.populateStateVariables(value.toString(), null, state);
               } else {
                  value = Cmd.populateStateVariables(value.toString(), target, state);
               }
            }
         }
         target = target.stateParent;
      }
      return value;
   }

//   public static boolean isSingelStageReference(String input) {
//      return input.startsWith(STATE_PREFIX) && input.equalsIgnoreCase(STATE_SUFFIX) && input.indexOf(STATE_PREFIX, 1) == -1;
//   }

   public static boolean hasStateReference(String input, Cmd cmd){
      if(cmd == null){
         return input.contains(StringUtil.PATTERN_PREFIX);
      }else{
         return input.contains(cmd.getPatternPrefix());
      }
   }

   public static List<String> getStateVariables(String command, Cmd cmd, State state,Ref ref){
      CmdStateRefMap map = new CmdStateRefMap(cmd,state,ref);
      List<String> rtrn = Collections.EMPTY_LIST;
      if(cmd==null){
         try {
            rtrn = StringUtil.getPatternNames(
                    command,
                    map,
                    StringUtil.PATTERN_PREFIX,
                    StringUtil.PATTERN_DEFAULT_SEPARATOR,
                    StringUtil.PATTERN_SUFFIX,
                    StringUtil.PATTERN_JAVASCRIPT_PREFIX
            );
         } catch (PopulatePatternException e) {}
      }else{
         try {
            rtrn = StringUtil.getPatternNames(
                    command,
                    map,
                    StringUtil.PATTERN_PREFIX,
                    StringUtil.PATTERN_DEFAULT_SEPARATOR,
                    StringUtil.PATTERN_SUFFIX,
                    StringUtil.PATTERN_JAVASCRIPT_PREFIX
            );
         } catch (PopulatePatternException e) {}
      }
      return rtrn;
   }
   public static String populateStateVariables(String command, Cmd cmd, State state) {
      return populateStateVariables(command, cmd, state, new Ref(cmd));
   }
   public static String populateStateVariables(String command, Cmd cmd, State state, Ref ref) {
      if(command == null){
         return "";
      }
      if(!hasStateReference(command,cmd)){
         return command;
      }
      CmdStateRefMap map = new CmdStateRefMap(cmd,state,ref);
      try {
         if(cmd!=null){
            return StringUtil.populatePattern(command,map,cmd.getPatternPrefix(),cmd.getPatternSeparator(),cmd.getPatternSuffix(),cmd.getPatternJavascriptPrefix());
         }else {
            return StringUtil.populatePattern(command, map, StringUtil.PATTERN_PREFIX, StringUtil.PATTERN_DEFAULT_SEPARATOR, StringUtil.PATTERN_SUFFIX, StringUtil.PATTERN_JAVASCRIPT_PREFIX);
         }
      } catch (PopulatePatternException pe){
         //pe.printStackTrace();
         logger.debug(pe.getMessage());//changed to debug because runs now fail when patterns are missing
         //return command;//must return input to show there are unpopulated patterns
         return pe.getResult();//should still contain the missing entries
      }
   }
   private static final AtomicInteger uidGenerator = new AtomicInteger(0);

   protected Json withDef;
   protected Json withActive;
   //private int thenIndex = 0;
   protected LinkedList<Cmd> thens;
   private LinkedList<Cmd> watchers;
   private HashedLists<Long, Cmd> timers;
   private HashedLists<String, Cmd> onSignal;

   private String patternPrefix = StringUtil.PATTERN_PREFIX;
   private String patternSuffix = StringUtil.PATTERN_SUFFIX;
   private String patternSeparator = StringUtil.PATTERN_DEFAULT_SEPARATOR;
   private String patternJavascriptPrefix = StringUtil.PATTERN_JAVASCRIPT_PREFIX;

   private Cmd parent;
   private Cmd stateParent;

   private long idleTimer = DEFAULT_IDLE_TIMER;
   protected boolean silent = false;
   private boolean stateScan = true;

   int uid;
   private String output;

   protected Cmd() {
      this(false);
   }

   protected Cmd(boolean silent) {
      this.silent = silent;
      this.withDef = new Json();
      this.withActive = new Json();
      this.thens = new LinkedList<>();
      this.watchers = new LinkedList<>();
      this.timers = new HashedLists<>();
      this.onSignal = new HashedLists<>();
      this.parent = null;
      this.stateParent = null;
      this.uid = uidGenerator.incrementAndGet();
   }

   public void setStateScan(boolean stateScan){
      this.stateScan = stateScan;
   }
   public boolean isStateScan(){return stateScan;}
   public boolean hasCustomIdleTimer(){return idleTimer!=DEFAULT_IDLE_TIMER;}
   public boolean hasIdleTimer(){return idleTimer > 0;}
   public long getIdleTimer(){return idleTimer;}
   public void setIdleTimer(long idleTimer){
      this.idleTimer = idleTimer;
   }
   public void disableIdleTimer(){
      this.idleTimer = DISABLED_IDLE_TIMER;
   }

   private void preChild(Cmd child){}

   public boolean hasPatternPrefix(){
      return !StringUtil.PATTERN_PREFIX.equals(getPatternPrefix());
   }
   public String getPatternPrefix() {
      return patternPrefix;
   }

   public void setPatternPrefix(String patternPrefix) {
      this.patternPrefix = patternPrefix;
   }

   public boolean hasPatternSuffix(){
      return !StringUtil.PATTERN_SUFFIX.equals(getPatternSuffix());
   }
   public String getPatternSuffix() {
      return patternSuffix;
   }

   public void setPatternSuffix(String patternSuffix) {
      this.patternSuffix = patternSuffix;
   }

   public boolean hasPatternSeparator(){
      return !StringUtil.PATTERN_DEFAULT_SEPARATOR.equals(getPatternSeparator());
   }
   public String getPatternSeparator() {
      return patternSeparator;
   }

   public void setPatternSeparator(String patternSeparator) {
      this.patternSeparator = patternSeparator;
   }

   public boolean hasPatternJavascriptPrefix(){
      return !StringUtil.PATTERN_JAVASCRIPT_PREFIX.equals(getPatternJavascriptPrefix());
   }
   public String getPatternJavascriptPrefix() {
      return patternJavascriptPrefix;
   }

   public void setPatternJavascriptPrefix(String patternJavascriptPrefix) {
      this.patternJavascriptPrefix = patternJavascriptPrefix;
   }


   public Cmd onSignal(String name, Cmd command) {
      command.stateParent = this;
      onSignal.put(name, command);
      return this;
   }

   public boolean hasSignalWatchers() {
      return !onSignal.isEmpty();
   }

   public Set<String> getSignalNames() {
      return onSignal.keys();
   }

   public List<Cmd> getSignal(String name) {
      return onSignal.get(name);
   }

   public Cmd addTimer(long timeout, Cmd command) {
      command.stateParent = this;
      timers.put(timeout, command);
      return this;
   }

   public List<Cmd> getTimers(long timeout) {
      return timers.get(timeout);
   }

   public Set<Long> getTimeouts() {
      return timers.keys();
   }

   public boolean hasTimers() {
      return !timers.isEmpty();
   }


   public void injectThen(Cmd command) {
      if(command!=null){
         command.setParent(this);
         thens.addFirst(command);
      }
   }

   public boolean isSilent() {
      return silent;
   }

   public void setSilent(boolean silent) {
      this.silent = silent;
   }

   public Cmd with(Map<? extends Object, ? extends Object> map) {
      map.forEach((k, v) -> {
         withDef.set(k, v);
         withActive.set(k, v);
      });
      return this;
   }

   public Cmd with(Json withs) {
      if(withs!=null) {
         this.withDef.merge(withs);
         this.withActive.merge(withs);
      }
      return this;
   }

   public Cmd with(String key, Object value) {
      if(value instanceof String){
         value = State.convertType(value);
      }
      withDef.set(key, value);
      withActive.set(key, value);
      return this;
   }


   public Json getWith() {
      return withDef;
   }
   public Json getVisibleWith(){
      Cmd target = this;
      Json rtrn = new Json(false);

      do{
         rtrn.merge(target.withActive,false);
         rtrn.merge(target.withDef,false);
      }while ( (target = target.getStateParent()) != null);

      return rtrn;
   }
   public void loadWith(Cmd cmd){
      Cmd target = cmd;
      do{
         this.withDef.merge(target.withDef,true);
         this.withActive.merge(target.withActive,true);
      }while( (target = target.getParent()) != null);
   }

   public int getUid() {
      return uid;
   }

   public String tree() {
      return tree(0, false);
   }

   public String tree(int indent, boolean debug) {
      StringBuffer rtrn = new StringBuffer();
      tree(rtrn, indent, "", debug);
      return rtrn.toString();
   }

   private void tree(StringBuffer rtrn, int indent, String prefix, boolean debug) {
      final int correctedIndent = indent == 0 ? 1 : indent;
      if (debug) {
         rtrn.append(String.format("%" + correctedIndent + "s%s%s parent=%s skip=%s next=%s%n", "", prefix, this.getUid() + ":" + this, this.parent, this.getSkip(),this.getNext()));
      } else {
         rtrn.append(String.format("%" + correctedIndent + "s%s%s %n", "", prefix, this.toString()));
      }
      withDef.forEach((k, v) -> {
         String value = String.format("%" + (correctedIndent + 4) + "swith: %s=%s%n", "", k, v);
         rtrn.append(value);
      });
      watchers.forEach((w) -> {
         w.tree(rtrn, correctedIndent + 4, "watch:", debug);
      });
      timers.forEach((timeout, cmdList) -> {
         rtrn.append(String.format("%" + (correctedIndent + 4) + "stimer: %d%n", "", timeout));
         cmdList.forEach(cmd -> {
            cmd.tree(rtrn, correctedIndent + 6, "", debug);
         });
      });
      onSignal.forEach((name,cmdList)->{
         rtrn.append(String.format("%" + (correctedIndent +4) + "son-signal: %s%n","",name));
         cmdList.forEach(cmd -> {
            cmd.tree(rtrn, correctedIndent + 6, "", debug);
         });
      });
      thens.forEach((t) -> {
         t.tree(rtrn, correctedIndent + 2, "", debug);
      });
   }

   public String getOutput() {
      return output;
   }

   public void setOutput(String output) {
      this.output = output;
   }

   public Cmd getPrevious() {
      Cmd rtrn = null;
      if(hasParent()){
         rtrn = getParent().previousChildOrParent(this);
      }
      return rtrn;
   }

   public boolean hasParent(){return parent!=null;}
   public Cmd getParent(){return parent;}
   public boolean hasStateParent(){return stateParent!=null;}
   public Cmd getStateParent(){return stateParent;}
   public void setParent(Cmd command){
      this.parent = command;
      setStateParent(command);
   }
   public void setStateParent(Cmd command){
      this.stateParent = command;
   }

   public Cmd getSkip() {
      Cmd rtrn = null;
      if(hasParent()){
         Cmd target = this;
         while(rtrn == null && target.hasParent()){
            rtrn = target.getParent().nextChild(target);
            target = target.getParent();
         }
      }
      return rtrn;
   }
   public Cmd getNext() {
      Cmd rtrn = null;
      if(hasThens()){
         rtrn = thens.getFirst();
      }else{
         Cmd target = this;
         while(rtrn == null && target.hasParent()){
            rtrn = target.getParent().nextChild(target);
            target = target.getParent();
         }
      }
      return rtrn;
   }

   public Cmd nextChild(Cmd child){
      Cmd rtrn = null;
      int cmdIndex = thens.indexOf(child);
      if(cmdIndex < 0){
         //TODO throw error because current command is not a child?

      }else if (cmdIndex == thens.size() -1 ){
      }else{
         rtrn = thens.get(cmdIndex+1);
      }
      return rtrn;
   }
   public Cmd previousChildOrParent(Cmd child){
      int cmdIndex = thens.indexOf(child);
      if(cmdIndex < 0){
         return null;
      }else if (cmdIndex == 0){
         return this;
      }else{
         return thens.get(cmdIndex-1);
      }
   }

   public Cmd then(Cmd command) {
      if(command!=null){
         command.setParent(this);
         thens.add(command);
      }
      return this;
   }
   public Cmd thenOrphaned(Cmd command){
      if(command!=null){
         thens.add(command);
      }
      return this;
   }

   public Cmd watch(Cmd command) {
      command.stateParent = this;
      this.watchers.add(command);
      return this;
   }

   public boolean hasThens() {
      return !thens.isEmpty();
   }

   public List<Cmd> getThens() {
      return Collections.unmodifiableList(this.thens);
   }

   public boolean hasWatchers() {
      return !this.watchers.isEmpty();
   }

   public List<Cmd> getWatchers() {
      return Collections.unmodifiableList(this.watchers);
   }

   public boolean hasVariablePatternInWith(){
      return Cmd.hasStateReference(withDef.toString(0),this);
   }

   //replace with values if they have ${{ and check for secrets
   public void loadAllWithDefs(State state){
      Cmd target = this;
      do{
         target.loadWithDefs(state);
      } while ( (target = target.getStateParent()) != null );
   }
   public void loadWithDefs(State state){
      if(!withDef.isEmpty()){
         withDef.forEach((k,v)->{
            boolean isSecret = false;
            String key = k.toString();
            Object updatedKey = k;
            if(Cmd.hasStateReference(key,this)){
               //using state parent to prevent loop when trying to resolve self references
               key = Cmd.populateStateVariables(key,this.getStateParent(),state);
               updatedKey = key;
            }
            if(key.startsWith(SecretFilter.SECRET_NAME_PREFIX)){
               key = key.substring(SecretFilter.SECRET_NAME_PREFIX.length());
               isSecret = true;
               updatedKey = key;
            }
            if(v instanceof String && Cmd.hasStateReference((String) v,this)){
               String populatedV = populateStateVariables((String)v,this.getStateParent(),state);
               Object convertedV = State.convertType(populatedV);
               withActive.set(updatedKey,convertedV);
               if(isSecret){
                  state.getSecretFilter().addSecret(convertedV.toString());
               }
            }else{
               withActive.set(updatedKey,v);
               if(isSecret){
                  state.getSecretFilter().addSecret(v.toString());
               }
            }
         });
      }
   }

   public final void doRun(String input, Context context) {
      loadWithDefs(context.getState());
         //replace with values if they have ${{ and check for secrets


         //TODO the is currently being handles by populateStateVariables
         // a good idea might be to create a new context for this and this.then()
//            for(String key : with.keySet()){
//                context.getState().set(key,with.get(key));
//            }
      if(hasParent()){
         getParent().preChild(this);
      }
      //TODO move to ScriptContext.run() so it is called before timer.start(cmd.toString())
      try{
         preRun(input,context);
      }catch (Exception e){
         context.error("Error: "+e.getMessage()+"\n  "+this);
      }
      try {
         run(input, context);
      }catch(Exception e){
         e.printStackTrace();
         abort("Exception from "+this);
      }
   }

   public abstract void run(String input, Context context);
   public abstract Cmd copy();

   public void preRun(String input,Context context){}
   public void postRun(String output,Context context){
      String toLog = getLogOutput(output,context);
      if(toLog != null && !toLog.isBlank()){
         context.log(toLog);
      }
   }


   public String getLogOutput(String output, Context context) {
      if (output == null || isSilent() || (getPrevious() != null && output.equals(getPrevious().getOutput()))) {
         return this.toString();
      } else {
         return this.toString() + ((output!=null && !output.isBlank()) ? ("\n" + output) : "");
      }
   }

   public Cmd deepCopy() {
      Cmd clone = this.copy();
      if(clone !=null){
         clone.setStateScan(this.isStateScan());
         clone.setIdleTimer(this.getIdleTimer());
         clone.with(this.getWith());
         if(this.hasPatternPrefix()){
            clone.setPatternPrefix(this.getPatternPrefix());
         }
         if(this.hasPatternSuffix()){
            clone.setPatternSuffix(this.getPatternSuffix());
         }
         if(this.hasPatternSeparator()){
            clone.setPatternSeparator(this.getPatternSeparator());
         }
         if(this.hasPatternJavascriptPrefix()){
            clone.setPatternJavascriptPrefix(this.getPatternJavascriptPrefix());
         }
         for (Cmd watcher : this.getWatchers()) {
            clone.watch(watcher.deepCopy());
         }
         for (Cmd then : this.getThens()) {
            clone.then(then.deepCopy());
         }
         for (long timeout : this.getTimeouts()) {
            for (Cmd timed : this.getTimers(timeout)) {
               clone.addTimer(timeout, timed.deepCopy());
            }
         }
         for (String name : this.getSignalNames()) {
            for (Cmd signaled : this.getSignal(name)) {
               clone.onSignal(name, signaled);
            }
         }
      }
      return clone;
   }

   public Cmd getLastWatcher() {
      return watchers.getLast();
   }

   public Cmd getWatcherTail() {
      Cmd rtrn = watchers.getLast();
      while (!rtrn.getThens().isEmpty()) {
         rtrn = rtrn.thens.getLast();
      }
      return rtrn;
   }

   public Cmd getLastThen() {
      return thens.getLast();
   }

   public Cmd getTail() {
      Cmd rtrn = this;

      while (!rtrn.getThens().isEmpty() ) {
         rtrn = rtrn.thens.getLast();

      }
      return rtrn;
   }

   public Cmd getHead() {
      Cmd rtrn = this;
      while (rtrn.parent != null) {
         rtrn = rtrn.parent;
      }
      return rtrn;
   }

   @Override
   public int hashCode() {
      return getUid();
   }

   @Override
   public boolean equals(Object object) {
      if (object == null) {
         return false;
      }
      if (object instanceof Cmd) {
         return this.getUid() == ((Cmd) object).getUid();
      }
      return false;
   }

   public boolean isSame(Cmd command) {
      return command != null && this.toString().equals(command.toString());
   }

}
