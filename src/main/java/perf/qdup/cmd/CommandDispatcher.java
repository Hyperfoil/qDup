package perf.qdup.cmd;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import perf.qdup.cmd.impl.Abort;
import perf.qdup.cmd.impl.Done;
import perf.qdup.cmd.impl.Sh;
import perf.qdup.stream.MultiStream;
import perf.yaup.AsciiArt;
import perf.yaup.json.Json;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 *
 * Created by wreicher
 *
 * CommandDispatch.start put the first command into the executor
 * Executor calls RunCommand.run
 *  RunCommand.run calls Cmd.run(...)
 *    Cmd.run() calls SshSession.sh(...)
 * SemaphoreStream.write checks checkForPrompt
 *   calls SshSession.run()
 *     calls CommandResult.next(output)
 *
 *
 * Need strong guarantees that all Script / Cmd / Dispatch Observers finish before closing the session / killing the treads with Stop
 *
 *
 */
public class CommandDispatcher {

    final static Thread.UncaughtExceptionHandler DefaultUncaughtExceptionHandler = (thread, throwable) ->{
        System.out.println("UNCAUGHT:"+thread.getName()+" "+throwable.getMessage());
        throwable.printStackTrace(System.out);
    };

    final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

    final static long THRESHOLD = 30_000; //30s

    public interface ScriptObserver {
        public default void onStart(Cmd command,Context context){}
        public default void onStop(Cmd command,Context context){}
    }

    public interface CommandObserver {
        public default void onStart(Cmd command){}
        public default void onStop(Cmd command){}
        public default void onNext(Cmd command,String output){}
        public default void onSkip(Cmd command,String output){}
        public default void onUpdate(Cmd command,String output){}
    }
    public interface DispatchObserver {
        public default void preStart(){}
        public default void postStop(){}
    }

    //Result called by watchers which will just invoke the next watcher on the current thread
    class WatcherResult implements CommandResult {
        private Context context;
        public WatcherResult(Context context){ this.context = context; }

        @Override
        public void next(Cmd command, String output) {
            //commandObservers.forEach(o->o.onNext(command,output));
            if(command.getNext()!=null){
                logger.trace("{}:{} using WatcherResult to invoke next={}",command.getUid(),command,command.getNext());
                command.getNext().doRun(output,this.context,this);
            }
        }

        @Override
        public void skip(Cmd command, String output) {
            //commandObservers.forEach(o->o.onSkip(command,output));
            if(command.getSkip()!=null){
                logger.trace("{} using WatcherResult to invoke skip={}",command,command.getSkip());
                command.getSkip().doRun(output,this.context,this);
            }
        }

        @Override
        public void update(Cmd command, String output) {
            logger.warn("{} trying to update using a WatcherResult",command);
        }
    }
    /**
     * Stores the Context inside the CommandResult with the base Cmd (Script) so that the Dispatcher can be used WITH multiple Host+Script combinations.
     */
    class ScriptContext implements CommandResult {
        private final Context context;
        private final Cmd command;

        public ScriptContext(Cmd command, Context context){
            this.command = command;
            this.context = context;
        }

        public Context getContext(){return context;}
        public Cmd getCommand(){return command;}

        private void logCmdOutput(Cmd command,String output){
            if(command!=null && output!=null){
                command.setOutput(output);
                String cmdOutput="";
                String withOutput="";
                if(!command.isSilent()) {
                    //include output
                    cmdOutput = "\n" + output;
                }
                if(!command.getWith().isEmpty()){
                    StringBuilder sb = new StringBuilder();
                    command.getWith().forEach((k,v)->{
                        sb.append(String.format("  %s=%s",k,v));
                    });
                    withOutput="\n"+sb.toString();
                }
                if(command instanceof Sh){
                        context.getRunLogger().info("{}:{}:({}){}{}{}",command.getHead(),context.getSession().getHost().toString(),command.getUid(), command,withOutput,cmdOutput);
                }else{
                    if(command.isSilent()){
                        context.getRunLogger().info("{}:{}:({}){}{}",command.getHead(),context.getSession().getHost().toString(),command.getUid(),command,withOutput);
                    }else if (command.getPrevious()==null || !output.equals(command.getPrevious().getOutput())){
                        //include output
                        context.getRunLogger().info("{}:{}:({}){}{}{}",command.getHead(),context.getSession().getHost().toString(),command.getUid(), command,withOutput,output);
                    }else{
                        //no output
                        context.getRunLogger().info("{}:{}:({}){}{}",command.getHead(),context.getSession().getHost().toString(),command.getUid(),command,withOutput);
                    }
                }
            }
        }

