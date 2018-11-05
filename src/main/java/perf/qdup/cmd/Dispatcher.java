package perf.qdup.cmd;

import jdk.nashorn.internal.ir.Block;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import perf.qdup.Run;
import perf.qdup.cmd.impl.Abort;
import perf.qdup.cmd.impl.Done;
import perf.qdup.cmd.impl.Sh;
import perf.yaup.AsciiArt;
import perf.yaup.json.Json;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
 *
 * Need strong guarantees that all Script / Cmd / Dispatch Observers finish before closing the session / killing the treads with Stop
 *
 *
 */
public class Dispatcher {

    final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

    final static Thread.UncaughtExceptionHandler DefaultUncaughtExceptionHandler = (thread, throwable) ->{
        logger.error("UNCAUGHT:"+thread.getName()+" "+throwable.getMessage(),throwable);
    };


    final static long THRESHOLD = 30_000; //30s
    private static final String CLOSE_QUEUE = "CLOSE_QUEUE_OBSERVER_"+System.currentTimeMillis();
    private static final String QUEUE_OBSERVER = "QUEUE_OBSERVER";

    //Result called by watchers which will just invoke the next watcher on the current thread

    private final ConcurrentHashMap<Cmd,ScriptContext> scriptContexts;

    private final List<ScriptObserver> scriptObservers;
    private final List<DispatchObserver> dispatchObservers;

    private final ThreadPoolExecutor executor;
    private final ScheduledThreadPoolExecutor scheduler;
    private ScheduledFuture<?> nannyFuture;
    private final AtomicBoolean isRunning;
    private final Consumer<Long> nannyTask;

    private final boolean autoClose;

    private final ContextObserver observer = new ContextObserver() {
        @Override
        public void preNext(ScriptContext context, Cmd command, String output) {

        }
        @Override
        public void onDone(ScriptContext context){
            scriptContexts.remove(context.getRootCmd());
            scriptObservers.forEach(observer -> observer.onStop(context));
            context.getSession().close();
            checkActiveCount();
        }
    };

    public Json getActiveJson(){
        Json rtrn = new Json();

        scriptContexts.forEach( (rootCmd, context) -> {
            Json entry = new Json();
            Cmd currentCmd = context.getCurrentCmd();
            entry.set("name",Cmd.populateStateVariables(currentCmd.toString(),currentCmd,context.getState()));
            entry.set("host",context.getSession().getHost().toString());
            entry.set("uid",currentCmd.getUid());
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
        this.scriptObservers = new LinkedList<>();
        this.dispatchObservers = new LinkedList<>();

        this.nannyFuture = null;
        this.nannyTask = (timestamp)->{
            scriptContexts.forEach((script, context)->{
                Cmd command = context.getCurrentCmd();
                logger.trace("Nanny checking:\n  host={}\n  command={}",
                        context.getSession().getHost(),
                        command);
                long lastUpdate = context.getUpdateTime();
                if(timestamp - lastUpdate > THRESHOLD){
                    if(command instanceof Sh){
                        //TODO check for common prompts in output?
                        String output = context.getSession().peekOutput();
                        if(!command.isSilent()){
                            logger.warn("Nanny found idle\n  command={}\n  host={}\n  script={}\n  idle={}",
                                    command,
                                    context.getSession().getHost().getHostName(),
                                    script,
                                    String.format("%5.2f", (1.0 * timestamp - lastUpdate) / 1_000));
                        }
                    }
                }
            });

        };
        this.isRunning = new AtomicBoolean(false);
    }

    public ScheduledThreadPoolExecutor getScheduler(){return scheduler;}

    public BlockingQueue<Runnable> getRunQueue(){
        return executor.getQueue();
    }

    public void addScriptObserver(ScriptObserver observer){scriptObservers.add(observer);}
    public void removeScriptObserver(ScriptObserver observer){
        scriptObservers.remove(observer);
    }
    public void addDispatchObserver(DispatchObserver observer){dispatchObservers.add(observer);}
    public void removeDispatchObserver(DispatchObserver observer){dispatchObservers.remove(observer);}

    public ExecutorService getExecutor(){return executor;}

    public void addScriptContext(ScriptContext context){
        logger.trace("add script {} to {}",context.getRootCmd(),context.getSession().getHost().getHostName());

        context.setObserver(observer);
        ScriptContext previous = scriptContexts.put(
                context.getRootCmd(),
                context
        );
        if(previous!=null){
            logger.error("already have getScript.tail={} mapped to {}@{}",context.getRootCmd().getTail().getUid(),context.getRootCmd(),context.getSession().getHost().getHostName());
        }
        if(isRunning.get()){
            ScriptContext contextResult = scriptContexts.get(context.getRootCmd());
            scriptObservers.forEach(observer -> observer.onStart(contextResult));

            logger.info("queueing\n  host={}\n  script={}",
                    contextResult.getSession().getHost().getHostName(),
                    context.getRootCmd());
            getRunQueue().add(context);
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
            dispatchObservers.forEach(c->c.preStart());
            logger.info("starting {} scripts", scriptContexts.size());
            if(!scriptContexts.isEmpty()){
                if(nannyFuture == null) {
                    logger.info("starting nanny");
                    nannyFuture = scheduler.scheduleAtFixedRate(() -> {
                        long timestamp = System.currentTimeMillis();
                        nannyTask.accept(timestamp);
                    }, THRESHOLD, THRESHOLD, TimeUnit.MILLISECONDS);
                }
                for(Cmd script : scriptContexts.keySet()){
                    ScriptContext contextResult = scriptContexts.get(script);
                    scriptObservers.forEach(observer -> observer.onStart(contextResult));
                    logger.trace("queueing\n  host={}\n  script={}",
                            contextResult.getSession().getHost().getHostName(),
                            script);
                    getRunQueue().add(contextResult);
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
    public void stop(){
        if(isRunning.compareAndSet(true,false)){
            logger.debug("stop");

            if(nannyFuture!=null){
                boolean cancelledFuture= nannyFuture.cancel(true);
                nannyFuture = null;
            }
            //needs to occur before we notify observers because observers can queue next stage
            scriptContexts.values().forEach(ctx->{
                ctx.getSession().close();
            });
            scriptContexts.clear();
            dispatchObservers.forEach(c -> c.postStop());

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
