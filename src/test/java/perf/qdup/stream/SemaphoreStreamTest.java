package perf.qdup.stream;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.Semaphore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SemaphoreStreamTest {


    @Test
    public void checkPrompt(){
        Semaphore test = new Semaphore(0);
        SemaphoreStream stream = new SemaphoreStream(test,"\nfoo".getBytes());
        try {
            stream.write("one\nfoo".getBytes());

            assertEquals("should release semaphore",1,test.availablePermits());
        } catch (IOException e) {
            e.printStackTrace();
            fail("Exception during write");
        }
    }
    @Test
    public void checkPrompt_filtered_0m(){
        Semaphore test = new Semaphore(0);
        FilteredStream filteredStream = new FilteredStream();

        filteredStream.addFilter("tee","\u001b[0m");
        SemaphoreStream stream = new SemaphoreStream(test,"\nfoo".getBytes());
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);

        stream.addStream("bao",baos);
        filteredStream.addStream("semaphore",stream);
        try {
            System.out.println(SemaphoreStream.printByteCharacters("\u001b[0123456789m".getBytes(),0,"\u001b[0123456789m".getBytes().length));
            System.out.println(SemaphoreStream.printByteCharacters("\033[0;34m".getBytes(),0,"\033[0;34m".getBytes().length));
            System.out.println(SemaphoreStream.printByteCharacters("\\x1b[0m".getBytes(),0,"\\x1b[0m".getBytes().length));
            filteredStream.write("\n\u001b[0mfoo".getBytes());
            assertEquals("should release semaphore",1,test.availablePermits());

            System.out.println(new String(baos.toByteArray()));
            System.out.println(SemaphoreStream.printByteCharacters(baos.toByteArray(),0,baos.toByteArray().length));
        } catch (IOException e) {
            e.printStackTrace();
            fail("Exception during write");
        }
    }
}
