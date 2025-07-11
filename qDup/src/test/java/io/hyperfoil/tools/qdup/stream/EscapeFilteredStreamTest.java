package io.hyperfoil.tools.qdup.stream;

import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.Assert.*;

public class EscapeFilteredStreamTest {


    private String filterPerCharacter(String input,boolean close){
        EscapeFilteredStream fs = new EscapeFilteredStream();
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        fs.addStream("bao",bao);

        try {
            for(int i=0; i<input.getBytes().length; i++) {
                fs.write(input.getBytes(), i, 1);
            }
            if(close) {
                fs.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new String(bao.toByteArray());
    }
    private String filter(String input){
        return filter(input,false);
    }
    private String filter(String input,boolean close){
        EscapeFilteredStream fs = new EscapeFilteredStream();
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        fs.addStream("bao",bao);

        try {
            fs.write(input.getBytes(),0,input.getBytes().length);
            if(close) {
                fs.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new String(bao.toByteArray());
    }

    @Test
    public void single_character_write(){
        String input = "test input";
        String output = filterPerCharacter(input,true);
        assertEquals(input,output);
    }

    @Test
    public void single_char_length(){
        String input = "\u001b";
        EscapeFilteredStream fs = new EscapeFilteredStream();

        try{
            int length = fs.escapeLength(input.getBytes(),0,input.getBytes().length);

            assertEquals("length should match ",1,length);
        }catch(Exception e){
            fail("Exception trying to get length of single char:"+e.getMessage());
        }
    }

    @Test
    public void issue_remove_shift_in(){
        String output = filterPerCharacter(Character.toString(15)+"o"+Character.toString(15)+"ne"+Character.toString(15),true);
        assertEquals("leading, trailing, and injected shift in should be removed", "one",output);
    }
    @Test
    public void issue_remove_shift_out(){
        String output = filterPerCharacter(Character.toString(14)+"o"+Character.toString(14)+"ne"+Character.toString(14),true);
        assertEquals("leading, trailing, and injected shift in should be removed", "one",output);
    }

    @Test
    public void issue31_duplicate_buffer(){
        //was seeing [[[[INFOOO]   - insead of [INFO] - because of escape sequences being split across buffer writes and flushes
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        EscapeFilteredStream stream = new EscapeFilteredStream();
        stream.addStream("baos",outputStream);

        try {
            //[[[[INFOOO]   -
            stream.write(new byte[]{91,27,91,49});
            stream.write(new byte[]{59});
            stream.write(new byte[]{51});
            stream.write(new byte[]{52,109});
            stream.write(new byte[]{73,78});
            stream.write(new byte[]{70});
            stream.write(new byte[]{79,27});
            stream.write(new byte[]{91});
            stream.write(new byte[]{109});
            stream.write(new byte[]{93});
            stream.write(new byte[]{32,27});
            stream.write(new byte[]{91,49});
            stream.write(new byte[]{109});
            stream.write(new byte[]{45});
        } catch (IOException e) {
            fail("exception writing to stream:"+e.getMessage());
            e.printStackTrace();
        }

        String output = new String(outputStream.toByteArray());
        assertEquals("control characters should be removed: ","[INFO] -",output);


    }

    @Test
    public void near_miss_mid(){
        String input = "  \u001b[bar";
        EscapeFilteredStream fs = new EscapeFilteredStream();
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        fs.addStream("bao",bao);

        try {
            fs.write(input.getBytes(),0,input.getBytes().length);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String response = new String(bao.toByteArray());

        assertEquals("expect to remove the escape:","  \u001b[bar",filter(input));

    }

    @Test
    public void filter_mid(){
        String input = "  \u001b[Kbar";

        EscapeFilteredStream fs = new EscapeFilteredStream();
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        fs.addStream("bao",bao);

        try {
            fs.write(input.getBytes(),0,input.getBytes().length);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String response = new String(bao.toByteArray());

        assertEquals("expect to remove the escape:","  bar",filter(input));
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
    public void filter_preexec(){
        String input="\u001b]777;preexec\u001b\\foo";
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
    public void filter_questionMark_1h(){
        String input="\u001b[?1h";
        assertEquals("all input should be filtered","",filter(input));
    }
    @Test
    public void filter_equal(){
        String input="\u001b=";
        assertEquals("all input should be filtered","",filter(input));
    }
    @Test
    public void filter_greaterThan(){
        String input="\u001b>";
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

    @Test @Ignore //TODO does this occur in terminals? need to change looping if it does
    public void filter_escape_inside_escape(){
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        EscapeFilteredStream stream = new EscapeFilteredStream();
        stream.addStream("baos",outputStream);

        try {
            stream.write("x\u001b[".getBytes());
            stream.write("\u001b[0m".getBytes());
            stream.write("0mxx".getBytes());
        } catch (IOException e) {
                fail("exception writing to stream:"+e.getMessage());
                e.printStackTrace();
        }

        String output = new String(outputStream.toByteArray());
            assertEquals("nested escapes should be filtered","xxx",output);
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
        input = "\u001b[K";
        assertEquals("full match",input.getBytes().length,fs.escapeLength(input.getBytes(),0,input.getBytes().length));

        input = "x";
        assertEquals("no match",0,fs.escapeLength(input.getBytes(),0,input.getBytes().length));
        input = "\u001bx";
        assertEquals("no match",0,fs.escapeLength(input.getBytes(),0,input.getBytes().length));
        input = "\u001b[x";
        assertEquals("no match",0,fs.escapeLength(input.getBytes(),0,input.getBytes().length));
        input = "\u001b[0x";
        assertEquals("no match",0,fs.escapeLength(input.getBytes(),0,input.getBytes().length));

//        input = "\u001b[0;x";//this was wrong, it is actually a full partial match to title setting
//        assertEquals("no match",0,fs.escapeLength(input.getBytes(),0,input.getBytes().length));

        //Xterm terminal title
        byte[] bytes = new byte[]{  27, 93, 48, 59, 64, 50,100,101, 56, 52, 98, 50, 99, 50, 48, 51, 53, 58, 47,  7};
        assertEquals("full match",bytes.length,fs.escapeLength(bytes,0,bytes.length));
        bytes = new byte[]{  27, 93, 48, 59, 64, 50,100,101, 56, 52, 98, 50, 99, 50, 48, 51, 53, 58, 47,  7, 69};
        assertEquals("match all but last character",bytes.length-1,fs.escapeLength(bytes,0,bytes.length));
        bytes = new byte[]{  27, 93, 48, 59, 64, 50,100,101, 56, 52, 98, 50, 99, 50, 48, 51, 53, 58, 47};
        assertEquals("full match",bytes.length,fs.escapeLength(bytes,0,bytes.length));

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
        byte[] bytes = new byte[]{  27, 93, 48, 59, 64, 50,100,101, 56, 52, 98, 50, 99, 50, 48, 51, 53, 58, 47,  7};
        assertTrue("escaped",fs.isEscaped(bytes,0,bytes.length));
        bytes = new byte[]{  27, 93, 48, 59, 64, 50,100,101, 56, 52, 98, 50, 99, 50, 48, 51, 53, 58, 47,  7, 69};
        assertTrue("escaped",fs.isEscaped(bytes,0,bytes.length));
        bytes = new byte[]{  27, 93, 48, 59, 64, 50,100,101, 56, 52, 98, 50, 99, 50, 48, 51, 53, 58, 47};
        assertFalse("not escaped",fs.isEscaped(bytes,0,bytes.length));
    }


}
