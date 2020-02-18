package io.hyperfoil.tools.qdup.cmd;

import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.impl.Abort;
import io.hyperfoil.tools.qdup.cmd.impl.CodeCmd;
import io.hyperfoil.tools.qdup.cmd.impl.Countdown;
import io.hyperfoil.tools.qdup.cmd.impl.CtrlC;
import io.hyperfoil.tools.qdup.cmd.impl.Done;
import io.hyperfoil.tools.qdup.cmd.impl.Download;
import io.hyperfoil.tools.qdup.cmd.impl.Echo;
import io.hyperfoil.tools.qdup.cmd.impl.ExitCode;
import io.hyperfoil.tools.qdup.cmd.impl.ForEach;
import io.hyperfoil.tools.qdup.cmd.impl.InvokeCmd;
import io.hyperfoil.tools.qdup.cmd.impl.JsCmd;
import io.hyperfoil.tools.qdup.cmd.impl.Log;
import io.hyperfoil.tools.qdup.cmd.impl.QueueDownload;
import io.hyperfoil.tools.qdup.cmd.impl.ReadState;
import io.hyperfoil.tools.qdup.cmd.impl.Reboot;
import io.hyperfoil.tools.qdup.cmd.impl.Regex;
import io.hyperfoil.tools.qdup.cmd.impl.RepeatUntilSignal;
import io.hyperfoil.tools.qdup.cmd.impl.ScriptCmd;
import io.hyperfoil.tools.qdup.cmd.impl.SetState;
import io.hyperfoil.tools.qdup.cmd.impl.Sh;
import io.hyperfoil.tools.qdup.cmd.impl.Signal;
import io.hyperfoil.tools.qdup.cmd.impl.Sleep;
import io.hyperfoil.tools.qdup.cmd.impl.Upload;
import io.hyperfoil.tools.qdup.cmd.impl.WaitFor;
import io.hyperfoil.tools.qdup.cmd.impl.XmlCmd;
import io.hyperfoil.tools.yaup.HashedLists;
import io.hyperfoil.tools.yaup.PopulatePatternException;
import io.hyperfoil.tools.yaup.StringUtil;
import io.hyperfoil.tools.yaup.json.Json;
import org.apache.commons.jexl3.JexlContext;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;
import java.io.Reader;
import java.io.Writer;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Created by wreicher
 * Base for all the commands than can be added to a Script. Commands are created through the static methods.
 */
public abstract class Cmd {
   //private static final JexlEngine jexl = new JexlBuilder().cache(512).strict(true).silent(false).create();
   private static final ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");

   static {
      try {
         engine.eval("function milliseconds(v){ return Packages.io.hyperfoil.tools.qdup.cmd.impl.Sleep.parseToMs(v)}");
         engine.eval("function seconds(v){ return Packages.io.hyperfoil.tools.qdup.cmd.impl.Sleep.parseToMs(v)/1000}");
      } catch (ScriptException e) {
         e.printStackTrace();
      }
   }

   private static class JexlStateContext implements JexlContext {

      private State state;

      public JexlStateContext(State state) {
         this.state = state;
      }

      @Override
      public Object get(String name) {
         if (state.has(name)) {
            Object value = state.get(name);
            if (value instanceof String && ((String) value).matches("\\d+")) {
               return Long.parseLong((String) value);
            } else if (value instanceof String && ((String) value).matches("\\d+\\.\\d+")) {
               return Double.parseDouble((String) value);
            }
         }
         return null;
      }

      @Override
      public void set(String name, Object value) {

      }

      @Override
      public boolean has(String name) {
         return state.has(name, true);
      }
   }

   private static class NashornStateContext implements javax.script.ScriptContext {

      private final State state;
      private final ScriptContext parent;

      private Object fromState(String key) {
         Object val = state.get(key);
         if (val instanceof String && ((String) val).matches("\\d+")) {
            return Long.parseLong((String) val);
         } else if (val instanceof String && ((String) val).matches("\\d+\\.\\d+")) {
            return Double.parseDouble((String) val);
         } else {
            return val;
         }
      }

      public NashornStateContext(State state) {
         this(state, new SimpleScriptContext());
      }

      public NashornStateContext(State state, javax.script.ScriptContext parent) {
         this.state = state;
         this.parent = parent;
      }

      @Override
      public void setBindings(Bindings bindings, int i) {
         parent.setBindings(bindings, i);
      }

      @Override
      public Bindings getBindings(int i) {
         return parent.getBindings(i);
      }

