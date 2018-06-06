package perf.qdup.stream;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EscapeFilteredStreamTest {

    private String filter(String input){
        EscapeFilteredStream fs = new EscapeFilteredStream();
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        fs.addStream("bao",bao);

        try {
            fs.write(input.getBytes(),0,input.getBytes().length);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new String(bao.toByteArray());
    }

    @Test
    public void no_filter(){
        String input="foo";
        assertEquals("input should equal output",input,filter(input));
    }
    @Test
    public void buffer_all(){
        String input="\u001b[0;1;0";
        assertEquals("input should all be buffered","",filter(input));
    }
    @Test
    public void buffer_tail(){
        String input="foo\u001b[";
        assertEquals("should only buffer tail","foo",filter(input));
    }
    @Test
    public void filter_K_noDigit(){
        String input="foo\u001b[K";
        assertEquals("should only be foo","foo",filter(input));
    }
    @Test
    public void filter_J_noDigit(){
        String input="foo\u001b[J";
        assertEquals("should only be foo","foo",filter(input));
    }
    @Test
    public void filter_all(){
        String input="\u001b[0m";
        assertEquals("all input should be filtered","",filter(input));
    }
    @Test
    public void filter_all_complex(){
        String input="\u001b[0;1;2;3m";
        assertEquals("all input should be filtered","",filter(input));

    }
    @Test
    public void filter_head(){
        String input="\u001b[0mm";
        assertEquals("header should be filtered","m",filter(input));
    }
    @Test
    public void filter_tail(){
        String input="m\u001b[0m";
        assertEquals("tail should be filtered","m",filter(input));
    }

    @Test
    public void escapeLength(){
        EscapeFilteredStream fs = new EscapeFilteredStream();

        String input;
        input = "bar";
        assertEquals("no escape found",0,fs.escapeLength(input.getBytes(),0,input.getBytes().length));
        input = "\u001b[";
        assertEquals("partial match header",2,fs.escapeLength(input.getBytes(),0,input.getBytes().length));
        input = "\u001b[0";
        assertEquals("partial match header digit",3,fs.escapeLength(input.getBytes(),0,input.getBytes().length));
        input = "\u001b[0;";
        assertEquals("partial match ;",4,fs.escapeLength(input.getBytes(),0,input.getBytes().length));
        input = "\u001b[0;1";
        assertEquals("partial match ; digit",5,fs.escapeLength(input.getBytes(),0,input.getBytes().length));
        input = "\u001b[0m";
        assertEquals("full match",4,fs.escapeLength(input.getBytes(),0,input.getBytes().length));
        input = "x";
        assertEquals("no match",0,fs.escapeLength(input.getBytes(),0,input.getBytes().length));
        input = "\u001bx";
        assertEquals("no match",0,fs.escapeLength(input.getBytes(),0,input.getBytes().length));
        input = "\u001b[x";
        assertEquals("no match",0,fs.escapeLength(input.getBytes(),0,input.getBytes().length));
        input = "\u001b[0x";
        assertEquals("no match",0,fs.escapeLength(input.getBytes(),0,input.getBytes().length));
        input = "\u001b[0;x";
        assertEquals("no match",0,fs.escapeLength(input.getBytes(),0,input.getBytes().length));

    }

    @Test
    public void isEscaped(){
        EscapeFilteredStream fs = new EscapeFilteredStream();
        String input;
        input = "x";
        assertFalse("not escaped",fs.isEscaped(input.getBytes(),0,input.getBytes().length));
        input = "\u001b";
        assertFalse("not escaped",fs.isEscaped(input.getBytes(),0,input.getBytes().length));
        input = "\u001b[";
        assertFalse("not escaped",fs.isEscaped(input.getBytes(),0,input.getBytes().length));
        input = "\u001b[0";
        assertFalse("not escaped",fs.isEscaped(input.getBytes(),0,input.getBytes().length));
        input = "\u001b[0;";
        assertFalse("not escaped",fs.isEscaped(input.getBytes(),0,input.getBytes().length));
        input = "\u001b[0m";
        assertTrue("escaped",fs.isEscaped(input.getBytes(),0,input.getBytes().length));
        input = "\u001b[0;1m";
        assertTrue("escaped",fs.isEscaped(input.getBytes(),0,input.getBytes().length));
    }


}
