package perf.qdup.cmd;

import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import perf.qdup.State;
import perf.qdup.cmd.impl.*;
import perf.yaup.HashedLists;
import perf.yaup.StringUtil;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;
import java.io.Reader;
import java.io.Writer;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by wreicher
 * Base for all the commands than can be added to a Script. Commands are created through the static methods.
 */
public abstract class Cmd {
    private static final JexlEngine jexl = new JexlBuilder().cache(512).strict(true).silent(false).create();
    private static final ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
    static {
        try {
            engine.eval("function milliseconds(v){ return Packages.perf.qdup.cmd.impl.Sleep.parseToMs(v)}");
            engine.eval("function seconds(v){ return Packages.perf.qdup.cmd.impl.Sleep.parseToMs(v)/1000}");
        } catch (ScriptException e) {
            e.printStackTrace();
        }
    }
    private static class JexlStateContext implements JexlContext {

        private State state;
        public JexlStateContext(State state){
            this.state = state;
        }

        @Override
        public Object get(String name) {
            if(state.has(name)) {
                String value = state.get(name);
                if (value.matches("\\d+")) {
                    return Long.parseLong(value);
                } else if (value.matches("\\d*\\.\\d+")) {
                    return Double.parseDouble(value);
                }
            }
            return null;
        }

        @Override
        public void set(String name, Object value) {

        }

        @Override
        public boolean has(String name) {
            return state.has(name,true);
        }
    }
    private static class NashonStateContext implements javax.script.ScriptContext{

        private final State state;
        private final ScriptContext parent;

        private Object fromState(String key){
            String val = state.get(key);
            if(val.matches("\\d+")){
                return Long.parseLong(val);
            }else if (val.matches("\\d*\\.\\d+")){
                return Double.parseDouble(val);
            }else{
                return val;
            }
        }

        public NashonStateContext(State state){
            this(state,new SimpleScriptContext());
        }
        public NashonStateContext(State state,javax.script.ScriptContext parent){
            this.state = state;
            this.parent = parent;
        }

        @Override
        public void setBindings(Bindings bindings, int i) {
            parent.setBindings(bindings,i);
        }

        @Override
        public Bindings getBindings(int i) {
            return parent.getBindings(i);
        }

        @Override
        public void setAttribute(String s, Object o, int i) {

            parent.setAttribute(s,o,i);
        }

        @Override
        public Object getAttribute(String s, int i) {
            if(state.has(s,true)){
                return fromState(s);
            }else{
                return parent.getAttribute(s,i);
            }
        }

        @Override
        public Object removeAttribute(String s, int i) {
            return parent.removeAttribute(s,i);
        }

