package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import perf.yaup.json.Json;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Container for all the coordination points between Cmds
 * Primarily used for the CountDownLatches that coordinate signal / waitFor commands
 * Also has counters that can increase / decrease depending on needs
 *
 */
public class Coordinator {

    final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());


    class Waiter {
        Context context;
        Supplier<String> input;
        Cmd command;
        public Waiter( Cmd command, Context context, String input){
            this(command,context,()->input);
        }
        public Waiter( Cmd command, Context context, Supplier<String> input){
            this.command = command;
            this.context = context;
            this.input = input;
        }
        public Cmd getCommand(){return command;}

        public Context getContext() {return context;}
        public String getInput(){return input.get();}

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
            context.next(input.get());
        }
        public void skip(){
            context.skip(input.get());
        }
    }

    private List<Consumer<String>> observers;

    private Map<String,AtomicInteger> latches;
    private Map<String,Long> latchTimes;
    private Map<String,List<Waiter>> waitFors;

    private Map<String,AtomicInteger> counters;

    public Coordinator(){
        latches = new HashMap<>();
        latchTimes = new LinkedHashMap<>();
        counters = new HashMap<>();
        observers = new LinkedList<>();
        waitFors = new ConcurrentHashMap<>();
    }

    public List<Waiter> ensureWaitFor(String name){
        waitFors.putIfAbsent(name,Collections.synchronizedList(new LinkedList<>()));
        return waitFors.get(name);
    }

    public Map<String,Integer> getLatches(){
        Map<String,Integer> rtrn = new HashMap<>();
        latches.forEach((k,v)->rtrn.put(k,v.intValue()));
        return rtrn;
    }
    public Map<String,Long> getLatchTimes(){return Collections.unmodifiableMap(latchTimes);}
    public Map<String,Integer> getCounters(){
        Map<String,Integer> rtrn = new LinkedHashMap<>();
        counters.forEach((key,value)->{
            rtrn.put(key,value.get());
        });
        return rtrn;
    }

    public void addObserver(Consumer<String> observer){
        observers.add(observer);
    }
    public void removeObserver(Consumer<String> observer){
        observers.remove(observer);
    }

    public Json getWaitJson(){
        Json rtrn = new Json();
        waitFors.keySet().forEach(key->{
            Json entry = new Json();
            rtrn.set(key,entry);
            waitFors.get(key).forEach(waiter->{
                Cmd head = waiter.getCommand().getHead();
                Host host = waiter.getContext().getHost();
                entry.add(head.toString()+"-"+head.getUid()+"@"+host.getShortHostName());
            });
        });
        return rtrn;
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
            logger.warn("duplicate initialize for {}, using current VALUE {} not new VALUE {}",name,latches.get(name).get(),count);
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

    public void clearWaiters(){
        waitFors.clear();
    }
    public void signal(String name){

        if(!latches.containsKey(name)){
            logger.warn("signal {} missing latch, ignoring",name);
            return;
        }
        if( latches.get(name).get() > 0 ){
            latches.get(name).decrementAndGet();
            if(latches.get(name).get()==0){
                latchTimes.put(name,System.currentTimeMillis());
            }
        }
        if( latches.get(name).get()<=0 ) {
            if(latches.get(name).get() < 0){
                logger.error("Latch {} went below zero to {}",name,latches.get(name).get());
            }
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
    public void waitFor(String name, Cmd command, Context context, String input){
        Waiter waiter = new Waiter(command,context,input);
        waitFor(name,waiter);
    }
    private void waitFor(String name,Waiter waiter){
        if(!latches.containsKey(name)){
            logger.error("waitFor {} missing latch, using default latch WITH count=0",name);
            waiter.next();
        }else {
            //TODO this is a race condition, need a check after adding to the list as well. Refactor out of signal to use same check code
            if(latches.get(name).get() <=0){
                logger.info("waitFor {} count = {}, invoking next",name,latches.get(name).get());
                waiter.next();
            }else {
                logger.debug("waitFor {} count = {}, queueing",name,latches.get(name).get());
                ensureWaitFor(name).add(waiter);
            }
        }
    }
    public void removeWaiter(String name,Cmd command){
        waitFors.getOrDefault(name,new ArrayList<>()).removeIf((w)->w.hashCode() == command.hashCode());
    }
    public void waitFor(String name,Cmd command,Context context,Supplier<String> input){

        Waiter waiter = new Waiter(command,context,input);
        waitFor(name,waiter);
    }
    public void waitFor(String name,Cmd command,Context context,String input, long timeout, TimeUnit unit){
        //TODO use scheduled task to implement timeout
        Waiter waiter = new Waiter(command,context,input);
        waitFor(name,waiter);
    }


}
