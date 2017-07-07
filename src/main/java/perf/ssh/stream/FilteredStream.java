package perf.ssh.stream;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by wreicher
 * A MultiStream that filters content before writing to subsequent OutputStreams.
 * TODO tree of filters to speed up filtering content
 */
public class FilteredStream extends MultiStream{

    private byte[] buffered;
    private int writeIndex = 0;
    private Map<String,byte[]> filters;
    private Map<String,byte[]> replacements;
    public FilteredStream(){
        buffered = new byte[20*1024];//4k was growing with most runs, 20k seems to work better
        filters = new HashMap<>();
        replacements = new HashMap<>();
    }

    public void addFilter(String name,String filter){
        filters.put(name,filter.getBytes());
        replacements.remove(name);
    }
    public void addFilter(String name,String filter,String replacement){

        filters.put(name,filter.getBytes());
        replacements.put(name,replacement.getBytes());
    }
    public boolean hasFilter(String name){
        return filters.containsKey(name);
    }
    public boolean hasReplacement(String name){
        return replacements.containsKey(name);
    }
    public void removeFilter(String name){
        filters.remove(name);
        replacements.remove(name);
    }

    private void superWrite(byte b[], int off, int len) throws IOException {

        super.write(b,off,len);
    }
    @Override
    public void write(byte b[], int off, int len) throws IOException {


        int flushIndex = 0;
        int trailingPrefixIndex = Integer.MAX_VALUE;
        if(filters.isEmpty()){
            if(writeIndex > 0){

                superWrite(buffered,0, writeIndex);
                writeIndex = 0;
            }
            super.write(b,off,len);
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



            for(int currentIndex = 0; currentIndex < writeIndex; currentIndex++){
                for (String name : filters.keySet()) {
                    byte[] filter = filters.get(name);
                    int prefixLength = prefixLength(buffered, filter, currentIndex, writeIndex);

                    if (prefixLength == filter.length) {//full match

                        if (flushIndex < currentIndex) {

                            superWrite(buffered, flushIndex, currentIndex - flushIndex);
                        }
                        if (replacements.containsKey(name)) {

                            superWrite(replacements.get(name), 0, replacements.get(name).length);
                            //call write(b[],int,int) to avoid infinite recursion if filter replacement includes filter text
                        }
                        currentIndex += filter.length;
                        flushIndex = currentIndex;
                        trailingPrefixIndex = Integer.MAX_VALUE;



                    } else if (prefixLength > 0) { // could be a match
                        if (currentIndex < trailingPrefixIndex) {

                            trailingPrefixIndex = currentIndex;


                        }
                    }
                }
            }

            if(trailingPrefixIndex < Integer.MAX_VALUE){//flush from flushIndex to trailingPrefixIndex

                if(trailingPrefixIndex-flushIndex>0) {

                    superWrite(buffered, flushIndex, trailingPrefixIndex - flushIndex);
                }
                flushIndex = trailingPrefixIndex;
            }else{// no matches and no potential matches, flush everything

                superWrite(buffered,flushIndex,writeIndex-flushIndex);
                flushIndex = writeIndex-1;
                flushIndex = writeIndex;//TODO testing if fixes double write
            }


            //compact the buffer if we haven't flushed everything

            if(flushIndex > 0 ) {
                System.arraycopy(buffered, flushIndex, buffered, 0, writeIndex-flushIndex);
                writeIndex=0;
            }
        }



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
