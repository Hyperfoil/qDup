package perf.ssh.cmd;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import perf.ssh.State;
import perf.ssh.cmd.impl.*;
import perf.yaup.HashedLists;

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

    private static class NO_OP extends Cmd{
        @Override
        protected void run(String input, Context context, CommandResult result) {
            result.next(this,input);
        }
        @Override
        protected Cmd clone() {
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
    public static final Pattern STATE_PATTERN = Pattern.compile("\\$\\{\\{(?<name>[^}]+)}}");

    public static final String ENV_PREFIX = "${";
    public static final String ENV_SUFFIX = "}";
    public static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{(?<name>[^\\{][^\\}]*)}");

    public static final Pattern NAMED_CAPTURE = java.util.regex.Pattern.compile("\\(\\?<([^>]+)>");

    public static Cmd NO_OP(){return new NO_OP();}
    public static Cmd abort(String message){return new Abort(message);}
    public static Cmd code(Code code){return new CodeCmd(code);}
    public static Cmd code(String className){return new CodeCmd(className);}
    public static Cmd countdown(String name,int initial){return new Countdown(name,initial);}
    public static Cmd ctrlC(){return new CtrlC();}
    public static Cmd download(String path){return new Download(path);}
    public static Cmd download(String path,String destination){return new Download(path,destination);}
    public static Cmd upload(String path,String destination){return new Upload(path,destination);}
    public static Cmd echo(){ return new Echo(); }
    public static Cmd invoke(Cmd command){ return new InvokeCmd(command);}
    public static Cmd log(String value){ return new Log(value); }
    public static Cmd queueDownload(String path){return new QueueDownload(path);}
    public static Cmd queueDownload(String path,String destination){return new QueueDownload(path,destination);}
    public static Cmd readState(String name){return new ReadState(name);}
    public static Cmd regex(String pattern){
        return new Regex(pattern);
    }
    public static Cmd repeatUntil(String name){return new RepeatUntilSignal(name);}
    public static ScriptCmd script(String name){ return new ScriptCmd(name); }
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
    public static Cmd xpath(String path){return new XPath(path);}

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

        if(command.indexOf(STATE_PREFIX)<0)
            return command;

        int previous = 0;
        StringBuffer rtrn = new StringBuffer();
        Matcher matcher = STATE_PATTERN.matcher(command);
        while(matcher.find()){
            int findIndex = matcher.start();
            if(findIndex > previous){
                rtrn.append(command.substring(previous,findIndex));
            }
            String name = matcher.group("name");
            String value = null;
            if(cmd!=null && cmd.hasWith(name)){
                value = cmd.getWith(name);
            }else {
                value = state.get(name);
            }
            if(value == null ){//bad times
                logger.debug("missing {} state variable for {}",name,command);
                //TODO how to alert the missing state
                value = "";
            }
            rtrn.append(value);
            previous = matcher.end();
        }
        if(previous<command.length()){
            rtrn.append(command.substring(previous));
        }
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
        Cmd next = this.next;
        Cmd skip = this.skip;
        if(next!=null){
            command.getTail().next = next;
        }else{
            //we are potentially changing the getScript tail, need to inform context
            //this should no longer be necessary because CommandDispatcher tracks the head cmd not tail
            if(context!=null){
                context.notifyTailMod(this,command.getTail());
            }
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
            rtrn.append(String.format("%" + correctedIndent + "s%s%s next=%s skp=%s prv=%s%n", "", prefix, this, this.getNext(), this.getSkip(), this.getPrevious()) );
        }else{
            rtrn.append(String.format("%" + correctedIndent + "s%s%s %n", "", prefix, this) );
        }

        with.forEach((k,v)->{
            String value = String.format("%"+(correctedIndent+4)+"swith: %s=%s%n","",k.toString(),v.toString());
            rtrn.append(value);
        });
        watchers.forEach((w)->{w.tree(rtrn,correctedIndent+4,"watch:",debug);});
        timers.forEach((timeout,cmdList)->{
            rtrn.append(String.format("%"+(correctedIndent+4)+"stimer: %i%n","",timeout));
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

    public Cmd getSkip(){return skip;}
    private void setSkip(Cmd skip){
        this.skip = skip;
        if(!this.thens.isEmpty()){
            this.thens.getLast().setNext(skip);
            this.thens.getLast().setSkip(skip);
        }
    }

    public Cmd then(Cmd command){
        if(next==null){
            next = command;
        }

        if(!this.thens.isEmpty()){
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


    protected final void doRun(String input,Context context,CommandResult result){
        if(!with.isEmpty()){

            //TODO the is currently being handles by populateStateVariables
            // a good idea might be to create a new context for this and this.then()
//            for(String key : with.keySet()){
//                context.getState().set(key,with.get(key));
//            }
        }
        run(input,context,result);
    }
    protected abstract void run(String input, Context context, CommandResult result);

    protected abstract Cmd clone();

    public Cmd deepCopy(){
        Cmd clone = this.clone();
        for(Cmd watcher : this.getWatchers()){
            clone.watch(watcher.deepCopy());
        }
        for(Cmd then : this.getThens()){
            clone.then(then.deepCopy());
        }
        for(long timeout: this.getTimeouts()){
            for(Cmd timed : this.getTimers(timeout)){
                clone.addTimer(timeout,timed);
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
