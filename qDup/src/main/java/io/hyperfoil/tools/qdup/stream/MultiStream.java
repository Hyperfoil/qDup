package io.hyperfoil.tools.qdup.stream;


import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by wreicher
 * An OutputStream synchronously writes to multiple other  OutputStreams
 */
public class MultiStream extends OutputStream{

    private final static Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass());

    public static String printByteCharacters(String input){
        return printByteCharacters(input.getBytes(),0,input.getBytes().length);
    }
    public static String printByteCharacters(byte b[], int off, int len){
        String spaces = "                                                                   ";
        StringBuilder bytes = new StringBuilder();
        StringBuilder chars = new StringBuilder();
        StringBuilder indxs = new StringBuilder();
        bytes.append("[");
        chars.append("[");
        indxs.append("[");
        if(b!=null && b.length>0 && off+len <= b.length){
            int lim = off+len;
            for(int i=off; i<lim; i++){
                int v = b[i];
                String byteValue = ""+v;
                String charValue = "";
                switch (v){
                    case 10: charValue = "'\\n'";
                        break;
                    case 13: charValue = "'\\r'";
                        break;
                    case 27: charValue = "'\\e'";
                        break;
                    default: charValue = "'"+((char)v)+"'";
                }
                String indexValue = ""+i;
                int width = Math.max(Math.max(byteValue.length(),charValue.length()),indexValue.length());
                if(bytes.length()>1){
                    bytes.append(",");
                    chars.append(",");
                    indxs.append(",");
                }

                bytes.append(String.format("%"+width+"s",byteValue));
                chars.append(String.format("%"+width+"s",charValue));
                indxs.append(String.format("%"+width+"s",indexValue));
            }
        }
        bytes.append("]");
        chars.append("]");
        indxs.append("]");
        return "bytes="+bytes.toString()+System.lineSeparator()+"chars="+chars.toString()+System.lineSeparator()+"indxs="+indxs.toString();
    }

    private String name = "";
    private Map<String,OutputStream> streams;

    public MultiStream(){this(""+System.currentTimeMillis());}
    public MultiStream(String name){
        this.name = name;
        this.streams = new HashMap<>();
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    @Override
    public void close() throws IOException {
        super.close();
        if(!streams.isEmpty()){
            for(OutputStream s : streams.values()){
                s.close();
            }
        }
    }
    //TODO add a non-closable stream option for System . out :)
    public void addStream(String key,OutputStream stream){
        streams.put(key,stream);
    }
    public void removeStream(String key){
        if(hasStream(key)){
            streams.remove(key);
        }
    }
    public boolean hasStream(String key){
        return streams.containsKey(key);
    }
    public OutputStream getStream(String key){
        return streams.get(key);
    }

    @Override
    public void write(int b) throws IOException {
        //System.err.println("MultiStream.write("+b+") not supported");
        for(OutputStream s : streams.values()){
            //s.write(b);
        }
    }
    @Override
    public void write(byte b[]) throws IOException {

        write(b,0,b.length);
    }
    @Override
    public void write(byte b[], int off, int len) throws IOException {
        for (OutputStream s : streams.values()) {
            try {
                s.write(b, off, len);
            } catch (Exception e) {
                logger.error("MultiStream.write():"+e.getMessage()+" b="+(b!=null ? b.length : "null")+" off="+off+" len="+len,e);
            }
        }
    }
    @Override
    public void flush() throws IOException {
        for(OutputStream s : streams.values()){
            s.flush();
        }
    }
}
