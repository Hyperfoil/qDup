package perf.qdup.stream;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class SuffixStream extends MultiStream {
    final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());
    private static final long DELAY_MS = 1_000;

    private class FoundRunnable implements Runnable{
        private int lastIndex;
        private String name;

        public FoundRunnable(String name,int writeIndex){
            this.name = name;
            this.lastIndex = writeIndex;
        }

        public void reset(String name,int writeIndex){
            this.name = name;
            this.lastIndex = writeIndex;
        }
        public int getLastIndex(){return lastIndex;}
        public String getName(){return name;}

        @Override
        public void run() {
            if(lastIndex == writeIndex){//if there has not been a subsequent write
                foundSuffix(name,lastIndex);
            }
        }
    }

    private byte[] buffered;
    private int writeIndex = 0;
    private Map<String,byte[]> suffixes;
    private Map<String,byte[]> replacements;
    private List<Consumer<String>> consumers;

    private ScheduledThreadPoolExecutor executor;
    private ScheduledFuture future;
    private FoundRunnable foundRunnable;

    public SuffixStream(){
        this(null);

    }
    public SuffixStream(ScheduledThreadPoolExecutor threadPool){
        buffered = new byte[20*1024];
        suffixes = new LinkedHashMap<>();
        replacements = new LinkedHashMap<>();
        consumers = new LinkedList<>();
        executor = threadPool;
        future = null;
        foundRunnable = new FoundRunnable("",-1);
    }

    public void flushBuffer() throws IOException {
        if(writeIndex>0){
            superWrite(buffered,0,writeIndex);
            writeIndex=0;
        }
    }

    @Override
    public void flush()throws IOException {
        super.flush();
    }

    @Override
    public void close()throws IOException {
        flushBuffer();
        super.close();
    }

    public void clear(){
        suffixes.clear();
        replacements.clear();
    }
    public void addSuffix(String name){
        addSuffix(name,name);
    }
    public void addSuffix(String name,String suffix){
        suffixes.put(name,suffix.getBytes());
        replacements.remove(name);
    }
    public void addSuffix(String name,String suffix,String replacement){
        suffixes.put(name,suffix.getBytes());
        replacements.put(name,replacement.getBytes());
    }
    public boolean hasSuffix(String name){
        return suffixes.containsKey(name);
    }
    public void addConsumer(Consumer<String> consumer){
        consumers.add(consumer);
    }
    public boolean hasConsumers(){return !consumers.isEmpty();}
    public void removeConsumer(Consumer<String> consumer){
        consumers.remove(consumer);
    }
    public void clearConsumers(){
        consumers.clear();
    }

    private void superWrite(byte b[], int off, int len) throws IOException {
        super.write(b,off,len);
    }
    @Override
    public void write(byte b[]) throws IOException {
        write(b, 0, b.length);
    }
    @Override
    public void write(byte b[], int off, int len) throws IOException {
        if(b==null || len < 0 || off + len > b.length){
            System.out.println(getClass().getName()+".write("+off+","+len+")");
            System.out.println(MultiStream.printByteCharacters(b,off,Math.min(10,b.length-off)));
            System.out.println(Arrays.asList(Thread.currentThread().getStackTrace()).stream().map(Object::toString).collect(Collectors.joining("\n")));
            System.exit(-1);
        }
        //logger.info(getClass().getName()+".write("+off+","+len+")\n"+MultiStream.printByteCharacters(b,off,len));

        try {
            int trailingSuffixLength = Integer.MIN_VALUE;

            if (suffixes.isEmpty()) {
                if (writeIndex > 0) {
                    superWrite(buffered, 0, writeIndex);
                    writeIndex = 0;
                }
                superWrite(b, off, len);
            } else {//we are going searching for a suffix
                if (writeIndex + len > buffered.length) {
                    int needed = writeIndex + len - buffered.length;
                    byte[] newBuffer = new byte[buffered.length + needed];
                    System.arraycopy(buffered, 0, newBuffer, 0, writeIndex);
                    buffered = newBuffer;
                }
                //copy the content to write into the buffered content
                System.arraycopy(b, off, buffered, writeIndex, len);
                writeIndex += len;

                boolean found = false;
                String foundName = "";
                for (String name : suffixes.keySet()) {
                    byte[] toFind = suffixes.get(name);
                    int suffMatch = suffixLength(buffered, toFind, writeIndex);
                    if (suffMatch == toFind.length) {
                        if (!found || trailingSuffixLength < suffMatch) {//pick the longest match
                            found = true;
                            foundName = name;
                            trailingSuffixLength = suffMatch;
                        }
                    } else if (!found && suffMatch > trailingSuffixLength) {
                        trailingSuffixLength = suffMatch;
                    }
                }
                if (found) {
                    if(executor!=null){
                        if(future!=null){
                            future.cancel(true);
                        }
                        foundRunnable.reset(foundName,writeIndex);
                        future = executor.schedule(foundRunnable, DELAY_MS,TimeUnit.MILLISECONDS);
                    }else{
                        foundSuffix(foundName,writeIndex);
                    }

                } else if (trailingSuffixLength > Integer.MIN_VALUE) {
                    superWrite(buffered, 0, writeIndex - trailingSuffixLength);
                    System.arraycopy(buffered, writeIndex - trailingSuffixLength, buffered, 0, trailingSuffixLength);
                    writeIndex = trailingSuffixLength;
                }
            }
        }catch(Exception e){
            System.out.println(e.getMessage());
            e.printStackTrace(System.out);
            System.exit(-1);
        }
    }
    private void foundSuffix(String name,int index){
        try {
            if (replacements.containsKey(name)) {
                byte replacement[] = replacements.get(name);
                int trimLength = suffixes.get(name).length;
                superWrite(buffered, 0, writeIndex - trimLength);
                if (replacement.length > 0) {
                    superWrite(replacement, 0, replacement.length);
                }
                writeIndex = 0;
            } else {
                superWrite(buffered, 0, writeIndex);
                writeIndex = 0;
            }
        }catch(IOException e){
            e.printStackTrace(System.out);
        }
        consumers.forEach(c -> c.accept(name));

    }
    public int suffixLength(byte b[], byte toFind[], int endIndex){
        boolean matching = false;
        int rtrn = 0;
        for(int shift=Math.max(0,toFind.length-endIndex); shift< toFind.length && !matching; shift++){
            matching = true;
            int matchLength = toFind.length-shift;
            for(rtrn=0; rtrn < matchLength && matching; rtrn++){
                matching = rtrn < matchLength && b[endIndex-matchLength+rtrn] == toFind[rtrn];
                if(!matching){
                    if( rtrn < matchLength){
                        rtrn = -1;
                    }else{
                        rtrn -- ;
                    }
                }
            }
        }
        return rtrn;
    }



}

