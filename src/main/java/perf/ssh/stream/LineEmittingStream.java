package perf.ssh.stream;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Created by wreicher
 * A Stream that emits lines to Consumers
 */
public class LineEmittingStream extends OutputStream {

    int index = 0;
    byte buffered[] = new byte[4*1024];

    private List<Consumer<String>> consumers = new LinkedList<>();

    public LineEmittingStream(){}

    public boolean addConsumer(Consumer<String> consumer){
        return consumers.add(consumer);
    }
    public boolean removeConsumer(Consumer<String> consumer){
        return consumers.remove(consumer);
    }

    public void reset() { index = 0; }

    @Override
    public void write(int i) throws IOException {}
    @Override
    public void write(byte b[]) throws IOException {
        write(b,0,b.length);
    }
    @Override
    public void write(byte b[], int off, int len) {
        int lineBreak = -1;
        int next=-1;
        int lim = off+len;

        //printB(b,off,len);
        for(int i = off; i<lim; i++){
            if( b[i] == 10 || b[i]==13 ){ // if CR or LR
                lineBreak=i;
                if(index==0){
                    emit(b,off,lineBreak-off);//because we don't want the char @ lineBreak
                }else{
                    if(index+lineBreak-off >= buffered.length){
                        byte newBuf[] = new byte[buffered.length*2];
                        System.arraycopy(buffered,0,newBuf,0,index);
                        buffered = newBuf;
                    }

                    System.arraycopy(b,off,buffered,index,(lineBreak-off));
                    index+=(lineBreak-off);

                    emit(buffered,0,index);
                }
                if(i+1 < lim && (b[i+1]==10 || b[i+1]==13)){//skip the next CR or LR

                    lineBreak++;
                    i++;
                }
                len-=lineBreak-off+1;//because we don't want the char @ lineBreak
                off=lineBreak+1;//because we don't want the char @ lineBreak
                //printB(b,off,len);
            }
        }
        if(len>0){

            if(index+len>buffered.length){
                byte newBuffered[] = new byte[buffered.length*2];
                System.arraycopy(buffered,0,newBuffered,0,index);
                buffered = newBuffered;
            }

            System.arraycopy(b,off,buffered,index,len);
            index+=len;
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
        int lim = start+length;
        //trips out ANSI escape sequences (such as terminal coloring)
        String toEmit = new String(content,start,length).replaceAll("\u001B\\[[;\\d]*m", "");
        if(!consumers.isEmpty()){
            for(Consumer<String> consumer : consumers){
                consumer.accept(toEmit);
            }
        }
        reset();
    }

    public int getBufferLength() {
        return buffered.length;
    }

    public int getIndex() {
        return index;
    }
}
