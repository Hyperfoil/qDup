package perf.ssh.cmd;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import perf.util.AsciiArt;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 *
 * Created by wreicher
 *
 *
 * CommandDispatch.start put the first command into the executor
 * Executor calls RunCommand.run
 *  RunCommand.run calls Cmd.run(...)
 *    Cmd.run() calls SshSession.sh(...)
 * SemaphoreStream.write checks hasSuffix
 *   calls SshSession.run()
 *     calls CommandDispatch.next(output)
 *
 *

    TODO create xpath search, json search


 */
public class CommandDispatcher {

    final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

    final static long THRESHOLD = 30_000; //30s

    public interface Observer {
        public default void onStart(Cmd command){}
        public default void onStop(Cmd command){}
        public default void onNext(Cmd command,String output){}
        public default void onSkip(Cmd command,String output){}
        public default void onUpdate(Cmd command,String output){}
        public default void onStart(){}
        public default void onStop(){}
    }
    class WatcherResult implements  CommandResult {
        private CommandContext context;
        public WatcherResult(CommandContext context){ this.context = context; }

        @Override
        public void next(Cmd command, String output) {
            observers.forEach(o->o.onNext(command,output));
            if(command.getNext()!=null){
                logger.trace("{}:{} using WatcherResult to invoke next={}",command.getUid(),command,command.getNext());
                command.getNext().run(output,this.context,this);
            }
        }

        @Override
        public void skip(Cmd command, String output) {
            observers.forEach(o->o.onSkip(command,output));
            if(command.getSkip()!=null){
                logger.trace("{} using WatcherResult to invoke skip={}",command,command.getSkip());
                command.getSkip().run(output,this.context,this);
            }
        }

        @Override
        public void update(Cmd command, String output) {
            logger.warn("{} trying to update using a WatcherResult",command);
        }
    }
    /**
     * Stores the CommandContext inside the CommandResult so that the Dispatcher can be used with multiple Host+Script combinations.
     */
    class ContextedResult implements CommandResult {
        private CommandContext context;

        public ContextedResult(CommandContext context){
            this.context = context;

        }
        @Override
        public void next(Cmd command,String output) {
            observers.forEach(o->o.onNext(command,output));
            dispatch(command,command.getNext(),output,this.context,this);
        }

        @Override
        public void skip(Cmd command,String output) {
            observers.forEach(o->o.onSkip(command,output));
            dispatch(command,command.getSkip(),"",this.context,this);
        }

        @Override
        public void update(Cmd command, String output) {

            observers.forEach(o->o.onUpdate(command,output));
            try {
                if(activeCommands.containsKey(command)){ // trying to run a missing command
                    activeCommands.get(command).update(output);
                }

            }catch(Exception e){
                logger.error("{} Error: {}",command,e.getMessage(),e);
            }
        }
    }

    class ScriptResult {
        private Script script;
        private ContextedResult result;
        public ScriptResult(Script script,ContextedResult result){
            this.script = script;
            this.result = result;
        }
        public Script getScript(){return script;}
        public ContextedResult getResult(){return result;}
    }
    class ActiveCommandInfo {
        private String name;
        private ScheduledFuture<?> scheduledFuture;
        private RunWatchers runWatchers;
        private CommandContext context;
        private long startTime;
        private long lastUpdate;

        public ActiveCommandInfo(String name,RunWatchers runWatchers,CommandContext context){
            this.name =name;
            this.runWatchers = runWatchers;
            this.context = context;
            this.startTime = System.currentTimeMillis();
            this.lastUpdate = this.startTime;
        }
        public CommandContext getContext(){return context;}
        public ScheduledFuture<?> getScheduledFuture() {return scheduledFuture;}
        public void setScheduledFuture(ScheduledFuture<?> scheduledFuture){this.scheduledFuture = scheduledFuture;}
        public RunWatchers getRunWatchers(){return runWatchers;}
        public void setRunWatchers(RunWatchers runWatchers){
            this.runWatchers = runWatchers;
        }
        public void update(String update){
            lastUpdate = System.currentTimeMillis();
            if(runWatchers!=null){
                runWatchers.add(update);
            }
        }
        public long getLastUpdate(){ return lastUpdate; }
        public long getStartTime(){ return startTime; }
    }
    class RunWatchers implements Runnable {
        String name;
        BlockingQueue<String> queue;
        List<Cmd> watchers;
        CommandContext context;
        CommandResult result;
        boolean stop = false;
        String stopUid = "stop-"+System.currentTimeMillis();
        public RunWatchers(String name,List<Cmd> watchers,CommandContext context,CommandResult result,BlockingQueue<String> queue){
            this.name = name;
            this.watchers = watchers;
            this.context = context;
            this.result = result;
            this.queue = queue;
        }

