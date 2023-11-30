package io.hyperfoil.tools.qdup.stream;

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
        try(SemaphoreStream stream = new SemaphoreStream(test,"\nfoo".getBytes())){
            try {
                stream.write("one\nfoo".getBytes());

                assertEquals("should release semaphore",1,test.availablePermits());
            } catch (IOException e) {
                e.printStackTrace();
                fail("Exception during write");
            }
        }catch (IOException e){
            fail(e.getMessage());
        }
    }
    @Test
    public void checkPrompt_filtered_0m(){
        Semaphore test = new Semaphore(0);
        try(FilteredStream filteredStream = new FilteredStream()){

            filteredStream.addFilter("tee","\u001b[0m");
            SemaphoreStream stream = new SemaphoreStream(test,"\nfoo".getBytes());
            ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);

            stream.addStream("bao",baos);
            filteredStream.addStream("semaphore",stream);
            try {
                filteredStream.write("\n\u001b[0mfoo".getBytes());
                assertEquals("should release semaphore",1,test.availablePermits());

                assertEquals("\\nfoo expected","\nfoo",new String(baos.toByteArray()));

            } catch (IOException e) {
                e.printStackTrace();
                fail("Exception during write");
            }
        }catch (IOException e){
            fail(e.getMessage());
        }
    }
}