        @Override
        public void next(Cmd command,String output) {
            if(command!=null){
                command.setOutput(output);
            }
            //get off the calling thread
            logger.trace("queueing run-next={}\n  host={}\n  command={} output={}",
                    command.getNext(),
                    context.getSession().getHost(),
                    command,
                    output
                    );
            executor.submit(()->{
                logCmdOutput(command,output);
                commandObservers.forEach(o->o.onNext(command,output));
                dispatch(command,command.getNext(),output,this.context,this);
            });
        }

        @Override
        public void skip(Cmd command,String output) {
            //get off the calling thread
            logger.trace("queueing run-skip={}\n  host={}\n  command={}",
                    command.getSkip(),
                    context.getSession().getHost(),
                    command
                    );
            executor.submit(()-> {
                commandObservers.forEach(o -> o.onSkip(command, output));
                logCmdOutput(command, output);
                dispatch(command, command.getSkip(), "", this.context, this);
            });
        }

        @Override
        public void update(Cmd command, String output) {

            commandObservers.forEach(o->o.onUpdate(command,output));
            try {
                if(activeCommands.containsKey(command)){ // trying to run a missing command
                    activeCommands.get(command).update(output);
                }

            }catch(Exception e){
                logger.error("{}@{}:{} Error: {}",command.getHead(),context.getSession().getHost().toString(),command,e.getMessage(),e);
            }
        }
    }

    class ActiveCommandInfo {
        private String name;
        private ScheduledFuture<?> scheduledFuture;
        private RunWatchers runWatchers;
        private Context context;
        private long startTime;
        private long lastUpdate;

        public ActiveCommandInfo(String name,RunWatchers runWatchers,Context context){
            this.name =name;
            this.runWatchers = runWatchers;
            this.context = context;
            this.startTime = System.currentTimeMillis();
            this.lastUpdate = this.startTime;
        }
        public Context getContext(){return context;}
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
        final String name;
        final BlockingQueue<String> queue;
        final List<Cmd> watchers;
        final Context context;
        final CommandResult result;
        final String stopUid = "stop-"+System.currentTimeMillis();

        boolean stop = false;

