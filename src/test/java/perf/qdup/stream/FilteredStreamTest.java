package perf.qdup.stream;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class FilteredStreamTest {

    @Test
    public void prefixLength(){
        FilteredStream fs = new FilteredStream();

        byte expected[] = "FOO".getBytes();

        String input;
        input = "bar";
        assertEquals("FOO should not match any of bar",0,fs.prefixLength(input.getBytes(),expected,0,input.getBytes().length));
        input = "FOO";
        assertEquals("FOO should match the entire length",expected.length,fs.prefixLength(input.getBytes(),expected,0,input.getBytes().length));
        input = "F";
        assertEquals("FOO should match the entire length",input.getBytes().length,fs.prefixLength(input.getBytes(),expected,0,input.getBytes().length));

    }

    @Test
    public void partialMatch(){
        FilteredStream filteredStream = new FilteredStream();
        filteredStream.addFilter("filter","FOO");
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        filteredStream.addStream("bao",bao);
        try {
            filteredStream.write("BOOF".getBytes());
            filteredStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        String read = new String(bao.toByteArray());
        assertEquals("should filter prefix once","BOO",read);

    }

    @Test
    public void oneFilter(){
        FilteredStream filteredStream = new FilteredStream();
        filteredStream.addFilter("filter","FOO");
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        filteredStream.addStream("bao",bao);
        try {
            filteredStream.write("FOOFOOBAR".getBytes());
            filteredStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        String read = new String(bao.toByteArray());

        assertEquals("should filter prefix multiple times","BAR",read);
    }


    @Test
    public void severalFilters(){
        FilteredStream filteredStream = new FilteredStream();
        filteredStream.addFilter("foo","FOO");
        filteredStream.addFilter("bar","BAR");
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        filteredStream.addStream("bao",bao);
        try {
            filteredStream.write("FOOFOOBARBIZFOOBAR".getBytes());
            filteredStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        String read = new String(bao.toByteArray());
        assertEquals("should filter each occurrence of both filters","BIZ",read);
    }


    @Test
    public void filterAcrossWrites(){
        FilteredStream filteredStream = new FilteredStream();
        filteredStream.addFilter("foo","FOOD");
        filteredStream.addFilter("bar","BARN");
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        filteredStream.addStream("bao",bao);
        try {
            filteredStream.write("FOODB".getBytes());
            //filteredStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        String read = new String(bao.toByteArray());
        assertEquals("should not read yet because of partialMatch on bar","",read);

        try {
            filteredStream.write("ARNparty".getBytes());
            //filteredStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        read = new String(bao.toByteArray());
        assertEquals("should read party after match on BARN","party",read);
    }
}