      @Override
      public void setAttribute(String s, Object o, int i) {

         parent.setAttribute(s, o, i);
      }

      @Override
      public Object getAttribute(String s, int i) {
         if (state.has(s, true)) {
            return fromState(s);
         } else {
            return parent.getAttribute(s, i);
         }
      }

      @Override
      public Object removeAttribute(String s, int i) {
         return parent.removeAttribute(s, i);
      }

      @Override
      public Object getAttribute(String s) {
         if (state.has(s, true)) {
            return fromState(s);
         }
         return parent.getAttribute(s);
      }

      @Override
      public int getAttributesScope(String s) {
         return 0;
      }

      @Override
      public Writer getWriter() {
         return parent.getWriter();
      }

      @Override
      public Writer getErrorWriter() {
         return parent.getWriter();
      }

      @Override
      public void setWriter(Writer writer) {
         parent.setWriter(writer);
      }

      @Override
      public void setErrorWriter(Writer writer) {
         parent.setErrorWriter(writer);
      }

      @Override
      public Reader getReader() {
         return parent.getReader();
      }

      @Override
      public void setReader(Reader reader) {
         parent.setReader(reader);
      }

      @Override
      public List<Integer> getScopes() {
         return parent.getScopes();
      }
   }

   public abstract static class LoopCmd extends Cmd {
      @Override
      protected void setSkip(Cmd skip) {
         //prevent propagating skip to last then because it needs to skip to this

         this.forceSkip(skip);
      }

      @Override
      public Cmd then(Cmd command) {
         Cmd commandTail = command.getTail();
         Cmd currentTail = this.getTail();
         Cmd rtrn = super.then(command, true);


         if(currentTail==this){
            forceNext(command);//setting loopback will happen later
         }else if(currentTail.isLoopBack()){//
            //if this is making the  current tail a loop back then it needs to no longer be a loopback
            if(currentTail.getNext() == this){
               currentTail.clearLoopBack();
               currentTail.forceNext(command);
            }else{
               //the currentTail is the end of a different loop, set the other LoopCmd to skip to command
               assert currentTail.getSkip() instanceof LoopCmd;
               currentTail.getSkip().forceSkip(command);
            }
         }else{
            //TODO I think this is an error, how could the tail of a loop not be loopback
            System.out.printf("Not sure how LoopCmd.tail !isLoopBack "+currentTail);
         }

         if(!commandTail.isLoopBack()){//if the command is not part of a loop add it to this loop
            commandTail.setLoopBack(this);
         }else{
            Cmd commandTailSkip = commandTail.getSkip();
            assert commandTailSkip instanceof LoopCmd;

            Cmd target = commandTailSkip;
            while(target != null && target != this){
               if( !target.hasSkip() ){
                  target.forceSkip(this);
               }else {
                  if(target.getSkip() instanceof LoopCmd){

                  }else{

                  }
               }

               target = target.getParent();
            }
            //commandTailSkip.forceSkip(this);//if the other loop exits it should go back to this loop


//            while(target!=null && !target.hasSkip() && target !=this){
//               target.forceSkip(this);
//               target = target.getParent();
//            }
         }

         return rtrn;
      }

   }

   public static class Ref {
      private Ref parent;
      private Cmd command;

      public Ref(Cmd command) {
         this.command = command;
      }