        public RunWatchers(String name, List<Cmd> watchers, Context context, CommandResult result, BlockingQueue<String> queue){
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
                    //logger.info("{} line={}",this.name,line);
                    //logger.info(MultiStream.printByteCharacters(line.getBytes(),0,line.getBytes().length));
                    if(!stop) {
                        for (Cmd watcher : watchers) {
                            try {
                                watcher.doRun(line, context, result);
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
                logger.debug("{} finished watching WITH line={}",this.name,line);
            }
        }
    }
    class RunTimer implements Runnable {
        Cmd toRun;
        Cmd observing;
        Context context;
        CommandResult result;
        long timeout;
        public RunTimer(long timeout,Cmd toRun,Cmd observing,Context context,CommandResult result){
            this.timeout = timeout;
            this.toRun = toRun;
            this.observing = observing;
            this.context = context;
            this.result = result;
        }
        @Override
        public void run(){
            if(activeCommands.containsKey(observing)){
                toRun.doRun(""+timeout,context,result);
            }else{

            }
        }

    }
    class RunCommand implements Runnable {
        Cmd command;
        String input;
        Context context;
        CommandResult result;
        public RunCommand(Cmd command, String input, Context context, CommandResult result){
            this.command = command;
            this.input = input;
            this.context = context;
            this.result = result;
        }

        @Override
        public void run() {
            try {
                activeThreads.put(command, Thread.currentThread());
                logger.trace("run {}:{}",context.getSession().getHostName(), command);
                context.getProfiler().start(command.toString());

                command.doRun(input, context, result);
                //thread is no longer active when method exits even if command is active
                activeThreads.remove(command);


            }catch(Exception e){
                logger.error("Exception while running {}@{}. Aborting",command,context.getSession().getHostName(),e);
                context.abort();

            }
        }
    }

    private final AtomicInteger activeCommandCount;  // because ConcurrentHashMap.size is not atomic with put / remove operations
    private final Map<Cmd,ActiveCommandInfo> activeCommands;
    private final Map<Cmd,Thread> activeThreads;
    private final HashSet<Integer> pastCommands;

    private final ConcurrentHashMap<Cmd,ScriptContext> loadedScripts;

    private final List<CommandObserver> commandObservers;
    private final List<ScriptObserver> scriptObservers;
    private final List<DispatchObserver> dispatchObservers;

    private final ThreadPoolExecutor executor;
    private final ScheduledThreadPoolExecutor scheduler;
    private ScheduledFuture<?> nannyFuture;
    private final AtomicBoolean isRunning;
    private final BiConsumer<Cmd,Long> nannyTask;

    private final boolean autoClose;

    public Json getActiveJson(){
        Json rtrn = new Json();

        activeCommands.forEach( (cmd,activeCommandInfo) -> {
            Json entry = new Json();
            entry.set("name",cmd.toString());
            entry.set("host",activeCommandInfo.getContext().getSession().getHost().toString());
            entry.set("uid",cmd.getUid());
            entry.set("script",cmd.getHead().getUid()+":"+cmd.getHead().toString());
            if(cmd instanceof Sh){
                entry.set("host",activeCommandInfo.getContext().getSession().getHost().toString());
                String output = activeCommandInfo.getContext().getSession().peekOutput();
                entry.set("output",output);
            }
            entry.set("startTime",activeCommandInfo.getStartTime());
            entry.set("runTime",(System.currentTimeMillis()-activeCommandInfo.getStartTime()));
            entry.set("lastUpdate",activeCommandInfo.getLastUpdate());
            entry.set("idleTime",(System.currentTimeMillis()-activeCommandInfo.getLastUpdate()));

            rtrn.add(entry);
        });
        return rtrn;
    }

    @Override public String toString(){return "CD";}

    //TODO need to pass boolean to know we own the ThreadPools and need to close them in this.shutdown
    public CommandDispatcher(){
        this(
                new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors()/2, Runtime.getRuntime().availableProcessors(), 30, TimeUnit.MINUTES, new LinkedBlockingQueue<>(), new ThreadFactory() {
                    AtomicInteger count = new AtomicInteger(0);
                    @Override
                    public Thread newThread(Runnable runnable) {
                        Thread rtrn = new Thread(runnable,"execute-"+count.getAndAdd(1));
                        rtrn.setUncaughtExceptionHandler(DefaultUncaughtExceptionHandler);
                        return rtrn;
                    }
                }),
                new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors()/2,new ThreadFactory() {
                    AtomicInteger count = new AtomicInteger(0);
                    @Override
                    public Thread newThread(Runnable runnable) {
                        return new Thread(runnable,"schedule-"+count.getAndAdd(1));
                    }
                }),
                true
        );
    }
    public CommandDispatcher(ThreadPoolExecutor executor,ScheduledThreadPoolExecutor scheduler) {
        this(executor,scheduler,false);
    }
    private CommandDispatcher(ThreadPoolExecutor executor,ScheduledThreadPoolExecutor scheduler,boolean autoClose){
        this.executor = executor;
        this.scheduler = scheduler;
        this.autoClose=autoClose;

        this.activeCommandCount = new AtomicInteger(0);
        this.activeCommands = new ConcurrentHashMap<>();
        this.activeThreads = new ConcurrentHashMap<>();
        this.pastCommands = new HashSet<>();
        this.loadedScripts = new ConcurrentHashMap<>();

        this.commandObservers = new LinkedList<>();
        this.scriptObservers = new LinkedList<>();
        this.dispatchObservers = new LinkedList<>();

        this.nannyFuture = null;
        this.nannyTask = (command,timestamp)->{
            if(activeCommands.containsKey(command) /*&& command instanceof Sh*/){
                logger.trace("Nanny checking:\n  host={}\n  command={}",
                        activeCommands.get(command).getContext().getSession().getHost(),
                        command);

                long lastUpdate = activeCommands.get(command).getLastUpdate();
                if(timestamp - lastUpdate > THRESHOLD ){
                    if(command instanceof Sh){
                        String output = activeCommands.get(command).getContext().getSession().peekOutput();
                        if( output.endsWith("? [y]es, [n]o, [A]ll, [N]one, [r]ename:") || //unzip
                                (output.endsWith("'?") && output.contains("rm: remove regular file '")) || //root rm
                                output.endsWith("Is this ok [y/N]: ")
                                ){
                            logger.warn("Nanny found prompt for command={}\n  host={}\n  output={}",
                                    command,
                                    activeCommands.get(command).getContext().getSession().getHost(),
                                    output);
                            //TODO how to handle a prompt blocking a command from going back to PS1
                        }
                    }
                    if(!command.isSilent()) {
                        logger.warn("Nanny found idle command={}\n  host={}\n  script={}\n  idle={}",
                                command,
                                activeCommands.get(command).getContext().getSession().getHostName(),
                                command.getHead(),
                                String.format("%5.2f", (1.0 * timestamp - lastUpdate) / 1_000));
                    }
                }
            }
        };
        this.isRunning = new AtomicBoolean(false);
    }

