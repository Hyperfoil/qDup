package perf.ssh;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.CommandResult;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Container for all the coordination points between Cmds
 * Primarily used for the CountDownLatches that coordinate signal / waitFor commands
 * Also has counters that can increase / decrease depending on needs
 *
 */
public class Coordinator {

    final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());


    class Waiter {
        CommandResult result;
        String input;
        Cmd command;
        public Waiter( Cmd command, CommandResult result, String input){
            this.command = command;
            this.result = result;
            this.input = input;
        }
        public Cmd getCommand(){return command;}

        public CommandResult getResult() {return result;}
        public String getInput(){return input;}

        @Override
        public int hashCode(){return command.hashCode();}
        @Override
        public boolean equals(Object o){
            if(o instanceof Waiter){
                return command.getUid()==((Waiter) o).getCommand().getUid();
            }
            return false;
        }
        public void next(){
            result.next(command,input);
        }
        public void skip(){
            result.skip(command,input);
        }
    }

    private List<Consumer<String>> observers;

    private Map<String,AtomicInteger> latches;
    private Map<String,List<Waiter>> waitFors;

    private Map<String,AtomicInteger> counters;

    private AtomicInteger zero = new AtomicInteger(0);

    public Coordinator(){
        latches = new HashMap<>();
        counters = new HashMap<>();
        observers = new LinkedList<>();
        waitFors = new ConcurrentHashMap<>();
    }

    public List<Waiter> ensureWaitFor(String name){
        if(!waitFors.containsKey(name)){
            waitFors.put(name,new LinkedList<>());
        }
        return waitFors.get(name);
    }

    public void addObserver(Consumer<String> observer){
        observers.add(observer);
    }
    public void removeObserver(Consumer<String> observer){
        observers.remove(observer);
    }

    public int increase(String name){
        if(!counters.containsKey(name)){
            counters.put(name,new AtomicInteger(0));
        }
        return counters.get(name).incrementAndGet();
    }
    public int decrease(String name, int initialValue){
        if(!counters.containsKey(name)){
            counters.put(name,new AtomicInteger(initialValue));
        }
        return counters.get(name).decrementAndGet();
    }
    public int getCounter(String name){
        if(!counters.containsKey(name)){
            counters.put(name,new AtomicInteger(0));
        }
        return counters.get(name).get();

    }
    public void initialize(String name,int count){
        if(latches.containsKey(name)){
            logger.warn("duplicate initialize for {}, using current value {} not new value {}",name,latches.get(name).get(),count);
        }
        AtomicInteger latch = new AtomicInteger(count);
        latches.put(name,latch);
    }
    public int getSignalCount(String name){
        if(!latches.containsKey(name)){
            logger.error("signal {} missing latch, defaulting to 0",name);
            return 0;
        }
        return latches.get(name).get();
    }

    public void signal(String name){
        if(!latches.containsKey(name)){
            logger.warn("signal {} missing latch, ignoring",name);
            return;
        }
        if( latches.get(name).get() > 0 ){
            latches.get(name).decrementAndGet();
        }
        if( latches.get(name).get()<=0 ) {
            if(!observers.isEmpty()){
                for(Consumer<String> observer : observers){
                    observer.accept(name);
                }
            }
            List<Waiter> waiters = ensureWaitFor(name);
            for(Waiter waiter: waiters){
                waiter.next();
            }
            waiters.clear();
        }
    }
    public void waitFor(String name,Cmd command,CommandResult result,String input){
        Waiter waiter = new Waiter(command,result,input);
        waitFor(name,waiter);
    }
    private void waitFor(String name,Waiter waiter){
        if(!latches.containsKey(name)){
            logger.error("waitFor {} missing latch, using default latch with count=0",name);
            waiter.next();
        }else {
            //TODO this is a race condition, need a check after adding to the list as well. Refactor out of signal to use same check code
            if(latches.get(name).get() <=0){
                waiter.next();
            }else {
                ensureWaitFor(name).add(waiter);
            }
        }
    }
    public void waitFor(String name,Cmd command,CommandResult result,String input, long timeout, TimeUnit unit){
        //TODO use scheduled task to implement timeout
        Waiter waiter = new Waiter(command,result,input);
        waitFor(name,waiter);
    }
}
