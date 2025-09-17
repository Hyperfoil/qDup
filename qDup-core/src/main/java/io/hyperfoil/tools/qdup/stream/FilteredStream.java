package io.hyperfoil.tools.qdup.stream;


import org.jboss.logging.Logger;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Created by wreicher
 * A MultiStream that filters content before writing to subsequent OutputStreams.
 * TODO tree of filters to speed up filtering content
 * TODO need to also create a suffix filter (for PROMPT)
 */
public class FilteredStream extends MultiStream{
    private final static Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass());
    private byte[] buffered;
    private int writeIndex = 0;
    private int postFilterDrop = 0;
    private final Map<String,byte[]> filters;
    private final Map<String,byte[]> replacements;

    private final List<Consumer<String>> observers;

    public FilteredStream(){this("");}
    public FilteredStream(String name){
        super(name);
        buffered = new byte[20*1024];//4k was growing with most runs, 20k seems to work better
        filters = new ConcurrentHashMap<>();//to ensure key order
        replacements = new HashMap<>();
        observers = new LinkedList<>();
    }

    public String getBuffered(){
        return new String(buffered,0,writeIndex);
    }

    public void flushBuffer(){
        if(writeIndex>0){
            try {
                superWrite(buffered,0,writeIndex);
            } catch (IOException e) {
                //e.printStackTrace();
            }
            writeIndex=0;
        }
    }

    @Override
    public void flush()throws IOException {
        //flushBuffer();
        super.flush();
    }

    @Override
    public void close()throws IOException {
        flushBuffer();
        super.close();
    }


    public void addObserver(Consumer<String> consumer){
        observers.add(consumer);
    }
    public void removeObserver(Consumer<String> consumer){
        observers.remove(consumer);
    }
    public boolean hasObservers(){return !observers.isEmpty();}

    protected void tellObservers(String value){
        if(hasObservers()){
            for(Consumer<String> observer : observers){
                observer.accept(value);
            }
        }
    }

    public void addFilter(String name,String filter){
        addFilter(name,filter.getBytes());
    }
    public void addFilter(String name,byte bytes[]){
        filters.put(name,bytes);
        replacements.remove(name);
    }
    public void addFilter(String name,String filter,String replacement) {
        addFilter(name,filter.getBytes(),replacement.getBytes());
    }
    public void addFilter(String name,byte filter[],byte replacement[]){
        filters.put(name,filter);
        replacements.put(name,replacement);
    }
    public boolean hasFilter(String name){
        return filters.containsKey(name);
    }
    public String getFilter(String name){
        if(hasFilter(name)){
            return new String(getFilter(name));
        }else{
            return "";
        }
    }
    public boolean hasReplacement(String name){
        return replacements.containsKey(name);
    }
    public void remove(String name){
        filters.remove(name);
        replacements.remove(name);
    }

    protected void superWrite(byte b[], int off, int len) throws IOException {
        super.write(b,off,len);
    }

    @Override
    public void write(byte b[]) throws IOException {

        write(b,0,b.length);
    }

    @Override
    public void write(byte b[], int off, int len) throws IOException {
        try{
            int flushIndex = 0;
            int trailingPrefixIndex = Integer.MAX_VALUE;

            while(postFilterDrop > 0 && len > 0 && (b[off] == '\r' || b[off] == '\n') ){
                off++;
                len--;
                postFilterDrop--;
            }
            if(len>0){//if we are writing something then we are no longer post filter
                postFilterDrop=0;
            }
            if(filters.isEmpty()){
                if(writeIndex > 0){//something was buffered, probably back when there were filters

                    superWrite(buffered,0, writeIndex);
                    writeIndex = 0;
                }
                superWrite(b,off,len);
            }else{
                //if the current position + amount to write would overflow the buffer
                if(writeIndex + len > buffered.length){
                    int needed = writeIndex+len-buffered.length;
                    //slower growth because starting at 20k
                    byte[] newBuffer = new byte[buffered.length+needed];
                    System.arraycopy(buffered,0,newBuffer,0, writeIndex);
                    buffered = newBuffer;
                }
                //copy the content to write into the buffered content
                System.arraycopy(b,off,buffered, writeIndex,len);
                writeIndex +=len;
                boolean filteredAtLeastOnce = false;
                for(int currentIndex = 0; currentIndex < writeIndex; currentIndex++){
                    boolean filtered = false;
                    do{
                        filtered = false;
                        int dropLength = 0;
                        String matchedName = "";

                        //hack because ConcurrentModificationException from Sh calling before loop finishes :(
                        //Set<String> filterNames = new HashSet<>(filters.keySet());
                        //for(String name : filterNames){
                        String filterNames[] = filters.keySet().toArray(new String[0]);
                        for(int i=0; i<filterNames.length; i++){
                            String name = filterNames[i];
                            byte[] filter = filters.get(name);
                            if(filter!=null){
                                int prefixLength = prefixLength(buffered, filter, currentIndex, writeIndex-currentIndex);
                                if(prefixLength == filter.length) {//full match
                                    if(filter.length > dropLength){
                                        dropLength=filter.length;
                                        filtered=true;
                                        filteredAtLeastOnce=true;
                                        matchedName = name;
                                    }
                                }else if (prefixLength > 0) {
                                    if(trailingPrefixIndex > currentIndex){
                                        trailingPrefixIndex = currentIndex;
                                    }
                                }
                            }
                        }
                        if(filtered){
                            //tellObservers(matchedName); //FIX, move to after using the filter
                            //used 2 for \r\n but there can also be a \r from bracket paste mode's ?2004l\r
                            postFilterDrop += 3;
                            if ( flushIndex < currentIndex) {
                                superWrite(buffered,flushIndex, currentIndex - flushIndex);
                            }
                            if(hasReplacement(matchedName) && replacements.get(matchedName).length > 0){
                                superWrite(replacements.get(matchedName),0,replacements.get(matchedName).length);
                            }
                            int nextIndex = currentIndex + filters.get(matchedName).length;
                            //trap the potential \r\n if we filtered the entire write / line
                            //TODO BUG, this can increment currentIndex && flushIndex beyondWriteIndex
                            //TODO BUG, this only works if /r/n are on the same write, what about a subsequent call to write
                            if(postFilterDrop > 0 && currentIndex == off && nextIndex < writeIndex && (buffered[nextIndex]=='\n' || buffered[nextIndex]=='\r') ){
                                nextIndex++;
                                postFilterDrop--;
                            }
                            if(postFilterDrop > 0 && currentIndex == off && nextIndex < writeIndex &&  (buffered[nextIndex]=='\n' || buffered[nextIndex]=='\r') ){
                                nextIndex++;
                                postFilterDrop--;
                            }
                            //currentIndex += filters.get(matchedName).length;
                            currentIndex = nextIndex;
                            flushIndex = currentIndex;
                            trailingPrefixIndex = Integer.MAX_VALUE;
                            tellObservers(matchedName); //FIX, move to after using hte filter
                        }
                    }while(filtered);
                }

                if (trailingPrefixIndex < Integer.MAX_VALUE) { // flush from flushIndex to trailingPrefixIndex
                    if(trailingPrefixIndex-flushIndex>0) {
                        superWrite(buffered, flushIndex, trailingPrefixIndex - flushIndex);
                    }
                    flushIndex = trailingPrefixIndex;
                } else { // no matches and no potential matches, flush everything
                    if (flushIndex < writeIndex) { //fix #44 to only write empty
                        superWrite(buffered,flushIndex,writeIndex-flushIndex);//here is where writeIndex-flushIndex == -2
                    }

                    //flushIndex = writeIndex-1;
                    flushIndex = writeIndex;//TODO testing if fixes double write
                }
                //compact the buffer if we haven't flushed everything
                //TODO I think tail \r\n bug is happening here
                if (flushIndex > 0 ) {
                    System.arraycopy(buffered, flushIndex, buffered, 0, writeIndex-flushIndex);
                    writeIndex=writeIndex-flushIndex;
                }
            }
        } catch(Exception e) {
            logger.error(e.getMessage(),e);
            throw new RuntimeException("b.length="+(b==null?"null":b.length)+" off="+off+" len="+len+" buffered.length="+buffered.length, e);
        }
    }
    public boolean hasSuffix(byte b[],byte suffix[],int off,int len){
        boolean rtrn = true;
        for(int index=0; index < suffix.length && rtrn; index++){
            rtrn = index < len && suffix[index] == b[off+len-index-1];
        }
        return rtrn;
    }
    public int prefixLength(byte b[],byte prefix[],int off, int len){

        boolean matching = true;
        int rtrn = 0;
        for(rtrn=0; rtrn<prefix.length && matching; rtrn++){
            matching = rtrn < len && prefix[rtrn]==b[rtrn+off];
            if(!matching ){
                if(rtrn < len){
                    rtrn = -1; // discard that we matched some of the prefix because we didn't match all of it
                }else{
                    rtrn --;//to offset the ++ that will occur before next loop condition check
                }

            }
        }
        return rtrn;
    }
    public boolean hasPrefix(byte b[],byte prefix[],int off, int len){
        boolean rtrn = true;
        for(int i=0; i<prefix.length && rtrn; i++){
            rtrn = i < len && prefix[i] == b[i+off];

        }
        return rtrn;
    }


}
