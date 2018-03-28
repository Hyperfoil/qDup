package perf.qdup.stream;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class SuffixStream extends MultiStream {


    private byte[] buffered;
    private int writeIndex = 0;
    private Map<String,byte[]> suffix;
    private List<Consumer<String>> consumers;


    public SuffixStream(){
        buffered = new byte[20*1024];
        suffix = new HashMap<>();
        consumers = new LinkedList<>();
    }

    public void clearSuffix(){
        suffix.clear();
    }
    public void addSuffix(String name){
        suffix.put(name,name.getBytes());
    }
    public boolean hasSuffix(String name){
        return suffix.containsKey(name);
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
        int flushIndex = 0;
        int trailingSuffixLength = Integer.MIN_VALUE;

        if(suffix.isEmpty()){
            if(writeIndex>0) {
                superWrite(buffered, 0, writeIndex);
                writeIndex = 0;
            }
            superWrite(b,off,len);
        }else{//we are going searching for a suffix
            if(writeIndex + len > buffered.length){
                int needed = writeIndex+len - buffered.length;
                byte[] newBuffer = new byte[buffered.length+needed];
                System.arraycopy(buffered, 0, newBuffer, 0, writeIndex);
                buffered = newBuffer;
            }
            //copy hte content to write into the bufferd content
            System.arraycopy(b,off,buffered, writeIndex,len);
            writeIndex += len;

            boolean found = false;

            for(String name : suffix.keySet()){
                byte[] toFind = suffix.get(name);
                int suffMatch = suffixLength(buffered,toFind,writeIndex);
                if(suffMatch==toFind.length){
                    found = true;

                    //blocking call on the current thread!!
                    consumers.forEach(c->c.accept(name));

                }else if (suffMatch > trailingSuffixLength){
                    trailingSuffixLength = suffMatch;
                }
            }
            if(trailingSuffixLength > Integer.MIN_VALUE){
                superWrite(buffered,0,writeIndex-trailingSuffixLength);
                System.arraycopy(buffered,writeIndex-trailingSuffixLength,buffered,0,trailingSuffixLength);
                writeIndex=trailingSuffixLength;
            }
        }

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

