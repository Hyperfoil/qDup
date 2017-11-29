package perf.ssh.cmd;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import perf.ssh.State;
import perf.ssh.cmd.impl.*;

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by wreicher
 * Base for all the commands than can be added to a Script. Commands are created through the static methods.
 */
public abstract class Cmd {


    private static final Code NO_OP = (i, s)->Result.next(i);

    protected final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

    public static final String ENV_PREFIX = "${{";
    public static final String ENV_SUFFIX = "}}";
    public static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{\\{(?<name>[^}]+)}}");
    public static final Pattern NAMED_CAPTURE = java.util.regex.Pattern.compile("\\(\\?<([^>]+)>");


    public static Cmd NO_OP(){return new CodeCmd(NO_OP);}
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
    public static Cmd signal(String name){return new Signal(name);}
    public static Cmd sleep(long amount){return new Sleep(amount);}
    public static Cmd waitFor(String name){return new WaitFor(name);}
    public static Cmd xpath(String path){return new XPath(path);}

    public static String populateStateVariables(String command,State state){

        if(command.indexOf(ENV_PREFIX)<0)
            return command;

        int previous = 0;
        StringBuffer rtrn = new StringBuffer();
        Matcher matcher = ENV_PATTERN.matcher(command);
        while(matcher.find()){
            int findIndex = matcher.start();
            if(findIndex > previous){
                rtrn.append(command.substring(previous,findIndex));
            }
            String name = matcher.group("name");
            String value = state.get(name);
            if(value == null ){//bad times
                logger.error("missing {} value for {}",name,command);
                //TODO how to propegate missing state
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

    private LinkedList<Cmd> thens;
    private LinkedList<Cmd> watchers;

    private Cmd parent;
    private Cmd prev;
    private Cmd next;
    private Cmd skip;

    int uid;

    private String output;

    protected Cmd(){
        this.thens = new LinkedList<>();
        this.watchers = new LinkedList<>();
        this.next = null;
        this.skip = null;
        this.prev = null;
        this.parent = null;
        this.uid = uidGenerator.incrementAndGet();
    }
    protected void injectThen(Cmd command,Context context){
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
        watchers.forEach((w)->{w.tree(rtrn,correctedIndent+4,"watch:",debug);});
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

    protected List<Cmd> getThens(){return Collections.unmodifiableList(this.thens);}
    protected List<Cmd> getWatchers(){return Collections.unmodifiableList(this.watchers);}

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
