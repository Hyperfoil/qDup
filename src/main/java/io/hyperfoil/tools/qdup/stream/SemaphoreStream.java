package io.hyperfoil.tools.qdup.stream;

import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by wreicher
 * A MultiStream that releases a Semaphore when a suffix is present
 */
public class SemaphoreStream extends MultiStream {

    private static final AtomicInteger counter = new AtomicInteger();

    private Semaphore lock;
    private byte prompt[];
    private AtomicInteger promptIndex = new AtomicInteger(0);
    private Runnable runnable;

    public SemaphoreStream(Semaphore lock, byte bytes[]){
        this.lock = lock;
        this.prompt = bytes;
    }

    @Override
    public void close() throws IOException{
        super.close();
    }

    public void setRunnable(Runnable runnable){
        this.runnable = runnable;
    }

    //TODO checkForPrompt isn't enough to capture all use cases
    //prompt can split between writes
    //gradle in color mode will randomly inject console controls between the newline and prompt
    //27 ...

    //replace with suffix checking

    public boolean checkForPrompt(byte sequence[], int offset, int length){
        boolean found = false;
        if(prompt == null) {
            return false;
        }

        int i=0;
        while(i < length && !found){
            if( promptIndex.get()>= prompt.length ) {
                found = true;
            } else {
                if (prompt[promptIndex.get()] == sequence[offset+i]){
                    promptIndex.incrementAndGet();
                    //chere here in case i==length
                    if(promptIndex.get() >= prompt.length){
                        found = true;
                    }
                }else{
                    promptIndex.set(0);
                    if(prompt[promptIndex.get()] == sequence[offset+i]){
                        promptIndex.incrementAndGet();
                        if(promptIndex.get() >= prompt.length){//for single character prompts
                            found = true;
                        }

                    }
                }
            }
            i++;
        }
        if(found){
            promptIndex.set(0);
        }
        return found;
    }
    @Override
    public void write(int val) throws IOException {
        super.write(val);
    }

    @Override
    public void write(byte b[]) throws IOException {

        write(b,0,b.length);
    }
    @Override
    public void write(byte b[], int off, int len) throws IOException {
        try {
            super.write(b, off, len);
            if (checkForPrompt(b, off, len)) {
                if (this.runnable != null) {
                    this.runnable.run();
                }
                lock.release();
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