    private int removeActive(Cmd command){
        logger.trace("removeActive("+command+") size="+activeCommandCount.get());
        ActiveCommandInfo activeCommandInfo = activeCommands.remove(command);
        int rtrn = activeCommandCount.decrementAndGet();
        logger.trace("removeActive("+command+") post.size="+activeCommandCount.get());
        if(activeCommandInfo.getRunWatchers()!=null) {
            try {
                activeCommandInfo.getRunWatchers().stop();
            }catch (NullPointerException e){
                logger.error("NPE add null after {}",command);
            }
        }
        if(activeCommandInfo.getScheduledFuture()!=null && !activeCommandInfo.getScheduledFuture().isDone()){
            activeCommandInfo.getScheduledFuture().cancel(true);
        }

        return rtrn;
    }


    public void addScriptObserver(ScriptObserver observer){scriptObservers.add(observer);}
    public void removeScriptObserver(ScriptObserver observer){
        scriptObservers.remove(observer);
    }
    public void addCommandObserver(CommandObserver observer){
        commandObservers.add(observer);
    }
    public void removeCommandObserver(CommandObserver observer){
        commandObservers.remove(observer);
    }
    public void addDispatchObserver(DispatchObserver observer){dispatchObservers.add(observer);}
    public void removeDispatchObserver(DispatchObserver observer){dispatchObservers.remove(observer);}

    public ExecutorService getExecutor(){return executor;}

    public void addScript(Cmd script,Context context){
        logger.trace("add script {} to {}",script,context.getSession().getHostName());

        script = script.deepCopy();

        ScriptContext previous = loadedScripts.put(
                script.getHead(),
                new ScriptContext(
                    script,
                    context
                )
        );
        if(previous!=null){
            logger.error("already have getScript.tail={} mapped to {}@{}",script.getTail().getUid(),script,context.getSession().getHostName());
        }
    }
    public String debug(){
        StringBuilder sb = new StringBuilder();
        loadedScripts.forEach(((cmd, scriptResult) -> {
            sb.append(scriptResult.getContext().getSession().getHost()+" = "+cmd.getUid()+" "+cmd.toString());
            sb.append(System.lineSeparator());
        }));
        return sb.toString();
    }

