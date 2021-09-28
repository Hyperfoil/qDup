package io.hyperfoil.tools.qdup.cmd;

import io.hyperfoil.tools.qdup.SshSession;
import io.hyperfoil.tools.qdup.cmd.impl.RepeatUntilSignal;
import io.hyperfoil.tools.qdup.cmd.impl.Sh;
import io.hyperfoil.tools.qdup.cmd.impl.WaitFor;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import io.hyperfoil.tools.yaup.AsciiArt;
import io.hyperfoil.tools.yaup.json.Json;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 *
 * Created by wreicher
 * ScriptContext tracks the current Cmd that it is executing
*  Dispatcher tracks
 *
 * CommandDispatch.start put the first command into the executor
 * Executor calls RunCommand.run
 *  RunCommand.run calls Cmd.run(...)
 *    Cmd.run() calls SshSession.sh(...)
 * SemaphoreStream.write checks checkForPrompt
 *   calls SshSession.run()
 *     calls CommandResult.next(output)
 *
 * Need strong guarantees that all Script / Cmd / Dispatch Observers finish before closing the session / killing the treads with Stop
 *
 */
public class Dispatcher {

    final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

    final static Thread.UncaughtExceptionHandler DefaultUncaughtExceptionHandler = (thread, throwable) ->{
        StringBuffer sb = new StringBuffer();
        sb.append(throwable.getMessage());
        Arrays.asList(throwable.getStackTrace()).forEach(ste->{
            sb.append("\n  "+ste);
        });
        System.err.println(sb.toString());
        logger.error("UNCAUGHT:"+thread.getName()+" "+throwable.getMessage(),throwable);
    };


    private static final String CLOSE_QUEUE = "CLOSE_QUEUE_OBSERVER_"+System.currentTimeMillis();
    private static final String QUEUE_OBSERVER = "QUEUE_OBSERVER";

    //Result called by watchers which will just invoke the next watcher on the current thread

    private final ConcurrentHashMap<Cmd,ScriptContext> scriptContexts;
    private final ConcurrentHashMap<String,ScriptContext> contextById;

    private final List<ScriptObserver> scriptObservers;
    private final List<DispatchObserver> dispatchObservers;
    private final List<ContextObserver> contextObservers;

    private final ThreadPoolExecutor executor;
    private final ScheduledThreadPoolExecutor scheduler;
    private ScheduledFuture<?> nannyFuture;
    private final AtomicBoolean isRunning;
    private final AtomicBoolean isStopping;
    private final Consumer<Long> nannyTask;

    private final boolean autoClose;

    private final ContextObserver observer = new ContextObserver() {

        @Override
        public void onUpdate(Context context, Cmd command, String output){
            if(hasContextObserver()){
                for(ContextObserver o : contextObservers){
                    o.onUpdate(context,command,output);
                }
            }
        }

        @Override
        public void preStart(Context context,Cmd command){
            if(hasContextObserver()){
                for(ContextObserver o : contextObservers){
                    o.preStart(context,command);
                }
            }
        }
        @Override
        public void preStop(Context context,Cmd command,String output){
            if(hasContextObserver()){
                for(ContextObserver o : contextObservers){
                    o.preStop(context,command,output);
                }
            }
        }
        @Override
        public void preNext(Context context, Cmd command, String output){
            if(hasContextObserver()){
                for(ContextObserver o : contextObservers){
                    o.preNext(context,command,output);
                }
            }
        }
        @Override
        public void preSkip(Context context, Cmd command, String output){
            if(hasContextObserver()){
                for(ContextObserver o : contextObservers){
                    o.preSkip(context,command,output);
                }
            }

        }
        @Override
        public void onDone(Context context){
            if(hasContextObserver()){
                for(ContextObserver o : contextObservers){
                    o.onDone(context);
                }
            }
            if(context instanceof ScriptContext){
                ScriptContext scriptContext = (ScriptContext)context;
                scriptContext.getTimer().stop(); //fix bug where last timer has stop = 0

                scriptContexts.remove(scriptContext.getRootCmd());
                scriptObservers.forEach(observer -> observer.onStop(scriptContext));
                context.close();
                //context.getSession().close(); //using close on context to only close base context
                checkActiveCount();
            }
        }
    };

    public Context getContext(String id){
        ScriptContext rtrn = contextById.containsKey(id) ? contextById.get(id) : null;

        return rtrn;
    }
    public Json getContexts(){
        Json rtrn = new Json();
        scriptContexts.values().forEach(context->{
            Json entry = new Json();
            entry.set("id", context.getContextId());
            entry.set("host", context.getHost().getSafeString());
            entry.set("script", context.getRootCmd().toString());
            entry.set("cmdUid", context.getRootCmd().getUid());

            rtrn.add(entry);
        });
        return rtrn;
    }
    public Json getActiveJson(){
        Json rtrn = new Json();

        scriptContexts.forEach( (rootCmd, context) -> {
            Json entry = new Json();
            Cmd currentCmd = context.getCurrentCmd();
            entry.set("name",Cmd.populateStateVariables(currentCmd.toString(),currentCmd,context));
            entry.set("host",context.getSession().getHost().getSafeString());
            entry.set("uid",currentCmd.getUid());
            entry.set("contextId",context.getContextId());
            entry.set("script",rootCmd.getUid()+":"+rootCmd.toString());
            if(currentCmd instanceof Sh){
                entry.set("input",currentCmd.getPrevious()!=null?currentCmd.getPrevious().getOutput():"");
                entry.set("output",context.getSession().peekOutput());

            }
            entry.set("startTime",context.getStartTime());
            entry.set("runTime",(System.currentTimeMillis()-context.getStartTime()));
            entry.set("lastUpdate",context.getUpdateTime());
            entry.set("idleTime",(System.currentTimeMillis()-context.getUpdateTime()));
            rtrn.add(entry);
        });
        return rtrn;
    }