        @Override
        public Object getAttribute(String s) {
            if(state.has(s,true)){
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
        protected void setSkip(Cmd skip){
            //prevent propagating skip to last then because it needs to skip to this
            this.forceSkip(skip);
        }

        @Override
        public Cmd then(Cmd command){
            Cmd commandTail = command.getTail();
            Cmd currentTail = this.getTail();
            Cmd rtrn = super.then(command,true);
            currentTail.forceNext(command);
            if(!this.equals(currentTail)){
                currentTail.forceSkip(command);//if current tail is skipping it's children
            }
            commandTail.forceNext(this);
            command.setSkip(this);
            return rtrn;
        }

    }

    public static class Ref {
        private Ref parent;
        private Cmd command;

        public Ref(Cmd command){
            this.command = command;
        }
        public Ref add(Cmd command){
            Ref rtrn = new Ref(command);
            rtrn.parent = this;
            return rtrn;
        }

        public Ref getParent() {
            return parent;
        }
        public boolean hasParent(){return parent!=null;}

        public Cmd getCommand() {
            return command;
        }
    }


    private static class NO_OP extends Cmd{
        @Override
        public void run(String input, Context context) {
            context.next(input);
        }
        @Override
        public Cmd copy() {
            return new NO_OP().with(this.with);
        }
        @Override
        public String toString(){
            return "NO_OP";
        }
    }

    protected final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

    public static final String STATE_PREFIX = "${{";
    public static final String STATE_SUFFIX = "}}";
    public static final Pattern STATE_PATTERN = Pattern.compile("\\$\\{\\{(?<name>[^${}:]+):?(?<default>[^}]*)}}");

    public static final String ENV_PREFIX = "${";
    public static final String ENV_SUFFIX = "}";
    public static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{(?<name>[^\\{][^\\}]*)}");

    public static final Pattern NAMED_CAPTURE = java.util.regex.Pattern.compile("\\(\\?<([^>]+)>");

    public static Cmd js(String code){return new JsCmd(code);}
    public static Cmd done(){return new Done();}
    public static Cmd NO_OP(){return new NO_OP();}
    public static Cmd abort(String message){return new Abort(message);}
    public static Cmd code(Code code){return new CodeCmd(code);}
    public static Cmd code(String className){return new CodeCmd(className);}
    public static Cmd countdown(String name,int initial){return new Countdown(name,initial);}
    public static Cmd ctrlC(){return new CtrlC();}
    public static Cmd download(String path){return new Download(path);}
    public static Cmd download(String path,String destination){return new Download(path,destination);}
    public static Cmd upload(String path,String destination){return new Upload(path,destination);}
    public static Cmd exitCode(){return new ExitCode("");}
    public static Cmd exitCode(String expected){return new ExitCode(expected);}
    public static Cmd echo(){ return new Echo(); }
    public static Cmd forEach(String name){ return new ForEach(name);}
    public static Cmd forEach(String name,String input){ return new ForEach(name,input);}
    public static Cmd invoke(Cmd command){ return new InvokeCmd(command);}
    public static Cmd log(String value){ return new Log(value); }
    public static Cmd queueDownload(String path){return new QueueDownload(path);}
    public static Cmd queueDownload(String path,String destination){return new QueueDownload(path,destination);}
    public static Cmd readState(String name){return new ReadState(name);}
    public static Cmd reboot(String timeout,String target,String password){return new Reboot(timeout,target,password);}
    public static Cmd regex(String pattern){
        return new Regex(pattern);
    }
    public static Cmd repeatUntil(String name){return new RepeatUntilSignal(name);}
    public static ScriptCmd script(String name){ return new ScriptCmd(name,false); }
    public static ScriptCmd script(String name,boolean async){ return new ScriptCmd(name,async); }
    public static Cmd setState(String name){return new SetState(name);}
    public static Cmd setState(String name,String value){return new SetState(name,value);}
    public static Cmd sh(String command){
        return new Sh(command);
    }
    public static Cmd sh(String command,boolean silent){
        return new Sh(command,silent);
    }
    public static Cmd signal(String name){return new Signal(name);}
    public static Cmd sleep(String amount){return new Sleep(amount);}
    public static Cmd waitFor(String name){return new WaitFor(name);}
    public static Cmd xml(String path){return new XmlCmd(path);}
    public static Cmd xml(String path,String...operations){
        return new XmlCmd(path,operations);
    }


    private boolean hasWith(String name){

        boolean hasIt = with.containsKey(name);
        Cmd target = this.parent;
        while(!hasIt && target!=null){
            hasIt = target.with.containsKey(name);
            target = target.parent;
        }
        return hasIt;
    }
    private String getWith(String name){
        String value = with.get(name);
        Cmd target = this.parent;
        while(value==null && target!=null){
            value = target.with.get(name);
            target = target.parent;
        }
        return value;
    }
    public static String populateStateVariables(String command,Cmd cmd, State state){
        return populateStateVariables(command,cmd,state,true);
    }

    //handles recursive variable references
    protected static String populateVariable(String name, Cmd cmd,State state, Ref ref){
        String rtrn = null;
        String currentName = name;
        do {
            rtrn = null;//reset so we check ref or state with each loop
            if(currentName.startsWith(STATE_PREFIX) && currentName.endsWith(STATE_SUFFIX)){
                currentName = currentName.substring(
                    STATE_PREFIX.length(),
                    currentName.length()-STATE_SUFFIX.length()
                );

            }
            if (cmd != null && cmd.hasWith(currentName)) {
                rtrn = cmd.getWith(currentName);
            }
            if(rtrn == null && ref!=null){
                Ref targeetRef = ref;
                do {
                    if(targeetRef.getCommand()!=null && targeetRef.getCommand().hasWith(currentName)){
                        rtrn = targeetRef.getCommand().getWith(currentName);
                    }

                }while( (targeetRef=targeetRef.getParent())!=null && rtrn==null);
            }
            if (rtrn == null && state!=null){
                rtrn = state.get(currentName);
            }
        }while (rtrn!=null && (currentName=rtrn).startsWith(STATE_PREFIX));
        return rtrn;
    }
    public static String populateStateVariables(String command,Cmd cmd, State state,boolean replaceUndefined) {
        return populateStateVariables(command,cmd,state,replaceUndefined,null);
    }
    public static String populateStateVariables(String command,Cmd cmd, State state,boolean replaceUndefined,Ref ref){
        String rtrn = command;
        if(command.indexOf(STATE_PREFIX)<0)
            return command;

        int previous = 0;
        //StringBuffer rtrn = new StringBuffer();
        Matcher matcher = STATE_PATTERN.matcher(rtrn);
        while(matcher.find()){
                int findIndex = matcher.start();
                String name = matcher.group("name");
                String defaultValue = matcher.group("default");
                String value = null;

                if(StringUtil.findAny(name,"()/*^+-") > -1 ){
//                    JexlStateContext jexlState = new JexlStateContext(state);
//                    JexlExpression expression = jexl.createExpression(name);
//                    value = expression.evaluate(jexlState).toString();
                    try {
                        Object nashonVal = engine.eval(name,new NashonStateContext(state,engine.getContext()));
                        value = nashonVal.toString();
                        if(value.endsWith(".0")){
                            value = value.substring(0,value.length()-2);
                        }
                    } catch (ScriptException|IllegalArgumentException e) {
                        //e.printStackTrace();
                    }
                }else {
                    value = populateVariable(name, cmd, state, ref);
                }
                if (value == null) {//bad times
                    if (!defaultValue.isEmpty()) {
                        value = defaultValue;
                    } else if (defaultValue.isEmpty() && ':' == rtrn.charAt(matcher.end("name"))) {
                        //TODO how to alert the missing state? It could be intentional (e.g. nothing to wait-for)
                        value = defaultValue;
                        //logger.debug("missing {} state variable for {}", name, command);
                    } else if (replaceUndefined) {
                        value = "";
                    } else {
                        //value = STATE_PREFIX+name+STATE_SUFFIX; // do nothing, it will stay the same
                    }

                }
//            rtrn.append(value);
                previous = matcher.end();
                if (value != null) {
                    rtrn = rtrn.replace(rtrn.substring(findIndex, previous), value);
                    matcher.reset(rtrn);
                }

        }
//        if(previous<command.length()){
//            rtrn.append(command.substring(previous));
//        }
        return rtrn.toString();
    }


    private static final AtomicInteger uidGenerator = new AtomicInteger(0);

    protected Map<String,String> with;
    private LinkedList<Cmd> thens;
    private LinkedList<Cmd> watchers;
    private HashedLists<Long,Cmd> timers;

    private Cmd parent;
    private Cmd prev;
    private Cmd next;
    private Cmd skip;

    int uid;

    protected boolean silent = false;

    private String output;

    protected Cmd(){
        this(false);
    }
    protected Cmd(boolean silent){
        this.silent = silent;
        this.with = new HashMap<>();
        this.thens = new LinkedList<>();
        this.watchers = new LinkedList<>();
        this.timers = new HashedLists<>();
        this.next = null;
        this.skip = null;
        this.prev = null;
        this.parent = null;
        this.uid = uidGenerator.incrementAndGet();
    }
    public Cmd addTimer(long timeout,Cmd command){
        timers.put(timeout,command);
        return this;
    }
    public List<Cmd> getTimers(long timeout){
        return timers.get(timeout);
    }
    public Set<Long> getTimeouts(){return timers.keys();}
    public boolean hasTimers(){return !timers.isEmpty();}
    public void injectThen(Cmd command,Context context){
        thens.addFirst(command);

        Cmd next = this.getNext();
        Cmd skip = this.getSkip();
        if(next!=null){
            command.setSkip(next);
            Cmd commandTailNext = command.getTail().getNext();
            if( commandTailNext==null || !(commandTailNext instanceof LoopCmd) ){
                command.getTail().forceNext(next);
            }else{

            }
        }else{
            //we are potentially changing the getScript tail, need to inform context
            //this should no longer be necessary because Dispatcher tracks the head cmd not tail
            //if(context!=null){
                //context.notifyTailMod(this,command.getTail());
            //}
        }
        Cmd toForce = command.getTail();

        //make sure that any abnormal exits from the previous script will go to the next command
        if(skip!=null) {
            do {
                toForce.forceSkip(skip);
                toForce = toForce.getPrevious();
            } while (toForce != null && toForce.getSkip() == null);
        }
        this.next = command;
        command.parent = this;
        command.prev = this;
    }

    public boolean isSilent(){return silent;}

    public Cmd with(Map<String,String> withs){
        this.with.putAll(withs);
        return this;
    }
    public Cmd with(String key,String value){
        with.put(key,value);
        return this;
    }
    public Map<String,String> getWith(){return Collections.unmodifiableMap(with);}

    public int getUid(){return uid;}

    public String tree(){
        return tree(0,false);
    }
    public String tree(int indent,boolean debug){
        StringBuffer rtrn = new StringBuffer();
        tree(rtrn,indent,"",debug);
        return rtrn.toString();
    }
    private void tree(StringBuffer rtrn,int indent,String prefix,boolean debug){
        final int correctedIndent = indent == 0 ? 1 : indent;
        if(debug) {
            rtrn.append(String.format("%" + correctedIndent + "s%s%s next=%s skp=%s prv=%s%n", "", prefix, this.getUid()+":"+this, this.getNext(), this.getSkip(), this.getPrevious()) );
        }else{
            rtrn.append(String.format("%" + correctedIndent + "s%s%s %n", "", prefix, this.toString()) );
        }
        with.forEach((k,v)->{
            String value = String.format("%"+(correctedIndent+4)+"swith: %s=%s%n","",k,v);
            rtrn.append(value);
        });
        watchers.forEach((w)->{w.tree(rtrn,correctedIndent+4,"watch:",debug);});
        timers.forEach((timeout,cmdList)->{
            rtrn.append(String.format("%"+(correctedIndent+4)+"stimer: %d%n","",timeout));
            cmdList.forEach(cmd->{
                cmd.tree(rtrn,correctedIndent+6,"",debug);
            });
        });
        thens.forEach((t)->{t.tree(rtrn,correctedIndent+2,"",debug);});
    }

    public String getOutput(){return output;}
    public void setOutput(String output){
        this.output = output;
    }

    public Cmd getPrevious(){return prev;}
    public void setPrevious(Cmd previous){
        prev = previous;
    }
    public Cmd getNext(){return next;}
    private void setNext(Cmd next){
        if(this.next == null) {
            this.next = next;
        }
    }

    public void forceNext(Cmd next){
        this.next = next;
    }
    public void forceSkip(Cmd skip){
        this.skip = skip;
    }
    public Cmd getSkip(){return skip;}
    protected void setSkip(Cmd skip){
        this.skip = skip;
        if(!this.thens.isEmpty()){
            this.thens.getLast().setNext(skip);
            this.thens.getLast().setSkip(skip);
        }
    }

    public Cmd then(Cmd command) {
        return then(command,true);
    }
    protected Cmd then(Cmd command,boolean setSkip){
        setNext(command);
        if(setSkip && !this.thens.isEmpty()){
            Cmd previousThen = this.thens.getLast();
            previousThen.setSkip(command);
            previousThen.setNext(command);

            command.setPrevious(previousThen);
        }else{
            command.setPrevious(this);
        }
        command.parent=this;
        this.thens.add(command);
        return this;
    }
    public Cmd watch(Cmd command){
        command.parent=this;
        this.watchers.add(command);
        return this;
    }

    public boolean hasThens(){return !thens.isEmpty();}
    public List<Cmd> getThens(){return Collections.unmodifiableList(this.thens);}

    public boolean hasWatchers(){return !this.watchers.isEmpty();}
    public List<Cmd> getWatchers(){return Collections.unmodifiableList(this.watchers);}


    protected final void doRun(String input, Context context){
        if(!with.isEmpty()){
            //TODO the is currently being handles by populateStateVariables
            // a good idea might be to create a new context for this and this.then()
//            for(String key : with.keySet()){
//                context.getState().set(key,with.get(key));
//            }
        }
        run(input,context);
    }
    public abstract void run(String input, Context context);
    public abstract Cmd copy();
    public String getLogOutput(String output,Context context){
        if (output == null || isSilent() || (getPrevious()!=null && output.equals(getPrevious().getOutput()))){
            return this.toString();
        }else{
            return this.toString()+"\n"+output;
        }
    }

    public Cmd deepCopy(){
        Cmd clone = this.copy().with(this.getWith());
        for(Cmd watcher : this.getWatchers()){
            clone.watch(watcher.deepCopy());
        }
        for(Cmd then : this.getThens()){
            clone.then(then.deepCopy());
        }
        for(long timeout: this.getTimeouts()){
            for(Cmd timed : this.getTimers(timeout)){
                clone.addTimer(timeout,timed.deepCopy());
            }
        }
        return clone;
    }
    public Cmd getLastWatcher(){
        return watchers.getLast();
    }
    public Cmd getWatcherTail(){
        Cmd rtrn = watchers.getLast();
        while(!rtrn.getThens().isEmpty()){
            rtrn = rtrn.thens.getLast();
        }
        return rtrn;
    }
    public Cmd getLastThen(){return thens.getLast();}
    public Cmd getTail(){
        Cmd rtrn = this;

        while(!rtrn.getThens().isEmpty()){
            rtrn = rtrn.thens.getLast();
        }
        return rtrn;
    }
    public Cmd getHead(){
        Cmd rtrn = this;
        while(rtrn.parent!=null){
            rtrn = rtrn.parent;
        }
        return rtrn;
    }

    @Override
    public int hashCode(){
        return getUid();
    }
    @Override
    public boolean equals(Object object){
        if(object==null){
            return false;
        }
        if(object instanceof Cmd){
            return this.getUid() == ((Cmd)object).getUid();
        }
        return false;
    }
    public boolean isSame(Cmd command){
        return command!=null && this.toString().equals(command.toString());
    }

}