      public Ref add(Cmd command) {
         Ref rtrn = new Ref(command);
         rtrn.parent = this;
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

   public static final String ENV_PREFIX = "${";
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

   public static Cmd regex(String pattern) {
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

   public static Cmd setState(String name) {
      return new SetState(name);
   }

   public static Cmd setState(String name, String value) {
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


   public boolean hasWith(String name) {
      boolean hasIt = false;
      Cmd target = this;
      while (!hasIt && target != null) {
         hasIt = target.withActive.has(name) || Json.find(target.withActive, name.startsWith("$") ? name : "$." + name) != null;
         target = target.parent;
      }
      return hasIt;
   }

   public Object getWith(String name) {
      Object value = null;
      Cmd target = this;
      while (value == null && target != null) {
         value = target.withActive.has(name) ? target.withActive.get(name) : Json.find(target.withActive, name.startsWith("$") ? name : "$." + name);
         target = target.parent;
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

   public static Object getStateValue(String name, Cmd cmd, State state, Ref ref) {
      if(!hasStateReference(name,cmd)){
         return name;
      }
      CmdStateRefMap map = new CmdStateRefMap(cmd,state,ref);
      try {
         if(cmd!=null){
            return StringUtil.populatePattern(name,map,cmd.getPatternPrefix(),cmd.getPatternSeparator(),cmd.getPatternSuffix(),cmd.getPatternJavascriptPrefix());
         }else {
            return StringUtil.populatePattern(name, map, StringUtil.PATTERN_PREFIX, StringUtil.PATTERN_DEFAULT_SEPARATOR, StringUtil.PATTERN_SUFFIX, StringUtil.PATTERN_JAVASCRIPT_PREFIX);
         }
      } catch (PopulatePatternException pe){
          logger.warn(pe.getMessage());
          return name;//""; //TODO: this is the default behaviour, do we want to return a zero length string when populating the pattern has failed?
      }

   }
   public static String populateStateVariables(String command, Cmd cmd, State state) {
      return populateStateVariables(command, cmd, state, new Ref(cmd));
   }
   public static String populateStateVariables(String command, Cmd cmd, State state, Ref ref) {
      Object rtrn = getStateValue(command,cmd,state,ref);
      return rtrn == null ? "" : rtrn.toString();
   }


   private static final AtomicInteger uidGenerator = new AtomicInteger(0);

   protected Json withDef;
   protected Json withActive;
   private LinkedList<Cmd> thens;
   private LinkedList<Cmd> watchers;
   private HashedLists<Long, Cmd> timers;
   private HashedLists<String, Cmd> onSignal;


   private String patternPrefix = StringUtil.PATTERN_PREFIX;
   private String patternSuffix = StringUtil.PATTERN_SUFFIX;
   private String patternSeparator = StringUtil.PATTERN_DEFAULT_SEPARATOR;
   private String patternJavascriptPrefix = StringUtil.PATTERN_JAVASCRIPT_PREFIX;

   public String getPatternPrefix() {
      return patternPrefix;
   }

   public void setPatternPrefix(String patternPrefix) {
      this.patternPrefix = patternPrefix;
   }

   public String getPatternSuffix() {
      return patternSuffix;
   }

   public void setPatternSuffix(String patternSuffix) {
      this.patternSuffix = patternSuffix;
   }

   public String getPatternSeparator() {
      return patternSeparator;
   }

   public void setPatternSeparator(String patternSeparator) {
      this.patternSeparator = patternSeparator;
   }

   public String getPatternJavascriptPrefix() {
      return patternJavascriptPrefix;
   }

   public void setPatternJavascriptPrefix(String patternJavascriptPrefix) {
      this.patternJavascriptPrefix = patternJavascriptPrefix;
   }

   private Cmd parent;
   private Cmd prev;
   private Cmd next;
   private Cmd skip;

   private boolean isLoopBack = false;

   int uid;

   protected boolean silent = false;

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
      this.next = null;
      this.skip = null;
      this.prev = null;
      this.parent = null;
      this.uid = uidGenerator.incrementAndGet();
   }

   public boolean isLoopBack(){return isLoopBack;}
   public void setLoopBack(LoopCmd loopCmd){
      if(!isLoopBack){
         isLoopBack=true;
         forceNext(loopCmd);
         forceSkip(loopCmd);
         //set the parent skip if it isn't set so that they don't skip out of the loop
         Cmd pnt = this.getParent();
         while(pnt!=null && !pnt.hasSkip() && pnt!=loopCmd){
            pnt.forceSkip(loopCmd);
            pnt = pnt.getParent();
         }
      }
   }
   public void clearLoopBack(){
      isLoopBack=false;
   }


   public Cmd onSignal(String name, Cmd command) {
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
      timers.put(timeout, command);
      return this;
   }

   public List<Cmd> getTimers(long timeout) {
      setOutput("getTimers(" + timeout + ")=" + timers.get(timeout));
      return timers.get(timeout);
   }

   public Set<Long> getTimeouts() {
      return timers.keys();
   }

   public boolean hasTimers() {
      return !timers.isEmpty();
   }

   public void injectThen(Cmd command) {
      injectThen(command, null);
   }

   public void injectThen(Cmd command, Context context) {
      thens.addFirst(command);
      forceParent(command,context);
      this.next = command;
   }
   public void forceParent(Cmd command, Context context) {
      Cmd next = this.getNext();
      Cmd skip = this.getSkip();
      if (next != null) {
         command.setSkip(next);
         Cmd commandTailNext = command.getTail().getNext();
         if (commandTailNext == null || !(commandTailNext instanceof LoopCmd)) {
            command.getTail().forceNext(next);
         } else {

         }
      } else {
         //we are potentially changing the getScript tail, need to inform context
         //this should no longer be necessary because Dispatcher tracks the head cmd not tail
         //if(context!=null){
         //context.notifyTailMod(this,command.getTail());
         //}
      }
      Cmd toForce = command.getTail();

      //make sure that any abnormal exits from the previous script will go to the next command
      if (skip != null) {
         do {
            toForce.forceSkip(skip);
            toForce = toForce.getPrevious();
         } while (toForce != null && toForce.getSkip() == null);
      }
      command.parent = this;
      command.prev = this;
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
      this.withDef.merge(withs);
      this.withActive.merge(withs);
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
   public Json getWith(boolean recursive){
      if(!recursive){
         return withDef.clone();
      }else{
         Json rtrn = new Json();
         Cmd target = this;
         while(target!=null){
            target.getWith().forEach((key,value)->{
               if(!rtrn.has(key)){
                  if(value instanceof Json){
                     rtrn.set(key,((Json)value).clone()); //clone to avoid parallel modification
                  }else {
                     rtrn.set(key, value);
                  }
               }
            });
            target = target.getParent();
         }
         return rtrn;
      }
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
         rtrn.append(String.format("%" + correctedIndent + "s%s%s next=%s skp=%s prv=%s%n", "", prefix, this.getUid() + ":" + this, this.getNext(), this.getSkip(), this.getPrevious()));
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
      return prev;
   }

   public void setPrevious(Cmd previous) {
      prev = previous;
   }

   public Cmd getNext() {
      return next;
   }

   private void setNext(Cmd next) {
      if (this.next == null) {
         this.next = next;
      }
   }

   public void forceNext(Cmd next) {
      this.next = next;
   }

   public void forceSkip(Cmd skip) {
      this.skip = skip;
   }

   public boolean hasSkip(){return skip!=null;}
   public Cmd getSkip() {
      return skip;
   }

   protected void setSkip(Cmd skip) {
      this.skip = skip;
      if (!this.thens.isEmpty()) {
         this.thens.getLast().setNext(skip);
         this.thens.getLast().setSkip(skip);
      }
   }

   public Cmd then(Cmd command) {
      return then(command, true);
   }

   protected Cmd then(Cmd command, boolean setSkip) {
      if(isLoopBack()) {
         Cmd loopCmd = getSkip();
         assert loopCmd instanceof LoopCmd;

         command.parent = this;
         this.thens.add(command);

         clearLoopBack();
         forceNext(command);
         command.getTail().setLoopBack((LoopCmd) loopCmd);

      }else if (getTail().isLoopBack() && getTail().getSkip() == this.getSkip()){
         //if tail is a loop back to the same loop this is probably within
         Cmd loopCmd = this.getSkip();
         assert loopCmd instanceof LoopCmd;

         command.parent = this;

         getTail().clearLoopBack();
         getTail().forceNext(command);

         this.thens.add(command);

         command.getTail().setLoopBack((LoopCmd)loopCmd);



      }else {
         setNext(command);
         if (setSkip && !this.thens.isEmpty()) {
            Cmd previousThen = this.thens.getLast();
            previousThen.setSkip(command);
            previousThen.setNext(command);

            command.setPrevious(previousThen);
         } else {
            command.setPrevious(this);
         }
         command.parent = this;
         this.thens.add(command);
      }
      return this;
   }

   public Cmd watch(Cmd command) {
      command.parent = this;
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


   protected final void doRun(String input, Context context) {

      if (!withDef.isEmpty()) {
         //replace with values if they have ${{
         withDef.forEach((k,v)->{
            if(v instanceof String && ((String) v).indexOf(StringUtil.PATTERN_PREFIX) > -1){
               String populatedV =populateStateVariables((String)v,this,context.getState(),new Ref(this));
               Object convertedV = State.convertType(populatedV);
               withActive.set(k,convertedV);
            }
         });
         //TODO the is currently being handles by populateStateVariables
         // a good idea might be to create a new context for this and this.then()
//            for(String key : with.keySet()){
//                context.getState().set(key,with.get(key));
//            }
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

   public String getLogOutput(String output, Context context) {
      if (output == null || isSilent() || (getPrevious() != null && output.equals(getPrevious().getOutput()))) {
         return this.toString();
      } else {
         return this.toString() + "\n" + output;
      }
   }

   public Cmd deepCopy() {
      Cmd clone = this.copy().with(this.getWith());
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

      while (!rtrn.getThens().isEmpty()) {
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

   public Cmd getParent() {
      return parent;
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