        @Override
        public String toString(){return name+":["+watchers.size()+"]";}
        public void add(String input){
            try {
                queue.add(input);
            }catch(NullPointerException e){
                logger.catching(e);
            }
        }
        public void stop(){
            this.stop = true;
            queue.add(stopUid);
        }
        @Override
        public void run() {
            logger.debug("{} watcher count = {}",this.name,watchers.size());

            String line = null;
            try {
                while( (line=queue.take())!=stopUid && !stop){
                    if(!stop) {
                        for (Cmd watcher : watchers) {
                            try {
                                watcher.run(line, context, result);
                            }catch(Exception e){
                                logger.warn("Exception from watcher {}",watcher,e);
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                logger.catching(e);
                e.printStackTrace();
            } finally {
                logger.debug("{} finished watching with line={}",this.name,line);
            }
        }
    }
    class RunCommand implements Runnable {
        Cmd command;
        String input;
        CommandContext context;
        CommandResult result;
        public RunCommand(Cmd command, String input, CommandContext context, CommandResult result){
            this.command = command;
            this.input = input;
            this.context = context;
            this.result = result;
        }

        @Override
        public void run() {
            try {
                activeThreads.put(command, Thread.currentThread());
                logger.trace("CommandDispatch.run {}:{}",context.getSession().getHostName(), command);
                context.getProfiler().start(command.getUid()+":"+command);

                command.run(input, context, result);
            }catch(Exception e){
                logger.error("Exception while running {}@{}. Aborting",command,context.getSession().getHostName(),e);
                context.abort();

            }
        }
    }

    private Map<Cmd,ActiveCommandInfo> activeCommands;
    private Map<Cmd,Thread> activeThreads;
    private HashSet<Integer> pastCommands;

    private ConcurrentHashMap<Cmd,ScriptResult> script2Result;

    private List<Observer> observers;

    private ThreadPoolExecutor executor;
    private ScheduledThreadPoolExecutor scheduler;
    private ScheduledFuture<?> nannyFuture;
    private boolean isStopped;

    @Override public String toString(){return "CD";}

    public CommandDispatcher(ThreadPoolExecutor executor,ScheduledThreadPoolExecutor scheduler){
        this.executor = executor;
        this.scheduler = scheduler;

        this.activeCommands = new ConcurrentHashMap<>();
        this.activeThreads = new ConcurrentHashMap<>();
        this.pastCommands = new HashSet<>();
        this.script2Result = new ConcurrentHashMap<>();

        this.observers = new LinkedList<>();

        this.isStopped = true;
    }

    private Cmd removeActive(Cmd command){
        ActiveCommandInfo activeCommandInfo = activeCommands.remove(command);
        if(activeCommandInfo.getRunWatchers()!=null) {
            try {
                activeCommandInfo.getRunWatchers().stop();
            }catch (NullPointerException e){
                logger.info("NPE add null after {}",command);
            }
        }
        if(activeCommandInfo.getScheduledFuture()!=null && !activeCommandInfo.getScheduledFuture().isDone()){
            activeCommandInfo.getScheduledFuture().cancel(true);
        }
        activeThreads.remove(command);
        return activeCommandInfo.getRunWatchers()!=null? command : null;
    }


    public void addObserver(Observer observer){
        observers.add(observer);
    }
    public void removeObserver(Observer observer){
        observers.remove(observer);
    }


    public void addScript(Script script,CommandContext context){
        logger.entry(script.getName(),context.getSession().getHostName());
        script = (Script)script.deepCopy();

        //Cmd.InvokeCmd can change the tail and cause this to close profiler too soon
        //TODO make this thread safe
        ScriptResult previous = script2Result.put(
                script.getHead(),
                new ScriptResult(
                        script,
                        new ContextedResult(context)
                )
        );
        if(previous!=null){
            logger.error("already have getScript.tail={} mapped to {}@{}",script.getTail().getUid(),script.getName(),context.getSession().getHostName());
        }
        logger.exit();
    }
    //TODO this should no longer be needed now that script2Head stores the head cmd not tail
    public void onTailMod(Cmd previousTail,Cmd nextTail){
        //TODO make thread safe
        ScriptResult result = script2Result.get(previousTail);
        if(result!=null){
            logger.trace("changing {} tail from {} to {}",result.getScript().getName(),previousTail,nextTail);
            script2Result.remove(previousTail);
            script2Result.put(nextTail,result);
        }
    }
    public void start(){ //start all the scripts attached to this dispatcher
        logger.trace("CD.start");
        if(!isStopped){//don't try to start an already started dispatcher
            logger.trace("CD don't start, isStopped={}",isStopped);
            return;
        }
        isStopped = false;
        observers.forEach(c->c.onStart());
        logger.info("starting {} scripts",script2Result.size());
        if(!script2Result.isEmpty()){
            BiConsumer<Cmd,Long> checkUpdate = (command,timestamp)->{
                logger.trace("nanny checking {}",command);
                if(activeCommands.containsKey(command) && command instanceof Cmd.Sh){
                    long lastUpdate = activeCommands.get(command).getLastUpdate();
                    if(timestamp - lastUpdate > THRESHOLD){
                        logger.warn("{} idle for {}",
                                command,
                                String.format("%5.2f",(1.0*timestamp-lastUpdate)/1_000)
                        );
                    }
                }
            };
            if(nannyFuture == null) {
                nannyFuture = scheduler.scheduleAtFixedRate(() -> {
                    long timestamp = System.currentTimeMillis();
                    try {
                        for (Cmd c : activeCommands.keySet()) {
                            checkUpdate.accept(c, timestamp);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }, THRESHOLD, THRESHOLD, TimeUnit.SECONDS);
            }
            for(ScriptResult scriptResult : script2Result.values()){
                Script script = scriptResult.getScript();
                ContextedResult result = scriptResult.getResult();
                logger.info("starting {}@{}",script.getName(),result.context.getSession().getHostName());
                dispatch(null,script,"",result.context,result);
            }
        }else{
            checkActiveCount();
        }

    }
    public void schedule(Cmd command,Runnable runnable,long delay){
        schedule(command,runnable,delay,TimeUnit.MILLISECONDS);
    }
    public void schedule(Cmd command,Runnable runnable,long delay,TimeUnit timeUnit){
        ScheduledFuture<?> future = scheduler.schedule(runnable,delay,timeUnit);
        if(activeCommands.containsKey(command)){
            activeCommands.get(command).setScheduledFuture(future);
        }
    }
    public void shutdown(){
        stop();
        //scheduler.shutdownNow();
    }
    public void stop(){
        logger.debug("CD.stop");
        isStopped=true;
        closeSessions();
        activeCommands.values().forEach((w)->{
            if(w.getRunWatchers()!=null){
                w.getRunWatchers().stop();
            }
            if(w.getScheduledFuture()!=null){
                w.getScheduledFuture().cancel(true);
            }

        });
        activeThreads.values().forEach(thread->{
            logger.info("interrupting {}",thread.getName());
            thread.interrupt();
        });
        if(nannyFuture!=null){
            boolean cancelledFuture= nannyFuture.cancel(true);
            nannyFuture = null;
        }
        observers.forEach(c -> c.onStop());
    }


    private void execute(Cmd command,String input,CommandContext context,CommandResult result){
        logger.trace(" execute {} with input=[{}]",command,input.length()>120?input.substring(0,120)+ AsciiArt.ELLIPSIS:input);
        if(isStopped){
            return;
        }
        if(!command.getWatchers().isEmpty()){
            WatcherResult watcherResult = new WatcherResult(context);
            BlockingQueue<String> queue = new LinkedBlockingQueue<>();

            RunWatchers watcherRunnable = new RunWatchers("watch:"+command,command.getWatchers(),context,watcherResult,queue);
            activeCommands.get(command).setRunWatchers(watcherRunnable);
            logger.trace("queueing {} watchers for {}",command.getWatchers().size(),command);
            executor.execute(watcherRunnable);
        }

        executor.execute(new RunCommand(command,input,context,result));

    }
    public void dispatch(Cmd previousCommand, Cmd nextCommand,String input, CommandContext context, CommandResult result){
        if(isStopped){
            return;
        }
        if(previousCommand!=null){
            previousCommand.setOutput(input);
            //TODO BUG if previousCommand is last command in script but nextCommand references a repeat-until then this pre-maturely ends the script
            if(nextCommand==null){//nextCommand is only null when end of watcher or end of script
                checkScriptDone(previousCommand,context);
            }
        }

        if(nextCommand!=null){
            observers.forEach(c->c.onStart(nextCommand));

            ActiveCommandInfo previous = activeCommands.put(nextCommand,new ActiveCommandInfo(nextCommand.getUid()+" "+nextCommand.toString(),null,context));

            if(previous!=null){
                logger.warn("adding {} {} previous = {}",nextCommand.hashCode(),nextCommand,previous.name);
                activeCommands.entrySet().forEach((entry)->{
                    Cmd k =entry.getKey();
                    ActiveCommandInfo aci =entry.getValue();
                    if(aci.equals(previous)){
                        logger.warn("found match @ {} {} {}",k.hashCode(),k.getClass().getName(),k);
                    }
                });
            }

            pastCommands.add(nextCommand.getUid());
            context.getProfiler().start("waiting in run queue");
            if(nextCommand.getPrevious()!=null && nextCommand.getPrevious().getOutput()!=null){
                input = nextCommand.getPrevious().getOutput();
            }
            execute(nextCommand,input,context,result);
        }else{
            logger.trace("no next command from {}",previousCommand);
        }
        //remove after add to ensure activeCommands is only empty when there isn't a nextCommand
        if(previousCommand!=null) {
            Cmd removed = removeActive(previousCommand);
        }
        checkActiveCount();

    }
    private void checkScriptDone(Cmd command, CommandContext context){
        if(script2Result.containsKey(command.getHead())){//we finished a getScript
            context.getProfiler().stop();
            context.getProfiler().setLogger(context.getRunLogger());
            context.getProfiler().log();
            if(context.getRunLogger().isInfoEnabled()){
                context.getRunLogger().info("{}@{} closing script state:\n{}",
                        script2Result.get(command).getScript().getName(),
                        context.getSession().getHostName(),
                        context.getState().getScriptState());
            }
            observers.forEach(c->c.onStop(command));
            script2Result.get(command).getResult().context.getSession().close();
            script2Result.remove(command);
        }
    }
    private void checkActiveCount(){
        if(activeCommands.size()==0 && !isStopped){
            logger.info("activeCommands.size = {}",activeCommands.size());
            executor.execute(() -> {
                closeSessions();
                observers.forEach(o->o.onStop());
            });
        }
    }
    public void closeSessions(){
        //TODO make thread safe
        logger.debug("{}.closeSessions",this);
        script2Result.values().forEach(scriptResult -> {
            logger.debug("closing connection to {}",scriptResult.getResult().context.getSession().getHostName());
            scriptResult.getResult().context.getSession().close();
            if(scriptResult.getResult().context.getRunLogger().isInfoEnabled()){
                scriptResult.getResult().context.getRunLogger().info("{}@{} closing state:\n{}",
                        scriptResult.getScript().getName(),
                        scriptResult.getResult().context.getSession().getHostName(),
                        scriptResult.getResult().context.getState());
            }
        });
        script2Result.clear();
        isStopped=true;
    }
    public boolean isActive(){return activeCommands.size()>0;}
    public int getActiveCount(){return activeCommands.size();}
}