    public void start(){ //start all the scripts attached to this dispatcher
        if(isRunning.compareAndSet(false,true)){
            dispatchObservers.forEach(c->c.preStart());
            logger.info("starting {} scripts", loadedScripts.size());
            if(!loadedScripts.isEmpty()){
                if(nannyFuture == null) {
                    logger.info("starting nanny");
                    nannyFuture = scheduler.scheduleAtFixedRate(() -> {
                        long timestamp = System.currentTimeMillis();
                        try {
                            for (Cmd c : activeCommands.keySet()) {
                                nannyTask.accept(c, timestamp);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }, THRESHOLD, THRESHOLD, TimeUnit.MILLISECONDS);
                }
                for(ScriptContext scriptContext : loadedScripts.values()){
                    Cmd script = scriptContext.getCommand();

                    scriptObservers.forEach(observer -> observer.onStart(script, scriptContext.getContext()));

                    logger.info("starting\n  host={}\n  script={}",
                            scriptContext.getContext().getSession().getHostName(),
                            script);
                    dispatch(null,script,"", scriptContext.getContext(), scriptContext);
                }
            }else{
                checkActiveCount();
            }


        }else{
            logger.warn("cannot start an already active Dispatcher");
        }
        logger.trace("start");


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
    private void clearActiveCommands(){
        closeSshSessions();
        activeCommands.forEach((cmd,activeCommand)->{
            if (!commandObservers.isEmpty()){
                for(CommandObserver c : commandObservers){
                    c.onStop(cmd);
                }
            }
            if( activeCommand.getRunWatchers()!=null ){
                activeCommand.getRunWatchers().stop();
            }
            if( activeCommand.getScheduledFuture()!=null ){
                activeCommand.getScheduledFuture().cancel(true);
            }
        });
        //TODO stop activeThreads?
        activeCommands.clear();
        activeCommandCount.set(0);
        //dispatchObservers.forEach(c -> c.postStop());
    }
    public void shutdown(){
        stop();
        if(autoClose){
            executor.shutdown();
            scheduler.shutdown();
        }
    }
    public void stop(){
        if(isRunning.compareAndSet(true,false)){
            logger.debug("stop");

            activeThreads.keySet().forEach(command->{
                Thread thread = activeThreads.get(command);
                //TODO this feels like a hack around commands that should be setting state on context and calling next / skip but dispatcher / context know the stage is ending
                if( !(command instanceof Abort) && !(command instanceof Done) ){
                    logger.info("interrupting {} running {}\n{}",thread.getName(),command,Arrays.asList(thread.getStackTrace())
                            .stream().map(stackTraceElement -> {
                                return stackTraceElement.getClassName()+"."+stackTraceElement.getMethodName()+"():"+stackTraceElement.getLineNumber();
                            }).collect(Collectors.joining("\n")));
                    thread.interrupt();
                }
            });
            activeThreads.clear();//ignore the fact we didn't interrupt the Abort thread, it will finish with the current execution
            if(nannyFuture!=null){
                boolean cancelledFuture= nannyFuture.cancel(true);
                nannyFuture = null;
            }

            //needs to occur before we notify observers because observers can queue next stage
            clearActiveCommands();//also closes the sshSessions

            dispatchObservers.forEach(c -> c.postStop());


        }

    }

    private void execute(Cmd command, String input, Context context, CommandResult result){
        logger.trace("execute command={}\n  host={}\n  input=[{}]",
                command,
                context.getSession().getHost(),
                input.length()>80?input.substring(0,80)+AsciiArt.ELLIPSIS:input);
        if(!isRunning()){
            return;
        }
        if(!command.getWatchers().isEmpty()){
            WatcherResult watcherResult = new WatcherResult(context);
            BlockingQueue<String> queue = new LinkedBlockingQueue<>();

            RunWatchers watcherRunnable = new RunWatchers("watch:"+command.getUid(),command.getWatchers(),context,watcherResult,queue);
            activeCommands.get(command).setRunWatchers(watcherRunnable);
            logger.trace("queueing {} watchers\n  host={}\n  command={}",command.getWatchers().size(),
                    context.getSession().getHost(),
                    command);
            executor.execute(watcherRunnable);
        }
        if(command.hasTimers()){
            WatcherResult watcherResult = new WatcherResult(context);

            for(long timeout: command.getTimeouts()){
               List<Cmd> timers = command.getTimers(timeout);
               for(Cmd timer : timers){
                   RunTimer runTimer = new RunTimer(timeout,timer,command,context,watcherResult);
                   scheduler.schedule(runTimer,timeout,TimeUnit.MILLISECONDS);
               }
           }
        }

        executor.execute(new RunCommand(command,input,context,result));

    }

    public void dispatch(Cmd previousCommand, Cmd nextCommand, String input, Context context, CommandResult result){
        logger.debug("dispatch command={}\n  host={}\n  previous={}\n  script={}",
                nextCommand,
                context.getSession().getHost(),
                previousCommand,
                nextCommand!=null?nextCommand.getHead():"--"
                );
        if(!isRunning()){
            return;
        }
        if(previousCommand!=null){
            //previousCommand.setOutput(input); why is this done here? should be done in CommandResult
            for(CommandObserver observer : commandObservers){
                observer.onStop(previousCommand);
            }
            if(nextCommand==null){//nextCommand is only null when end of watcher or end of script
                checkScriptDone(previousCommand,context);
            }
        }
        int activeCount= activeCommandCount.get();
        if(nextCommand!=null){
            commandObservers.forEach(c->c.onStart(nextCommand));

            ActiveCommandInfo previous = activeCommands.put(nextCommand,new ActiveCommandInfo(nextCommand.getUid()+" "+nextCommand.toString(),null,context));
            activeCount = activeCommandCount.incrementAndGet();
            logger.trace("addActive("+nextCommand+") size="+activeCommandCount.get());

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

            logger.debug("no next\n  host={}\n  command={}",
                    context.getSession().getHost(),
                    previousCommand);

        }
        //remove after add to ensure activeCommands is only empty when there isn't a nextCommand
        if(previousCommand!=null) {
            activeCount = removeActive(previousCommand);
        }
        //activeCount can be modified by a command too :(
        if(activeCommandCount.get()==0) {
            checkActiveCount();
        }
    }
    private boolean checkScriptDone(Cmd command, Context context){
        boolean rtrn = false;
        if(loadedScripts.containsKey(command.getHead())){//we finished a script
            context.getProfiler().stop();
            if(context.getRunLogger().isInfoEnabled()){
                context.getRunLogger().info("Closing script state:\n  host={}\n  script={}\n{}",
                        context.getSession().getHostName(),
                        loadedScripts.get(command.getHead()).getCommand(),
                        context.getState().tree());
            }
            rtrn = true;
                scriptObservers.forEach(observer -> observer.onStop(command,context));
                loadedScripts.get(command.getHead()).getContext().getSession().close();
                loadedScripts.remove(command.getHead());
        }
        return rtrn;
    }
    private void checkActiveCount(){
        logger.trace("checkActiveCount = "+activeCommandCount.get());
        if(activeCommandCount.get()==0 && isRunning.compareAndSet(true,false)){
            logger.debug("activeCommands.count = {}",activeCommandCount.get());
            executor.execute(() -> {
                closeSshSessions();
                dispatchObservers.forEach(o->o.postStop());
            });
        }
    }
    private void closeSshSessions(){
        if(isRunning()){
            logger.warn("cannot closeSshSessions on a running Dispatcher");
        }else{
            logger.debug("{}.closeSshSessions",this);
            loadedScripts.values().forEach(scriptResult -> {
                logger.debug("closing connection to {}",scriptResult.getContext().getSession().getHostName());
                //don't wait will force running commands (holding shell lock) to be closed
                scriptResult.getContext().getSession().close(false);
            });
            loadedScripts.clear();
        }
    }
    public boolean isRunning(){return isRunning.get();}
    public int getActiveCount(){return activeCommandCount.get();}
}
