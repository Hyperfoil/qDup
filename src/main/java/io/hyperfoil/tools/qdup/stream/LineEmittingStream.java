package io.hyperfoil.tools.qdup.stream;

import io.hyperfoil.tools.yaup.AsciiArt;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Created by wreicher
 * A Stream that synchronously emits lines to Consumers
 */
public class LineEmittingStream extends OutputStream {
    final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());


    private String name = "";
    int writeIndex = 0;
    byte buffered[] = new byte[4*1024];

    private List<Consumer<String>> consumers = new LinkedList<>();

    public LineEmittingStream(){this(""+System.currentTimeMillis());}
    public LineEmittingStream(String name){
        this.name = name;
    }

    public boolean addConsumer(Consumer<String> consumer){
        return consumers.add(consumer);
    }
    public boolean removeConsumer(Consumer<String> consumer){
        return consumers.remove(consumer);
    }

    public void reset() {
        writeIndex = 0;
    }

    public void forceEmit(){
        if(writeIndex >0) {
            emit(buffered, 0, writeIndex);
            reset();
        }
    }
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public void flush() throws IOException {
        super.flush();
    }

    @Override
    public void close() throws IOException {
        forceEmit();
        super.close();
    }

    @Override
    public void write(int i) throws IOException {}

    public void write(String content) throws IOException {
        write(content.getBytes());
    }

    @Override
    public void write(byte b[]) throws IOException {
        write(b,0,b.length);
    }
    @Override
    public void write(byte b[], int off, int len) {
        try {
            int writeFrom = off;
            //printB(b,off,len);
            for (int i = 0; i < len; i++) {
                if (b[off + i] == 10 || b[off + i] == 13) { // if CR or LR
                    if (writeIndex == 0) {//nothing buffered, can just flush from b
                        emit(b, writeFrom, off + i - writeFrom);
                    } else {//have to add up to off+i to buffered to emit all at once
                        if (writeIndex + off + i >= buffered.length) {
                            byte newBuffered[] = new byte[buffered.length * 2];
                            System.arraycopy(buffered, 0, newBuffered, 0, writeIndex);
                            buffered = newBuffered;
                        }
                        System.arraycopy(b, off, buffered, writeIndex, off + i - writeFrom);
                        writeIndex = writeIndex + off + i - writeFrom;

                        emit(buffered, 0, writeIndex);
                        reset();
                    }
                    if (i + 1 < len && (b[off + i + 1] == 10 || b[off + i + 1] == 13)) {//skip the next CR or LR
                        i++;//skip over the CR or LR
                    }
                    writeFrom = off + i + 1;//+1 to skip over the current CR|LR
                }
            }
            if (writeFrom < off + len) {
                int toBuffer = (off + len - writeFrom);//remaining bytes
                //should only grow once but still using while loop
                while (writeIndex + toBuffer > buffered.length) {
                    int needed = writeIndex + toBuffer - buffered.length;
                    byte newBuffered[] = new byte[buffered.length + 2 * needed];
                    System.arraycopy(buffered, 0, newBuffered, 0, writeIndex);
                    buffered = newBuffered;
                }

                System.arraycopy(b, writeFrom, buffered, writeIndex, toBuffer);
                writeIndex += toBuffer;
            }
        }catch(Exception e){
            logger.error(e.getMessage(),e);
            throw new RuntimeException("b.length="+(b==null?"null":b.length)+" off="+off+" len="+len+" buffered.length="+buffered.length, e);
        }
    }
    public int find(byte[] array,byte[] content,int start,int length){
        if(array == null || content == null || array.length < content.length){
            return -1;
        }
        if(start >=array.length || length == 0 || length+start > array.length){
            return -1;
        }
        int matched = 0;
        for(int a=start; a<=start+length-content.length; a++){
            matched = 0;
            for(int c=0; c<content.length; c++){
                if( array[a+c] != content[c] ){
                    break;
                } else {
                    matched ++;
                }

            }
            if( matched == content.length){
                return a;
            }
        }
        return -1;
    }
    private void emit(byte content[], int start,int length){
        //trips out ANSI escape sequences (such as terminal coloring)
        String toEmit = new String(content,start,length);
        if(!consumers.isEmpty()){
            for(Consumer<String> consumer : consumers){
                consumer.accept(toEmit);
            }
        }
    }

    public int getBufferLength() {
        return buffered.length;
    }

    public int getWriteIndex() {
        return writeIndex;
    }
}
