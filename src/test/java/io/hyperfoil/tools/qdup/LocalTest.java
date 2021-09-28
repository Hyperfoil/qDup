package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.qdup.config.yaml.Parser;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class LocalTest extends SshTestBase{


    @Test
    public void getRemote_url_https(){
        Local local = new Local(null);
        String content = local.getRemote("https://raw.githubusercontent.com/Hyperfoil/qDup/master/src/main/resources/sample.yaml");
        assertNotNull(content);
        assertTrue(content,content.length() > 0);
    }

    @Test
    public void getRemote_url(){
        Local local = new Local(null);
        String content = local.getRemote("raw.githubusercontent.com/Hyperfoil/qDup/master/src/main/resources/sample.yaml");
        assertNotNull(content);
        assertTrue(content,content.length() > 0);
    }

    @Test
    public void upload(){
        Host host = getHost();
        File toSend = null;
        File toRead = null;
        try {
            toSend = File.createTempFile("tmp","local");
            toSend.deleteOnExit();
            Files.write(toSend.toPath(),"foo".getBytes());

            Local local = new Local(getBuilder().buildConfig(Parser.getInstance()));

            local.upload(toSend.getPath(),"/tmp/destination.txt",host);

            assertTrue("/tmp/destination.txt exists",exists("/tmp/destination.txt"));

            String read = readFile("/tmp/destination.txt");
            assertEquals("foo",read);

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(toSend !=null && toSend.exists()){
                toSend.delete();
            }
            if(toRead !=null && toRead.exists()){
                toRead.delete();
            }
        }
    }
}
