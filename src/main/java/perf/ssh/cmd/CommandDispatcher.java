package perf.ssh.cmd;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import perf.util.AsciiArt;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

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

    public interface Observer {
        public default void onStart(Cmd command){}
        public default void onStop(Cmd command){}
        public default void onNext(Cmd command,String output){}
        public default void onSkip(Cmd command,String output){}
        public default void onUpdate(Cmd command,String output){}
        public default void onStart(){}
        public default void onStop(){}
    }
    /**
     * Stores the CommandContext inside the CommandResult so that the Dispatcher can be used with multiple Host+Script combinations.
     */
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
                RunWatchers runWatchers = activeCommands.get(command);
                if(runWatchers!=null){
                    logger.debug("update [{}] watchers with [{}]",command,output);
                    runWatchers.add(output);
                }else{
                    logger.debug("no watchers for [{}]",command);
                }

            }catch(IllegalStateException e){
                logger.catching(e);
                e.printStackTrace();
            }catch(Exception e){
                e.printStackTrace();
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
            logger.info("{} watcher count = {}",this.name,watchers.size());

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

    private Map<Cmd,RunWatchers> activeCommands;
    private Map<Cmd,Thread> activeThreads;
    private HashSet<Integer> pastCommands;

    //bug same script can be mapped to multiple hosts

    private Map<Cmd,ScriptResult> script2Result;

    private List<Observer> observers;

    private ThreadPoolExecutor executor;

    private boolean isStopped;

    @Override public String toString(){return "CD";}

    public CommandDispatcher(ThreadPoolExecutor executor){
        this.executor = executor;

        this.activeCommands = Collections.synchronizedMap(new HashMap<>());
        this.activeThreads = Collections.synchronizedMap(new HashMap<>());
        this.pastCommands = new HashSet<>();
        this.script2Result = new LinkedHashMap<>();

        this.observers = new LinkedList<>();

        this.isStopped = true;
    }

    private Cmd removeActive(Cmd command){
        RunWatchers runWatchers = activeCommands.remove(command);
        if(runWatchers!=null) {
            try {
                runWatchers.stop();
            }catch (NullPointerException e){
                logger.info("NPE add null after {}",command);
            }
        }
        activeThreads.remove(command);
        return runWatchers!=null? command : null;
    }


    public void addObserver(Observer observer){
        observers.add(observer);
    }
    public void removeObserver(Observer observer){
        observers.remove(observer);
    }


    public void addScript(Script script,CommandContext context){
        //TODO BUG: scripts can be used multiple times (copy it)?
        //hack to fix issue where same script could not be run on multiple hosts
        //I don't like this hack because it breaks the ability to modify 1 script
        //and change all the instances
        script = (Script)script.copy();

        //TODO BUG: Cmd.InvokeCmd can change the tail and cause this to close profiler too soon
        ScriptResult previous = script2Result.put(
                script.getTail(),
                new ScriptResult(
                        script,
                        new ContextedResult(context)
                )
        );
        if(previous!=null){
            logger.error("already have script.tail={} mapped to {}@{}",script.getTail().getUid(),script.getName(),context.getSession().getHostName());
        }
    }
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
        isStopped = false;
        observers.forEach(c->c.onStart());
        logger.info("starting {} scripts",script2Result.size());
        if(!script2Result.isEmpty()){
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
    public void stop(){
        if(!isStopped){
            isStopped=true;
            activeCommands.values().forEach((w)->{w.stop();});
            activeThreads.values().forEach(thread->thread.interrupt());
        }
    }


    public void execute(Cmd command,String input,CommandContext context,CommandResult result){
        logger.trace(" execute {} with input=[{}]",command,input.length()>120?input.substring(0,120)+ AsciiArt.ELLIPSIS:input);
        if(isStopped){
            return;
        }
        if(!command.getWatchers().isEmpty()){
            WatcherResult watcherResult = new WatcherResult(context);
            BlockingQueue<String> queue = new LinkedBlockingQueue<>();

            RunWatchers watcherRunnable = new RunWatchers("watch:"+command,command.getWatchers(),context,watcherResult,queue);
            activeCommands.put(command,watcherRunnable);
            logger.info("queueing {} watchers for {}",command.getWatchers().size(),command);
            executor.execute(watcherRunnable);
        }
        executor.execute(new RunCommand(command,input,context,result));

    }
    public void dispatch(Cmd previousCommand, Cmd nextCommand,String input, CommandContext context, CommandResult result){

        if(isStopped){
            return;
        }
        if(previousCommand!=null){
            checkScriptDone(previousCommand,context);
        }

        if(nextCommand!=null){
            observers.forEach(c->c.onStart(nextCommand));
            activeCommands.put(nextCommand,null);
            pastCommands.add(nextCommand.getUid());
            context.getProfiler().start("waiting in run queue");
            execute(nextCommand,input,context,result);
        }else{
            logger.info("no next command from {}",previousCommand);
        }
        //remove after add to ensure activeCommands is only empty when there isn't a nextCommand
        if(previousCommand!=null) {
            Cmd removed = removeActive(previousCommand);
        }
        checkActiveCount();

    }
    private void checkScriptDone(Cmd command, CommandContext context){
        if(script2Result.containsKey(command)){//we finished a script
            context.getProfiler().stop();
            context.getProfiler().setLogger(context.getRunLogger());
            context.getProfiler().log();
            //context.getProfiler().print();

        }
        observers.forEach(c->c.onStop(command));
        script2Result.get(command).getResult().context.getSession().close();
        script2Result.remove(command);

    }
    private void checkActiveCount(){
        if(activeCommands.size()==0 && !isStopped){
            logger.info("activeCommands.size = {}",activeCommands.size());
            executor.execute(() -> {
                if(!isStopped) {
                    isStopped = true;
                    //closeSessions before observers because they may synchronously start loading more scripts :(
                    closeSessions();
                    observers.forEach(c -> c.onStop());
                }
            });
        }
    }
    public void closeSessions(){
        //TODO make thread safe
        logger.debug("{}.closeSessions",this);
        script2Result.values().forEach(scriptResult -> {
            logger.debug("closing connection to {}",scriptResult.getResult().context.getSession().getHostName());
            scriptResult.getResult().context.getSession().close();
        });
        script2Result.clear();
    }
    public boolean isActive(){return activeCommands.size()>0;}
    public int getActiveCount(){return activeCommands.size();}
}
