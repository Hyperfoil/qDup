package perf.qdup.stream;

import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;

public class SuffixStreamTest {

    @Test
    public void suffixLength_noMatch(){
        SuffixStream stream = new SuffixStream();
        byte expected[] = "FOO".getBytes();
        String input;
        input = "";
        assertEquals("FOO should not match any of \"\"",0,stream.suffixLength(input.getBytes(),expected,input.getBytes().length));
    }
    @Test
    public void suffixLength_fullMatch(){
        SuffixStream stream = new SuffixStream();
        byte expected[] = "FOO".getBytes();
        String input;
        input = "FOO";
        assertEquals("FOO should be a full match",3,stream.suffixLength(input.getBytes(),expected,input.getBytes().length));
    }
    @Test
    public void suffixLength_partialMatch(){
        SuffixStream stream = new SuffixStream();
        byte expected[] = "FOO".getBytes();
        String input;
        input = "FO";
        assertEquals("FOO should be a full match",2,stream.suffixLength(input.getBytes(),expected,input.getBytes().length));
    }
    @Test
    public void suffixLength_partialMatch2(){
        SuffixStream stream = new SuffixStream();
        byte expected[] = "FOO".getBytes();
        String input;
        input = "F";
        assertEquals("FOO should be a full match",1,stream.suffixLength(input.getBytes(),expected,input.getBytes().length));
    }

    @Test
    public void consumer_fullMatch(){
        SuffixStream stream = new SuffixStream();
        byte expected[] = "FOO".getBytes();
        stream.addSuffix("FOO");
        AtomicBoolean called = new AtomicBoolean(false);

        stream.addConsumer((s)->{called.set(true);});

        try {
            stream.write("BOOFOO".getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }

        assertTrue(called.get());
    }
    @Test
    public void consumer_splitMatch(){
        SuffixStream stream = new SuffixStream();
        byte expected[] = "FOO".getBytes();
        stream.addSuffix("FOO");
        AtomicBoolean called = new AtomicBoolean(false);

        stream.addConsumer((s)->{called.set(true);});

        try {
            stream.write("FO".getBytes());
            assertFalse(called.get());
            stream.write("".getBytes());
            stream.write("O".getBytes());
            assertTrue(called.get());
        } catch (IOException e) {
            e.printStackTrace();
        }




    }
}
