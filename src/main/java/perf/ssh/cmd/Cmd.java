package perf.ssh.cmd;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import perf.ssh.Local;
import perf.ssh.State;
import perf.util.AsciiArt;
import perf.util.file.FileUtility;
import perf.util.xml.Xml;
import perf.util.xml.XmlLoader;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by wreicher
 * All the commands than can be added to a Script. Commands are created through the static methods.
 */
public abstract class Cmd {

    final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

    public static final String ENV_PREFIX = "${{";
    public static final String ENV_SUFFIX = "}}";
    public static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{\\{(?<name>[^}]+)}}");
    public static final Pattern NAMED_CAPTURE = java.util.regex.Pattern.compile("\\(\\?<([^>]+)>");


    public static Cmd abort(){return new Abort();}
    public static Cmd code(Code code){return new CodeCmd(code);}
    public static Cmd queueDownload(String path){return new DelayedDownload(path);}
    public static Cmd queueDownload(String path,String destination){return new DelayedDownload(path,destination);}
    public static Cmd download(String path){return new Download(path);}
    public static Cmd download(String path,String destination){return new Download(path,destination);}
    public static Cmd echo(){ return new Echo(); }
    public static Cmd log(String value){ return new Log(value); }
    public static Cmd script(String name){ return new ScriptCmd(name); }
    public static Cmd invoke(Cmd command){ return new InvokeCmd(command);}
    public static Cmd sh(String command){
        return new Sh(command);
    }
    public static Cmd regex(String pattern){
        return new Filter(pattern);
    }
    public static Cmd ctrlC(){return new CtrlC();}
    public static Cmd sleep(long amount){return new Sleep(amount);}
    public static Cmd waitFor(String name){return new WaitFor(name);}
    public static Cmd signal(String name){return new Signal(name);}
    public static Cmd xpath(String path){return new XPath(path);}
    public static Cmd countdown(String name,int amount){return new Countdown(name,amount);}

    static class DelayedDownload extends Cmd {
        private String path;
        private String destination;
        public DelayedDownload(String path,String destination){
            this.path = path;
            this.destination = destination;
        }
        public DelayedDownload(String path){
            this(path,"");
        }
        public String getPath(){return path;}
        public String getDestination(){return destination;}


        @Override
        public String toString(){return "DelayedDownload "+path+" -> "+destination;}

        @Override
        protected void run(String input, CommandContext context, CommandResult result) {
            String basePath = context.getRunOutputPath()+File.separator+context.getSession().getHostName();
            String resolvedPath = Cmd.populateStateVariables(getPath(),context.getState());
            String resolvedDestination = Cmd.populateStateVariables(basePath + File.separator + getDestination(),context.getState());

            context.addPendingDownload(resolvedPath,resolvedDestination);

            File destinationFile = new File(resolvedDestination);
            if(!destinationFile.exists()){
                destinationFile.mkdirs();
            }
            result.next(this,input);

        }

        @Override
        protected Cmd clone() {
            return new DelayedDownload(path,destination);
        }
    }
    static class Abort extends Cmd {
        public Abort(){}

        @Override
        protected void run(String input, CommandContext context, CommandResult result) {
            context.abort();
            //don't call result.next or skip to force script to pause until stopped by CommandDispatcher
        }

        @Override
        protected Cmd clone() {
            return new Abort();
        }

        @Override
        public String toString(){return "abort";}
    }
    static class InvokeCmd extends Cmd {
        private Cmd command;
        public InvokeCmd(Cmd command){
            this.command = command.copy();
            //moved here from run to avoid issue where dispatcher has the wrong tail cmd

            injectThen(command,null);//null context so we don't updated tail change
        }
        public Cmd getCommand(){return command;}

        @Override
        protected void run(String input, CommandContext context, CommandResult result) {
            result.next(this,input);
        }

        @Override
        protected Cmd clone() {
            return new InvokeCmd(command.copy());
        }
        @Override
        public String toString(){return "invoke "+command.toString();}
    }
    static class CodeCmd extends Cmd {
        private Code code;
        public CodeCmd(Code code){
            this.code = code;
        }
        public Code getCode(){return code;}
        @Override
        protected void run(String input, CommandContext context, CommandResult result) {
            Result codeResult = code.run(input,context.getState());
            switch(codeResult.getType()){
                case skip:
                    result.skip(this,codeResult.getResult());
                    break;
                default:
                    result.next(this,codeResult.getResult());
            }
        }
        @Override
        protected Cmd clone() {
            return new CodeCmd(code);
        }
        @Override
        public String toString(){return "Code "+code.toString();}
    }
    static class Echo extends Cmd {
        public Echo(){}

        @Override
        protected void run(String input, CommandContext context, CommandResult result) {
            System.out.println(input); //TODO should echo also go to the run logger?
            result.next(this,input);
        }

        @Override
        protected Cmd clone() {
            return new Echo();
        }
        @Override
        public String toString(){return "echo";}
    }
    static class Log extends Cmd {
        String value;
        public Log(String value){ this.value = value; }
        @Override
        protected void run(String input, CommandContext context, CommandResult result) {
            context.getRunLogger().info(Cmd.populateStateVariables(value,context.getState()));
            result.next(this,input);
        }
        @Override
        protected Cmd clone() {
            return new Log(value);
        }
        @Override
        public String toString(){return "LOG: "+this.value;}
    }
    static class Countdown extends Cmd {
        private String name;
        private int startCount;
        public Countdown(String name,int count){

            this.name = name;
            this.startCount = count;

        }
        @Override
        protected void run(String input, CommandContext context, CommandResult result) {
            int newCount = context.getCoordinator().decrease(this.name,this.startCount);

            if(newCount == 0){
                result.next(this,input);
            }else{
                result.skip(this,input);
            }
        }
        @Override
        protected Cmd clone() { return new Countdown(this.name,this.startCount); }
        @Override
        public String toString(){return "decrease "+this.name+" ("+this.startCount+")";}
    }

    static class Sh extends Cmd {
        private String command;
        public Sh(String command){
            this.command = command;
        }
        @Override
        protected void run(String input, CommandContext context, CommandResult result) {

            context.getSession().setCommand(this,result);
            context.getSession().sh(populateStateVariables(command,context.getState()));

        }

        @Override
        protected Cmd clone() {
            return new Sh(this.command);
        }

        @Override public String toString(){return ""+command;}
    }
    static class CtrlC extends Cmd {
        public CtrlC(){}
        @Override
        protected void run(String input, CommandContext context, CommandResult result) {
            context.getSession().ctrlC();
            result.next(this,input); //now waits for shell to return prompt
        }

        @Override
        protected Cmd clone() {
            return new CtrlC();
        }

        @Override public String toString(){return "^C";}
    }
    static class WaitFor extends Cmd {
        private String name;
        public WaitFor(String name){ this.name = name;}
        @Override
        protected void run(String input, CommandContext context, CommandResult result) {
            context.getCoordinator().waitFor(name);
            result.next(this,input);
        }

        @Override
        protected Cmd clone() {
            return new WaitFor(this.name);
        }

        public String getName(){return name;}
        @Override public String toString(){return "waitFor "+name;}
    }
    static class XPath extends Cmd {
        String path;
        public XPath(String path){
            this.path = path;
        }

        @Override
        public String toString(){
            return path;
        }

        @Override
        protected void run(String input, CommandContext context, CommandResult result) {
            XmlLoader loader = new XmlLoader();
            Xml xml = null;
            if(path.indexOf(FileUtility.SEARCH_KEY)>0){ //file>xpath ...
                String filePath = Cmd.populateStateVariables(path.substring(0,path.indexOf(FileUtility.SEARCH_KEY)),context.getState());
                path = path.substring(path.indexOf(FileUtility.SEARCH_KEY)+FileUtility.SEARCH_KEY.length());
                try {
                    File tmpDest = File.createTempFile("cmd-"+this.getUid()+"-"+context.getSession().getHostName(),"."+System.currentTimeMillis());
                    Local.get().download(filePath,tmpDest.getPath(),context.getSession().getHost());
                    logger.info("{} tmpFile.length={}",this,tmpDest.length());
                    xml = loader.loadXml(tmpDest.toPath());
                    int opIndex = FileUtility.OPERATIONS.stream().mapToInt(op->{
                        int rtrn = path.indexOf(op);
                        logger.info("{} => {}",op,rtrn);
                        return rtrn;
                    }).max().getAsInt();
                    logger.info("opIndex = {}",opIndex);
                    String search = opIndex>-1 ? Cmd.populateStateVariables(path.substring(0,opIndex),context.getState()).trim() : path;
                    String operation = opIndex>-1 ? Cmd.populateStateVariables(path.substring(opIndex),context.getState()).trim() : "";

                    logger.info("search=[{}] operation=[{}]",search,operation);
                    xml = loader.loadXml(tmpDest.toPath());
                    List<Xml> found = xml.getAll(search);
                    if(operation.isEmpty()){
                        logger.info("empty operation");
                        //convert found to a string and send it to next
                        String rtrn = found.stream().map(Xml::toString).collect(Collectors.joining("\n"));
                        tmpDest.delete();
                        result.next(this,rtrn);
                    }else{

                        found.forEach(x->x.modify(operation));
                        try(  PrintWriter out = new PrintWriter( tmpDest )  ){
                            out.print(xml.documentString());
                        }
                        Local.get().upload(tmpDest.getPath(),filePath,context.getSession().getHost());
                        tmpDest.delete();
                        result.next(this,input);//TODO decide a more appropriate output
                    }
                } catch (IOException e) {
                    logger.error("{}@{} failed to create local tmp file",this.toString(),context.getSession().getHostName(),e);

                }

            }else{
                //assume the input is the xml to process
                xml = loader.loadXml(input);
            }

            int idx = -1;
            if( (idx=path.indexOf(FileUtility.ADD_OPERATION)) > -1){
                String toAdd = path.substring(idx+FileUtility.ADD_OPERATION.length());
                path = path.substring(0,idx);
                switch(toAdd.charAt(0)){
                    case '@':

                        break;
                    case '<':

                        break;
                    default:

                }
            }else if ( (idx=path.indexOf(FileUtility.DELETE_OPERATION))>-1 ){
                String toDelete = path.substring(idx+FileUtility.DELETE_OPERATION.length());
                path = path.substring(0,idx);

            }else if ( (idx=path.indexOf(FileUtility.SET_OPERATION))>-1 ){
                String toSet = path.substring(idx+FileUtility.SET_OPERATION.length());
                path = path.substring(0,idx);

            }else{
                //no operation, just search and pass results to next cmd
            }


            List<Xml> matches = xml.getAll(path);
            if(!matches.isEmpty()){
                StringBuffer response = new StringBuffer();
                for(Xml match : matches){
                    result.update(this,xml.toString());
                    response.append(match.toString());
                    response.append(System.lineSeparator());
                }
                result.next(this,response.toString().trim());
            }else{
                result.skip(this,input);
            }
        }

        @Override
        protected Cmd clone() {
            return new XPath(this.path);
        }
    }
    static class Sleep extends Cmd {
        long amount;
        public Sleep(long amount){this.amount = amount;}
        @Override
        protected void run(String input, CommandContext context, CommandResult result) {
            try {
                Thread.sleep(amount);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.interrupted();
            } finally {
                result.next(this,input);
            }

        }

        @Override
        protected Cmd clone() {
            return new Sleep(this.amount);
        }

        @Override public String toString(){return "sleep "+amount;}
    }
    static class Signal extends Cmd {
        private String name;
        public Signal(String name){ this.name = name;}
        public String getName(){return name;}
        @Override
        protected void run(String input, CommandContext context, CommandResult result) {
            context.getCoordinator().signal(name);
            result.next(this,input);
        }

        @Override
        protected Cmd clone() {
            return new Signal(this.name);
        }

        @Override public String toString(){return "signal "+name;}
    }
    static class ScriptCmd extends Cmd {
        private String name;
        public ScriptCmd(String name){

            this.name = name;
        }
        public String getName(){return name;}
        @Override
        public String toString(){return "invoke "+name;}

        @Override
        protected void run(String input, CommandContext context, CommandResult result) {
            Script toCall = context.getScript(this.name);
            injectThen(toCall.copy(),context);
            result.next(this,input);
        }

        @Override
        protected Cmd clone() {
            return new ScriptCmd(name);
        }
    }
    static class Download extends Cmd {
        private String path;
        private String destination;
        public Download(String path,String destination){
            this.path = path;
            this.destination = destination;
        }
        public Download(String path){
            this(path,"");
        }
        public String getPath(){return path;}
        public String getDestination(){return destination;}
        @Override
        protected void run(String input, CommandContext context, CommandResult result) {

            String basePath = context.getRunOutputPath()+File.separator+context.getSession().getHostName();
            String userName = context.getSession().getUserName();
            String hostName = context.getSession().getHostName();
            String remotePath = populateStateVariables(path,context.getState());
            String destinationPath =  populateStateVariables(basePath + File.separator +destination,context.getState());
            File destinationFile = new File(destinationPath);
            if(!destinationFile.exists()){
                destinationFile.mkdirs();
            }

            Local.get().download(remotePath,destinationPath,context.getSession().getHost());
            result.next(this,path);
        }

        @Override
        protected Cmd clone() {
            return new Download(this.path,this.destination);
        }
        @Override
        public String toString(){return "download "+path+" -> "+destination;}
    }
    static class Filter extends Cmd {
        private Pattern pattern;
        private String patternString;
        public Filter(String pattern){

            this.patternString = pattern;
            this.pattern = Pattern.compile(pattern,Pattern.DOTALL);

        }
        public String getPattern(){return patternString;}
        @Override
        protected void run(String input, CommandContext context, CommandResult result) {

            Matcher matcher = pattern.matcher(input);
            if(matcher.matches()){
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
                result.skip(this,input);
            }
        }

        @Override
        protected Cmd clone() {
            return new Filter(this.patternString);
        }

        @Override public String toString(){return "/"+patternString+"/";}
    }

    private static final AtomicInteger uidGeneratore = new AtomicInteger(0);

    private LinkedList<Cmd> thens;
    private LinkedList<Cmd> watchers;


    private Cmd next;
    private Cmd skip;

    private int uid;

    protected Cmd(){
        this.thens = new LinkedList<>();
        this.watchers = new LinkedList<>();
        this.next = null;
        this.skip = null;
        this.uid = uidGeneratore.incrementAndGet();
    }
    protected void injectThen(Cmd command,CommandContext context){
        thens.addFirst(command);
        Cmd next = this.next;
        Cmd skip = this.skip;
        if(next!=null){
            command.getTail().next = next;
        }else{
            //we are potentially changing the script tail, need to inform context
            if(context!=null){
                context.notifyTailMod(this,command.getTail());
            }
        }

        this.next = command;
    }

    private static String populateStateVariables(String command,State state){
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
                System.err.printf("missing "+name+" value for "+command);
                //TODO how to propegate missing state
                return null;
            }
            rtrn.append(value);
            previous = matcher.end();
        }
        if(previous<command.length()){
            rtrn.append(command.substring(previous));
        }
        return rtrn.toString();
    }


    public int getUid(){return uid;}


    public String tree(){
        return tree(0);
    }
    public String tree(int indent){
        StringBuffer rtrn = new StringBuffer();
        tree(rtrn,indent,"");
        return rtrn.toString();
    }
    private void tree(StringBuffer rtrn,int indent,String prefix){
        final int correctedIndent = indent == 0 ? 1 : indent;
        rtrn.append( String.format("%"+correctedIndent+"s%s%s %n","",prefix,this) );
        watchers.forEach((w)->{w.tree(rtrn,correctedIndent+4,"watcher:");});
        thens.forEach((t)->{t.tree(rtrn,correctedIndent+2,"");});
    }

    public Cmd getNext(){return next;}
    private void setNext(Cmd next){
        if(this.next == null) {
            this.next = next;
        }
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
        }
        this.thens.add(command);
        return this;
    }
    public Cmd watch(Cmd command){
        this.watchers.add(command);
        return this;
    }

    protected List<Cmd> getThens(){return Collections.unmodifiableList(this.thens);}
    protected List<Cmd> getWatchers(){return Collections.unmodifiableList(this.watchers);}

    protected abstract void run(String input, CommandContext context, CommandResult result);
    protected abstract Cmd clone();

    public Cmd copy(){
        Cmd clone = this.clone();
        for(Cmd watcher : this.getWatchers()){
            clone.watch(watcher.copy());
        }
        for(Cmd then : this.getThens()){
            clone.then(then.copy());
        }
        return clone;
    }
    protected Cmd getTail(){
        Cmd rtrn = this;

        while(!rtrn.getThens().isEmpty()){
            rtrn = rtrn.thens.getLast();
        }
        return rtrn;
    }

}
