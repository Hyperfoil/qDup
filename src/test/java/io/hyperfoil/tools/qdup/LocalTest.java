package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.qdup.config.yaml.Parser;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LocalTest extends SshTestBase{


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
