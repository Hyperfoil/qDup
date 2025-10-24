package io.hyperfoil.tools.qdup.stream;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.*;

public class SuffixStreamTest {

    @Test
    public void suffixLength_injected_middle(){
        SuffixStream stream = new SuffixStream();
        stream.addInjectable((byte)'\r');
        byte expected[] = "FOO".getBytes();
        String input = "FO\rO";
        SuffixStream.MatchLength matchLength = stream.suffixLength(input.getBytes(),expected,input.getBytes().length);
        assertTrue(matchLength.fullMatch());
        assertEquals(expected.length+1,matchLength.length());
    }
    @Test
    public void suffixLength_injected_end(){
        SuffixStream stream = new SuffixStream();
        stream.addInjectable((byte)'\r');
        byte expected[] = "FOO".getBytes();
        String input = "FOO\r";
        SuffixStream.MatchLength matchLength = stream.suffixLength(input.getBytes(),expected,input.getBytes().length);
        assertFalse(matchLength.fullMatch());
        assertEquals(0,matchLength.length());
    }


    @Test
    public void suffixLength_end_before_match(){
        SuffixStream stream = new SuffixStream();
        byte expected[] = "FOO".getBytes();
        String input;
        input = "notFOO";
        SuffixStream.MatchLength matchLength = stream.suffixLength(input.getBytes(),expected,2);
        assertEquals("FOO should not match any of \"\"",0,matchLength.length());
        assertFalse(matchLength.fullMatch());
    }
    @Test
    public void suffixLength_noMatch(){
        SuffixStream stream = new SuffixStream();
        byte expected[] = "FOO".getBytes();
        String input;
        input = "";
        SuffixStream.MatchLength matchLength = stream.suffixLength(input.getBytes(),expected,input.getBytes().length);
        assertEquals("FOO should not match any of \"\"",0,matchLength.length());
        assertFalse(matchLength.fullMatch());
    }
    @Test
    public void suffixLength_fullMatch(){
        SuffixStream stream = new SuffixStream();
        byte expected[] = "FOO".getBytes();
        String input;
        input = "FOO";
        SuffixStream.MatchLength matchLength = stream.suffixLength(input.getBytes(),expected,input.getBytes().length);
        assertEquals("FOO should be a full match",3,matchLength.length());
        assertTrue(matchLength.fullMatch());
    }
    @Test
    public void suffixLength_partialMatch(){
        SuffixStream stream = new SuffixStream();
        byte expected[] = "FOO".getBytes();
        String input;
        input = "FO";
        SuffixStream.MatchLength matchLength = stream.suffixLength(input.getBytes(),expected,input.getBytes().length);
        assertEquals("FOO should be a full match",2,matchLength.length());
        assertFalse(matchLength.fullMatch());
    }
    @Test
    public void suffixLength_partialMatch2(){
        SuffixStream stream = new SuffixStream();
        byte expected[] = "FOO".getBytes();
        String input;
        input = "F";
        SuffixStream.MatchLength matchLength = stream.suffixLength(input.getBytes(),expected,input.getBytes().length);
        assertEquals("FOO should be a full match",1,matchLength.length());
        assertFalse(matchLength.fullMatch());
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
    public void consumer_fullMatch_executor_verifyThread(){
        ScheduledThreadPoolExecutor sfe = new ScheduledThreadPoolExecutor(2);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SuffixStream stream = new SuffixStream("",sfe);
        stream.setExecutorDelay(0);
        stream.addSuffix("FOO","FOO","");
        stream.addStream("baos",baos);
        StringBuilder sb = new StringBuilder();

        stream.addConsumer((s)->{sb.append(Thread.currentThread().getName());});
        try {
            stream.write("BOOFOO".getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assertNotEquals("current thread and consumer thread should not match",Thread.currentThread().getName(),sb.toString());
    }
    @Test
    public void consumer_fullMatch_executor(){
        ScheduledThreadPoolExecutor sfe = new ScheduledThreadPoolExecutor(2);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SuffixStream stream = new SuffixStream("",sfe);
        stream.addSuffix("FOO","FOO","");
        stream.addStream("baos",baos);
        AtomicBoolean called = new AtomicBoolean(false);

        stream.addConsumer((s)->{called.set(true);});

        try {
            stream.write("BOOFOO".getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assertTrue(called.get());
        assertEquals("baos == BOO","BOO",baos.toString());
    }

    @Test
    public void consumer_splitMatch(){
        SuffixStream stream = new SuffixStream();
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
