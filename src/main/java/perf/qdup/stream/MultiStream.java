package perf.qdup.stream;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by wreicher
 * An OutputStream that can pass writes to multiple other  OutputStreams
 */
public class MultiStream extends OutputStream{

    public static String printByteCharacters(byte b[], int off, int len){
        String spaces = "         ";
        StringBuilder bytes = new StringBuilder();
        StringBuilder chars = new StringBuilder();
        bytes.append("[");
        chars.append("[");
        if(b!=null && b.length>0){
            int lim = off+len;
            for(int i=off; i<lim; i++){
                int v = b[i];
                String append = v+"";
                bytes.append(append);
                bytes.append(".");
                if(v == 10){
                    chars.append(spaces.substring(0,append.length()-2));
                    chars.append("\\n");
                }else if (v == 13){
                    chars.append(spaces.substring(0,append.length()-2));
                    chars.append("\\r");
                }else {
                    chars.append(spaces.substring(0, append.length() - 1));
                    chars.append((char) v);
                }
                chars.append(".");
            }
            bytes.append("]");
            chars.append("]");
        }
        return "bytes="+bytes.toString()+System.lineSeparator()+"chars="+chars.toString();
    }

    private Map<String,OutputStream> streams;

    public MultiStream(){
        this.streams = new HashMap<>();
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
            } catch (IOException e) {
                e.printStackTrace();
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
