package perf.ssh.stream;

import org.junit.Test;

import java.io.IOException;

public class LineEmittingStreamTest {

    @Test
    public void testBufferShrinks(){
        LineEmittingStream stream = new LineEmittingStream();

        try {
            stream.write("foo".getBytes());
            stream.write("foo".getBytes());
            stream.write("foo".getBytes());
            System.out.println(stream.getBufferLength()+" "+ stream.getIndex());
            stream.write("\n".getBytes());
            System.out.println(stream.getBufferLength()+" "+ stream.getIndex());
            stream.write("foo".getBytes());
            System.out.println(stream.getBufferLength()+" "+ stream.getIndex());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
