package io.hyperfoil.tools.qdup.stream;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.wildfly.common.Assert.assertFalse;

public class FilteredStreamTest {

    @Test
    public void prefixLength(){
        FilteredStream fs = new FilteredStream();

        byte expected[] = "FOO".getBytes();

        String input;
        input = "bar";
        MultiStream.MatchLength matchLength = fs.prefixLength(input.getBytes(),expected,0,input.getBytes().length);
        assertFalse(matchLength.fullMatch());
        assertEquals("FOO should not match any of bar",0,matchLength.length());
        input = "FOO";
        matchLength = fs.prefixLength(input.getBytes(),expected,0,input.getBytes().length);
        assertTrue(matchLength.fullMatch());
        assertEquals("FOO should match the entire length",expected.length,matchLength.length());
        input = "F";
        matchLength = fs.prefixLength(input.getBytes(),expected,0,input.getBytes().length);
        assertFalse(matchLength.fullMatch());
        assertEquals("FOO should match the entire length",input.getBytes().length,matchLength.length());

    }

    @Test
    public void prefixLength_injected_cr(){
        FilteredStream fs = new FilteredStream();
        fs.addInjectable((byte)'\r');
        byte expected[] = "FOO".getBytes();
        String input = "F\rO\rO\r";
        MultiStream.MatchLength matchLength = fs.prefixLength(input.getBytes(),expected,0,input.getBytes().length);
        assertTrue("prefix should be a full match",matchLength.fullMatch());
        assertEquals("prefix match length should include injected CR but not trailing CR",5,matchLength.length());
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
    public void filter_trailing_newlines(){
        FilteredStream filteredStream = new FilteredStream();
        filteredStream.addFilter("foo","FOO");
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        filteredStream.addStream("bao",bao);
        try {
            filteredStream.write("FOO\r\n".getBytes());
            //filteredStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        String read;
        read = new String(bao.toByteArray());
        assertEquals("should not read newlines","",read);
        String expected = "B\r\n";
        try {
            filteredStream.write(expected.getBytes());
            //filteredStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        read = new String(bao.toByteArray());
        assertEquals("should see expected output",expected,read);
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
