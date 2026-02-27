package io.hyperfoil.tools.qdup.stream;

import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static io.hyperfoil.tools.qdup.stream.MultiStream.logger;
import static org.junit.Assert.*;

public class EscapeFilteredStreamTest {


    private String filterPerCharacter(String input, boolean close) {
        EscapeFilteredStream fs = new EscapeFilteredStream();
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        fs.addStream("bao", bao);

        try {
            for (int i = 0; i < input.getBytes().length; i++) {
                fs.write(input.getBytes(), i, 1);
            }
            if (close) {
                fs.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new String(bao.toByteArray());
    }

    private String filter(String input) {
        return filter(input, false);
    }

    private String filter(String input, boolean close) {
        EscapeFilteredStream fs = new EscapeFilteredStream();
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        fs.addStream("bao", bao);

        try {
            fs.write(input.getBytes(), 0, input.getBytes().length);
            if (close) {
                fs.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new String(bao.toByteArray());
    }

    @Test
    public void single_character_write() {
        String input = "test input";
        String output = filterPerCharacter(input, true);
        assertEquals(input, output);
    }


    @Test
    public void issue_remove_shift_in() {
        String output = filterPerCharacter(Character.toString(15) + "o" + Character.toString(15) + "ne" + Character.toString(15), true);
        assertEquals("leading, trailing, and injected shift in should be removed", "one", output);
    }


    @Test
    public void issue_remove_shift_out() {
        String output = filterPerCharacter(Character.toString(14) + "o" + Character.toString(14) + "ne" + Character.toString(14), true);
        assertEquals("leading, trailing, and injected shift in should be removed", "one", output);
    }

    @Test
    public void issue31_duplicate_buffer() {
        //was seeing [[[[INFOOO]   - insead of [INFO] - because of escape sequences being split across buffer writes and flushes
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        EscapeFilteredStream stream = new EscapeFilteredStream();
        stream.addStream("baos", outputStream);

        try {
            //[[[[INFOOO]   -
            stream.write(new byte[]{91, 27, 91, 49});//[\u001b1;
            stream.write(new byte[]{59});//;
            stream.write(new byte[]{51});
            stream.write(new byte[]{52, 109});
            stream.write(new byte[]{73, 78});
            stream.write(new byte[]{70});
            stream.write(new byte[]{79, 27});
            stream.write(new byte[]{91});
            stream.write(new byte[]{109});
            stream.write(new byte[]{93});
            stream.write(new byte[]{32, 27});
            stream.write(new byte[]{91, 49});
            stream.write(new byte[]{109});
            stream.write(new byte[]{45});
        } catch (IOException e) {
            fail("exception writing to stream:" + e.getMessage());
            e.printStackTrace();
        }

        String output = new String(outputStream.toByteArray());
        assertEquals("control characters should be removed: ", "[INFO] -", output);


    }


    @Test
    public void filter_null() {
        String input = " \0\0\0\0 ";
        assertEquals("expect to remove null", "  ", filter(input));
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

        assertEquals("expect to remove the escape:","  ar",response);

    }


    @Test
    public void filter_tres(){
        String input = "uno\r\ndos\r\ntres\n";
        String output = filter(input);
        assertEquals("uno\r\ndos\r\ntres\n",output);
    }

    @Test
    public void filter_mid(){
        String input = "  \u001b[Kbar";
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



    @Test
    public void filter_zsh_hash_escape() {
        String input = "\0\0#\u001b[m\0\0foo\0\0#\u001b[m\0";
        String output = filter(input);
        assertEquals("expect to remove the escape:", "foo", output);
    }



    // Testing Jansi stream Integration
    @Test
    public void testJansiIntegration() {

        String input = "Hello\u001b=World\u001b[0;1m\u001b>";
        String expected = "HelloWorld";

        assertEquals("Jansi should strip all specialized and complex sequences",
                expected, filter(input));
    }

}