    @Override public String toString(){return "CD";}

    public Dispatcher(){
        this(
                new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors(), 30, TimeUnit.MINUTES, new LinkedBlockingQueue<>(), new ThreadFactory() {
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
    public Dispatcher(ThreadPoolExecutor executor, ScheduledThreadPoolExecutor scheduler) {
        this(executor,scheduler,false);
    }
    private Dispatcher(ThreadPoolExecutor executor, ScheduledThreadPoolExecutor scheduler, boolean autoClose){
        this.executor = executor;
        this.scheduler = scheduler;
        this.autoClose=autoClose;

        this.scriptContexts = new ConcurrentHashMap<>();
        this.contextById = new ConcurrentHashMap<>();
        this.scriptObservers = new LinkedList<>();
        this.dispatchObservers = new LinkedList<>();
        this.contextObservers = new LinkedList<>();

        this.nannyFuture = null;
        this.nannyTask = (timestamp)->{
            AtomicInteger nonWaitingContexts = new AtomicInteger(0);
            scriptContexts.forEach((script, context)->{
                Cmd command = context.getCurrentCmd();
                long lastUpdate = context.getUpdateTime();
                logger.trace("Nanny checking:\n  host={}\n  command={}",
                        context.getSession().getHost(),
                        command);

                //checking if the command is waiting for a signal
                if(command instanceof WaitFor){

                }else {
                    Cmd target = command;
                    boolean isWaiting = false;

                    do {
                        if (target instanceof RepeatUntilSignal) {
                            boolean selfSignals = ((RepeatUntilSignal)target).isSelfSignaling();
                            isWaiting = !selfSignals;
                        }
                    } while (!isWaiting && target.hasParent() && (target = target.getParent()) != null);
                    if(!isWaiting){
                        nonWaitingContexts.incrementAndGet();
                    }else{
                    }
                }

                //check for idle sh

                if(command.hasIdleTimer() &&  timestamp - lastUpdate > command.getIdleTimer()){
                    if(command instanceof Sh){
                        String output = context.getSession().peekOutput();
                        boolean hasPrompt = output.contains(SshSession.PROMPT);
                        String parentName = null;
                        if (command.getParent() instanceof Script)
                            parentName = ((Script) (command).getParent()).getName();
                        if(!command.isSilent()){
                            logger.warn("{}Nanny found idle{}\n  command={}\n  host={}\n  contextId={} script={}\n  idle={}\n  lastLine={}"
                                    + (hasPrompt ? "\n  output includes qdup prompt, a background or child process may be running independent of the current command" : ""),
                                    context.isColorTerminal() ? AsciiArt.ANSI_RED : "",
                                    context.isColorTerminal() ? AsciiArt.ANSI_RESET : "",
                                    command,
                                    context.getSession().getHost().getHostName(),
                                    context.getContextId(),
                                    script + (parentName.equals(null)? "" : ":" + parentName),
                                    String.format("%5.2f", (1.0 * timestamp - lastUpdate) / 1_000),
                                    context.getSession().peekOutputTail());
                        }
                        //TODO do we check for common prompts in output?
                        if(output.contains(SshSession.PROMPT)){
                            //TODO do we assume the prompt means we missed the end of the command?
                        }
                    }
                }
            });
            if(nonWaitingContexts.get() == 0){
                if(!scriptContexts.isEmpty()){

                    if(logger.isTraceEnabled()){
                        logger.trace("ending phase with {} active idle waiting scripts\n{}",
                           scriptContexts.size(),
                           getActiveJson()
                        );
                    }else{
                        logger.info("ending phase with {} active idle waiting scripts\n{}",
                           scriptContexts.size(),
                           getActiveJson().toString(2)
                        );
                    }
                    ScriptContext first = scriptContexts.values().iterator().next();
                    first.done();//use context.done to also stop the waiters
                }
            }

        };
        this.isRunning = new AtomicBoolean(false);
        this.isStopping = new AtomicBoolean(false);
    }

    public ScheduledThreadPoolExecutor getScheduler(){return scheduler;}


    public void addContextObserver(ContextObserver observer){contextObservers.add(observer);}
    public void removeContextObserver(ContextObserver observer){contextObservers.remove(observer);}
    public boolean hasContextObserver(){return !contextObservers.isEmpty();}

    public void addScriptObserver(ScriptObserver observer){scriptObservers.add(observer);}
    public void removeScriptObserver(ScriptObserver observer){
        scriptObservers.remove(observer);
    }
    public void addDispatchObserver(DispatchObserver observer){dispatchObservers.add(observer);}
    public void removeDispatchObserver(DispatchObserver observer){dispatchObservers.remove(observer);}

    private ExecutorService getExecutor(){return executor;}
    public void submit(Runnable runnable){
        getExecutor().submit(runnable);
    }

    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> toCall) throws InterruptedException{
        return getExecutor().invokeAll(toCall);
    }
    public void addScriptContext(ScriptContext context){
       addScriptContext(context,true);
    }
    public void addScriptContext(ScriptContext context, boolean autoRun){
        logger.trace("add script {} to {}",context.getRootCmd(),context.getSession().getHost().getHostName());

        context.setObserver(observer);

        ScriptContext previous = scriptContexts.put(
                context.getRootCmd(),
                context
        );
        contextById.put(context.getContextId(),context);
        if(previous!=null){
            logger.error("already have getScript.tail={} mapped to {}@{}",context.getRootCmd().getTail().getUid(),context.getRootCmd(),context.getSession().getHost().getHostName());
        }
        if(isRunning.get()){
            ScriptContext contextResult = scriptContexts.get(context.getRootCmd());
            scriptObservers.forEach(observer -> observer.onStart(contextResult));
            if(autoRun) {
               logger.info("queueing\n  host={}\n  script={}",
                  contextResult.getSession().getHost().getHostName(),
                  context.getRootCmd());
               context.getTimer().start("waiting in run queue");
               getExecutor().submit(context);
            }
        }
    }
    public String debug(){
        StringBuilder sb = new StringBuilder();
        scriptContexts.forEach(((cmd, contextResult) -> {
            sb.append(contextResult.getSession().getHost()+" = "+cmd.getUid()+" "+cmd.toString());
            sb.append(System.lineSeparator());
        }));
        return sb.toString();
    }

    public void start(){ //start all the scripts attached to this dispatcher
        if(isRunning.compareAndSet(false,true)){
            isStopping.set(false);
            dispatchObservers.forEach(c->c.preStart());
            logger.info("starting {} scripts", scriptContexts.size());
            if(!scriptContexts.isEmpty()){
                if(nannyFuture == null) {
                    logger.info("starting nanny");
                    nannyFuture = scheduler.scheduleAtFixedRate(() -> {
                        long timestamp = System.currentTimeMillis();
                        nannyTask.accept(timestamp);
                    }, Cmd.DEFAULT_IDLE_TIMER, Cmd.DEFAULT_IDLE_TIMER, TimeUnit.MILLISECONDS);
                }
                for(Cmd script : scriptContexts.keySet()){
                    ScriptContext contextResult = scriptContexts.get(script);
                    scriptObservers.forEach(observer -> observer.onStart(contextResult));
                    logger.trace("queueing\n  host={}\n  script={}",
                            contextResult.getSession().getHost().getHostName(),
                            script);
                    contextResult.getTimer().start("waiting in run queue");
                    getExecutor().submit(contextResult);
                }
            }else{
                checkActiveCount();
            }
        }else{
            logger.info("cannot start an already active Dispatcher");
        }
        logger.trace("start");


    }
//    public void schedule(Cmd command,Runnable runnable,long delay){
//        schedule(command,runnable,delay,TimeUnit.MILLISECONDS);
//    }
//    public void schedule(Cmd command,Runnable runnable,long delay,TimeUnit timeUnit){
//        ScheduledFuture<?> future = scheduler.schedule(runnable,delay,timeUnit);
//        if(activeCommands.containsKey(command)){
//            activeCommands.get(command).setScheduledFuture(future);
//        }
//    }
    public void shutdown(){
        stop();
        if(autoClose){
            executor.shutdown();
            scheduler.shutdown();
        }
    }
    public void stop() {
        stop(true);
    }
    public void stop(boolean wait){
        if(isStopping.compareAndSet(false,true)){
            if(isRunning.compareAndSet(true,false)){
                logger.debug("stop");

                if(nannyFuture!=null){
                    boolean cancelledFuture= nannyFuture.cancel(true);
                    nannyFuture = null;
                }
                //needs to occur before we notify observers because observers can queue next stage
                scriptContexts.forEach((cmd,ctx)->{
                    if(cmd instanceof Sh){
                        String peekOutput = ctx.getSession().peekOutput();
                        cmd.postRun(peekOutput,ctx);
                    }
                    ctx.closeLineQueue();
                    ctx.getSession().close(wait);

                });
                scriptContexts.clear();
            }
            dispatchObservers.forEach(c -> {
                try{
                    c.postStop();
                }catch(Exception e){
                    e.printStackTrace();
                }
            });
        }else{
            logger.warn("ignoring stop call when already stopped");
        }
    }

    private void checkActiveCount(){
        if(scriptContexts.isEmpty() && isRunning.compareAndSet(true,false)){
            executor.execute(() -> {
                dispatchObservers.forEach(o->o.postStop());
            });
        }
    }
}
