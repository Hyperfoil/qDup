package io.hyperfoil.tools.qdup.stream;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class LineEmittingStreamTest {

    @Test
    public void buffering(){
        try(LineEmittingStream stream = new LineEmittingStream()){
            List<String> lines = new ArrayList<>();
            stream.addConsumer(line->lines.add(line));

            byte toWrite[] = new byte[10*1024];
            toWrite[10*1024-1] = 13;
            try{
                stream.write(toWrite,10,10);
                stream.write(toWrite,0,toWrite.length);
            }catch(ArrayIndexOutOfBoundsException e){
                fail(e.getMessage());
            }
        }catch (IOException e){
            fail(e.getMessage());
        }

    }
    @Test
    public void buffer_part_of_write(){
        try(LineEmittingStream stream = new LineEmittingStream()){
        List<String> lines = new ArrayList<>();
        stream.addConsumer(line->lines.add(line));

        try{
            stream.write("uno\ndos\ntr");
            stream.write("es\n");
        }catch (IOException e){
            fail(e.getMessage());
        }
        assertEquals("expect 3 lines\n"+lines,3,lines.size());
        assertEquals("lines[0]","uno",lines.get(0));
        assertEquals("lines[1]","dos",lines.get(1));
        assertEquals("lines[2]","tres",lines.get(2));
        }catch (IOException e){
            fail(e.getMessage());
        }


    }

    @Test
    public void buffer_flush(){
        try(LineEmittingStream stream = new LineEmittingStream()){
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
        }catch (IOException e){
            fail(e.getMessage());
        }
    }

    @Test
    public void write_multiple_lines(){
        try(LineEmittingStream stream = new LineEmittingStream()){
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
        }catch (IOException e){
                fail(e.getMessage());
            }
    }

    @Test
    public void testBufferShrinks(){
        try(LineEmittingStream stream = new LineEmittingStream()){
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
        }catch (IOException e){
                fail(e.getMessage());
            }
    }
}
