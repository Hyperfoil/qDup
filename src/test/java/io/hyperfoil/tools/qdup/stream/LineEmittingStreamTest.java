package io.hyperfoil.tools.qdup.stream;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class LineEmittingStreamTest {

    @Test
    public void buffer_flush(){
        LineEmittingStream stream = new LineEmittingStream();
        List<String> lines = new ArrayList<>();

        stream.addConsumer(line->lines.add(line));

        stream.write("\n".getBytes(),0,1);

        assertEquals("1 emit",1,lines.size());
        assertEquals("empty string","",lines.get(0));

        stream.write("1234567890".getBytes(),0,10);

        assertEquals("1 emit:"+lines,1,lines.size());

        stream.write("1234567890\n".getBytes(),0,11);

        assertEquals("2 emit:"+lines,2,lines.size());
        assertEquals("emit two writes together","12345678901234567890",lines.get(1));

        stream.write("\n".getBytes(),0,1);
        assertEquals("3 emit",3,lines.size());
        assertEquals("empty string","",lines.get(2));

    }

    @Test
    public void write_multiple_lines(){
        LineEmittingStream stream = new LineEmittingStream();
        AtomicInteger emitCount = new AtomicInteger(0);
        stream.addConsumer((line)->{
            emitCount.incrementAndGet();
        });

        try {
            stream.write("1\n2\n3".getBytes());
            stream.flush();
            stream.close();//emits the remaining "3"
            assertEquals("line count",3,emitCount.get());

        } catch (IOException e) {
            e.printStackTrace();
            fail("Exception: "+e.getMessage());
        }
    }

    @Test
    public void testBufferShrinks(){
        LineEmittingStream stream = new LineEmittingStream();
        AtomicInteger emitCount = new AtomicInteger(0);
        stream.addConsumer((line)->{emitCount.incrementAndGet();});
        try {
            stream.write("foo".getBytes());
            stream.write("foo".getBytes());
            stream.write("foo".getBytes());

            assertEquals("buffer should contain 9 bytes",9,stream.getWriteIndex());
            stream.write("\n".getBytes());
            assertEquals("emitCount should equal 1",1,emitCount.get());
            assertEquals("buffer should contain 0 bytes after emitting a line",0,stream.getWriteIndex());
            stream.write("foo".getBytes());
            assertEquals("buffer should contain 3 bytes",3,stream.getWriteIndex());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
