package perf.qdup.stream;

import java.io.IOException;
import java.util.concurrent.Semaphore;

/**
 * Created by wreicher
 * Removes terminal commands from the Stream
 */
public class TermFilteredStream extends SemaphoreStream {

    public static final  byte ESCAPE_BYTE = 0x1B;
    public static final byte[] DISABLE_LINE_WRAP = new byte[]{0x1B,'[','7','l'};
    public static final byte[] RESET_DEVICE = new byte[]{0x1B,'c'};
    public static final byte[] ENABLE_LINE_WRAP = new byte[]{0x1B,'[','7','h'};

    public TermFilteredStream(Semaphore semaphore, byte bytes[]){
        super(semaphore,bytes);
    }

    @Override
    public void write(byte b[], int off, int len) throws IOException{
        //strip out any control characters
        for(int i=0; i<len; i++){
            if(b[off+i]== DISABLE_LINE_WRAP[0] && i+1 < len && b[off+i+1]== DISABLE_LINE_WRAP[1] && i+2 < len && b[off+i+2] == DISABLE_LINE_WRAP[2] && i+3 < len && b[off+i+3] == DISABLE_LINE_WRAP[3]){
                //skip from i to i+3
                if(i>0){
                    super.write(b,off,i);
                }
                if(i+4 < len) {
                    write(b,off+i+4,len-(i+4));
                }
                return;
            }
        }

        super.write(b,off,len);
    }
}
