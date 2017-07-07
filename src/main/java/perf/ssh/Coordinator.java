package perf.ssh;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
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

    private List<Consumer<String>> observers;

    private Map<String,CountDownLatch> latches;
    private Map<String,AtomicInteger> counters;

    private CountDownLatch zero = new CountDownLatch(0);

    public Coordinator(){

        latches = new HashMap<>();
        counters = new HashMap<>();
        observers = new LinkedList<>();
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
    public void initialize(String name,int count){
        if(latches.containsKey(name)){
            logger.warn("duplicate initialize for {}, using current value {} not new value {}",name,latches.get(name).getCount(),count);
        }
        CountDownLatch latch = new CountDownLatch(count);
        latches.put(name,latch);
    }
    public long getCount(String name){
        if(!latches.containsKey(name)){
            logger.error("signal {} missing latch, defaulting to 0",name);
            return 0;
        }
        return latches.get(name).getCount();
    }

    public void signal(String name){
        if(!latches.containsKey(name)){
            logger.warn("signal {} missing latch, ignoring",name);
            return;
        }
        latches.get(name).countDown();
        if(latches.get(name).getCount()==0){
            if(!observers.isEmpty()){
                for(Consumer<String> observer : observers){
                    observer.accept(name);
                }
            }
        }
    }
    public void waitFor(String name){
        if(!latches.containsKey(name)){
            logger.error("waitFor {} missing latch, using default latch with count=0",name);
            latches.put(name,zero);
        }
        try{
            latches.get(name).await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    public boolean waitFor(String name, long timeout, TimeUnit unit){
        boolean acquired = false;
        if(!latches.containsKey(name)){
            logger.error("waitFor {} missing latch, using default latch with count=0",name);
            latches.put(name,zero);
        }
        try{
            acquired = latches.get(name).await(timeout,unit);
            if(!acquired){
                //TODO what todo when not acquired
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return acquired;
    }
}
