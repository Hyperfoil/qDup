package perf.qdup.stream;

import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class LineEmittingStreamTest {

    @Test
    public void testBufferShrinks(){
        LineEmittingStream stream = new LineEmittingStream();
        AtomicInteger emitCount = new AtomicInteger(0);
        stream.addConsumer((line)->{emitCount.incrementAndGet();});
        try {
            stream.write("foo".getBytes());
            stream.write("foo".getBytes());
            stream.write("foo".getBytes());

            assertEquals("buffer should contain 9 bytes",9,stream.getIndex());
            stream.write("\n".getBytes());
            assertEquals("emitCount should equal 1",1,emitCount.get());
            assertEquals("buffer should contain 0 bytes after emitting a line",0,stream.getIndex());
            stream.write("foo".getBytes());
            assertEquals("buffer should contain 3 bytes",3,stream.getIndex());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
