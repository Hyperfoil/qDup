package io.hyperfoil.tools.qdup;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;

public class LocalTest extends SshTestBase{


    @Test
    public void upload(){
        Host host = getHost();
        File toSend = null;
        File toRead = null;
        try {
            toSend = File.createTempFile("tmp","local");
            Files.write(toSend.toPath(),"foo".getBytes());

            Local local = new Local(null);

            local.upload(toSend.getPath(),"/tmp/destination.txt",host);


            toRead = new File("/tmp/destination.txt");


            String read = new String(Files.readAllBytes(toRead.toPath()));

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
