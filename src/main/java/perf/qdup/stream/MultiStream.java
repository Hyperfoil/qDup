package perf.qdup.stream;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by wreicher
 * An OutputStream that can pass writes to multiple other  OutputStreams
 */
public class MultiStream extends OutputStream{

    public static String printByteCharacters(byte b[], int off, int len){
        String spaces = "         ";
        StringBuilder bytes = new StringBuilder();
        StringBuilder chars = new StringBuilder();
        StringBuilder indxs = new StringBuilder();
        bytes.append("[");
        chars.append("[");
        indxs.append("[");
        if(b!=null && b.length>0 && off+len < b.length){
            int lim = off+len;
            for(int i=off; i<lim; i++){
                int v = b[i];
                String append = v+"";
                bytes.append(append);
                bytes.append(".");
//                if(v == 10){
//                    chars.append(spaces.substring(0,append.length()-2));
//                    chars.append("\\n");
//                }else if (v == 13){
//                    chars.append(spaces.substring(0,append.length()-2));
//                    chars.append("\\r");
//                }else {
//                    chars.append(spaces.substring(0, append.length() - 1));
//                    chars.append((char) v);
//                }
//                indxs.append(String.format("%"+append.length()+"d.",i));
//                chars.append(".");
            }
        }
        bytes.append("]");
        chars.append("]");
        indxs.append("]");
        return "bytes="+bytes.toString()/*+System.lineSeparator()+"chars="+chars.toString()+System.lineSeparator()+"indxs="+indxs.toString()*/;
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
    //TODO add a non-closable stream option for System.out :)
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
        if(b==null || len < 0 || off + len > b.length){
            System.out.println(getClass().getName()+".write("+off+","+len+")");
            System.out.println(MultiStream.printByteCharacters(b,off,Math.min(10,b.length-off)));
            System.out.println(Arrays.asList(Thread.currentThread().getStackTrace()).stream().map(Object::toString).collect(Collectors.joining("\n")));
            System.exit(-1);
        }

        for (OutputStream s : streams.values()) {
            try {
                s.write(b, off, len);
            } catch (IOException e) {
                e.printStackTrace(System.out);
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
