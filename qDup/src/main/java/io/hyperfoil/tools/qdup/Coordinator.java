package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.yaup.json.Json;
import org.jboss.logging.Logger;

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

    final static Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass());

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

    private Map<String,AtomicInteger> signalLatches;
    private Map<String,Long> latchTimes;
    private Map<String,List<Waiter>> waitFors;

    private Map<String,AtomicInteger> counters;

    private final Globals globals;

    public Coordinator(Globals globals){
        signalLatches = new HashMap<>();
        latchTimes = new LinkedHashMap<>();
        counters = new HashMap<>();
        observers = new LinkedList<>();
        waitFors = new ConcurrentHashMap<>();
        this.globals = globals;
    }

    public boolean hasSetting(String key){
        return globals.hasSetting(key);
    }
    public void setSetting(String key,Object value){
        globals.addSetting(key,value);
    }
    public <T> T getSetting(String key, T defaultValue){
        return globals.getSetting(key,defaultValue);
    }

    public List<Waiter> ensureWaitFor(String name){
        waitFors.putIfAbsent(name,Collections.synchronizedList(new LinkedList<>()));
        return waitFors.get(name);
    }

    public Map<String,Integer> getLatches(){
        Map<String,Integer> rtrn = new HashMap<>();
        signalLatches.forEach((k, v)->rtrn.put(k,v.intValue()));
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
        Json rtrn = new Json(false);
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

    public void setCounter(String name, int value){
        counters.put(name,new AtomicInteger(value));
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
    public void setSignal(String name, int count){
        setSignal(name,count,false);
    }
    public void setSignal(String name, int count,boolean force){
        if(signalLatches.containsKey(name) && signalLatches.get(name).get()>0 && !force){
            logger.warnf("duplicate setSignal for %s, using previous VALUE %s not new VALUE %d",name, signalLatches.get(name).get(),count);
            return;
        }
        AtomicInteger latch = new AtomicInteger(count);
        signalLatches.put(name,latch);
        checkWatchers(name);
    }
    public boolean hasSignal(String name){
        return signalLatches.containsKey(name);
    }
    public int getSignalCount(String name){
        if(!signalLatches.containsKey(name)){
            logger.errorf("signal %s missing latch, defaulting to 0",name);
            return 0;
        }
        return signalLatches.get(name).get();
    }
    public int getWaitCount(String name){
        if(!waitFors.containsKey(name)){
            return 0;
        }
        return waitFors.get(name).size();
    }

    public void clearWaiters(){
        waitFors.clear();
    }
    private void checkWatchers(String name){
        //TODO this should not signal missing once we correctly find singals inside for-each
        if( !signalLatches.containsKey(name) || signalLatches.get(name).get()<=0  ) {//signal for a missing latch
            if(signalLatches.containsKey(name) && signalLatches.get(name).get() < 0){
                logger.errorf("Latch %s went below zero to %d",name, signalLatches.get(name).get());
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
    public void signal(String name){

        if(!signalLatches.containsKey(name)){
            logger.warnf("signal %s missing latch, ignoring",name);
            //return;
        }else if( signalLatches.get(name).get() > 0 ){
            signalLatches.get(name).decrementAndGet();
            if(signalLatches.get(name).get()==0){
                latchTimes.put(name,System.currentTimeMillis());
            }
        }

        checkWatchers(name);
    }
    public void waitFor(String name, Cmd command, Context context, String input){
        Waiter waiter = new Waiter(command,context,input);
        waitFor(name,waiter);
    }
    private void waitFor(String name,Waiter waiter){
        if(!signalLatches.containsKey(name)){
            logger.errorf("waitFor %s missing latch, using default latch WITH count=0",name);
            waiter.next();
        }else {
            //TODO this is a race condition, need a check after adding to the list as well. Refactor out of signal to use same check code
            if(signalLatches.get(name).get() <=0){
                logger.debugf("waitFor %s count = %s, invoking next",name, signalLatches.get(name).get());
                waiter.next();
            }else {
                logger.debugf("waitFor %s count = %s, queueing",name, signalLatches.get(name).get());
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

    public List<String> getJsSnippetContents(){
        return globals.getJsSnippetsContents();
    }


}
