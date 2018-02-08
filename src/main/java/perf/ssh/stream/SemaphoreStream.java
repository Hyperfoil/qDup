package perf.ssh.stream;

import perf.ssh.cmd.CommandResult;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Semaphore;

/**
 * Created by wreicher
 * A MultiStream that releases a Semaphore when a suffix is present
 */
public class SemaphoreStream extends MultiStream {
    private Semaphore lock;
    private byte prompt[];
    private Runnable runnable;

    public SemaphoreStream(Semaphore lock, byte bytes[]){
        this.lock = lock;
        this.prompt = bytes;
    }
    public void setRunnable(Runnable runnable){
        this.runnable = runnable;
    }

    public boolean hasSuffix(byte sequence[],byte suffix[],int offset,int length){
        if(suffix == null) {
            return true;
        }
        if(sequence == null || length < suffix.length || sequence.length-offset < length){
            return false;
        }
        int diff = offset+length-suffix.length;
        byte seqSuffix[] = Arrays.copyOfRange(sequence,diff,diff+suffix.length);
        boolean rtrn =Arrays.equals(seqSuffix,suffix);
        return rtrn;
    }
    @Override
    public void write(byte b[]) throws IOException {

        write(b,0,b.length);
    }
    @Override
    public void write(byte b[], int off, int len) throws IOException {
        System.out.print("SS: "+new String(b,off,len));
        try {
            super.write(b, off, len);
            if (hasSuffix(b, prompt, off, len)) {
                lock.release();

                if (this.runnable != null) {
                    this.runnable.run();
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
