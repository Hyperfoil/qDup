package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.qdup.config.yaml.Parser;
import io.hyperfoil.tools.qdup.shell.AbstractShell;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class LocalTest extends SshTestBase{

    @Test
    public void pipelineSlit_nosplit(){
        List<List<String>> list = new ArrayList<List<String>>();
        Local.pipelineSplit("foobarbiz",list);
        assertEquals("list should have 1 entry",1,list.size());
        List<String> pipe = list.get(0);
        assertEquals("pipeline[0] should have 1 entry",1,pipe.size());
        String entry = pipe.get(0);
        assertEquals("pipeline[0][0] should be full input","foobarbiz",entry);
    }
    @Test
    public void pipelineSlit_space_split(){
        List<List<String>> list = new ArrayList<List<String>>();
        Local.pipelineSplit("foo bar",list);
        assertEquals("list should have 1 entry",1,list.size());
        List<String> pipe = list.get(0);
        assertEquals("pipeline[0] should have 2 entries: "+pipe,2,pipe.size());
        assertEquals("pipeline[0][0] should be full input","foo",pipe.get(0));
        assertEquals("pipeline[0][1] should be full input","bar",pipe.get(1));
    }
    @Test
    public void pipelineSlit_pipe_spaced_split(){
        List<List<String>> list = new ArrayList<List<String>>();
        Local.pipelineSplit("foo | bar",list);
        assertEquals("list should have 2 entries",2,list.size());
        List<String> pipe = list.get(0);
        assertEquals("pipeline[0]entries: "+pipe,1,pipe.size());
        assertEquals("pipeline[0][0] should be full input","foo",pipe.get(0));
        pipe = list.get(1);
        assertEquals("pipeline[1]entries: "+pipe,1,pipe.size());
        assertEquals("pipeline[1][0] should be full input","bar",pipe.get(0));
    }
    @Test
    public void pipelineSlit_pipe_no_space_split(){
        List<List<String>> list = new ArrayList<List<String>>();
        Local.pipelineSplit("foo|bar",list);
        assertEquals("list should have 1 entry",2,list.size());
        List<String> pipe = list.get(0);
        assertEquals("pipeline[0] entries: "+pipe,1,pipe.size());
        assertEquals("pipeline[0][0] should be full input","foo",pipe.get(0));
        pipe = list.get(1);
        assertEquals("pipeline[1] entries: "+pipe,1,pipe.size());
        assertEquals("pipeline[1][0] should be full input","bar",pipe.get(0));
    }
    @Test
    public void pipelineSlit_quoted_space(){
        List<List<String>> list = new ArrayList<List<String>>();
        Local.pipelineSplit("foo \"bar bar\"",list);
        assertEquals("list should have 1 entry",1,list.size());
        List<String> pipe = list.get(0);
        assertEquals("pipeline[0] should have 2 entries: "+pipe,2,pipe.size());
        assertEquals("pipeline[0][0] should be full input","foo",pipe.get(0));
        assertEquals("pipeline[0][1] should be full input","bar bar",pipe.get(1));
    }

    //TODO load from a url in the container to pass without internet connection
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
    public void remote_ssh_filesize(){
        Host host = getHost();
        File toSend = null;
        File toRead = null;
        try {
            toSend = File.createTempFile("tmp","local");
            toSend.deleteOnExit();
            Files.write(toSend.toPath(),"foobarbizbuz".getBytes());

            Local local = new Local(getBuilder().buildConfig(Parser.getInstance()));

            local.upload(toSend.getPath(),"/tmp/destination.txt",host);
            assertTrue("/tmp/destination.txt exists",exists("/tmp/destination.txt"));

            long response = local.remoteFileSize("/tmp/destination.txt",host);
            assertTrue("should ready back more than 0 bytes but was "+response,response > 0);

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
    @Test
    public void local_filesize(){
        Host host = new Host();
        File toSend = null;
        File toRead = null;
        try {
            toSend = File.createTempFile("tmp","local");
            toSend.deleteOnExit();

            Files.write(toSend.toPath(),"foobarbizbuz".getBytes());

            Local local = new Local(getBuilder().buildConfig(Parser.getInstance()));
            long response = local.remoteFileSize(toSend.getPath(),host);
            assertTrue("should read back more than 0 bytes but was "+response,response > 0);

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
    @Test
    public void remote_ssh_upload(){
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
    @Test
    public void local_upload(){
        Host host = new Host();//creates a local host
        assertTrue(host.isLocal());
        File toSend = null;
        File toRead = null;
        try {
            toSend = File.createTempFile("tmp","local");
            toSend.deleteOnExit();
            toRead = File.createTempFile("tmp","local");
            toRead.deleteOnExit();

            Files.write(toSend.toPath(),"foo".getBytes());
            Local local = new Local(getBuilder().buildConfig(Parser.getInstance()));

            local.upload(toSend.getPath(),toRead.getPath(),host);
            assertTrue(toRead.getPath()+" should exist",toRead.exists());

            String read = readFile(toRead.toPath());
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
    @Test
    public void local_download(){
        Host host = new Host();//creates a local host
        assertTrue(host.isLocal());
        File toSend = null;
        File toRead = null;
        try {
            toSend = File.createTempFile("tmp","local");
            toSend.deleteOnExit();
            toRead = File.createTempFile("tmp","local");
            toRead.deleteOnExit();

            Files.write(toSend.toPath(),"foo".getBytes());
            Local local = new Local(getBuilder().buildConfig(Parser.getInstance()));

            local.download(toSend.getPath(),toRead.getPath(),host);
            assertTrue(toRead.getPath()+" should exist",toRead.exists());

            String read = readFile(toRead.toPath());
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

    @Test
    public void container_download(){
        Host host = Host.parse("quay.io/fedora/fedora");
        //host.setStartContainer(List.of("podman run -it ${{host.container}} /bin/bash"));
//        host.setConnectShell(List.of("podman run -it ${{host.container}} /bin/bash"));
//        host.setStartContainer(List.of());
        AbstractShell shell = AbstractShell.getShell(
                host,
                new ScheduledThreadPoolExecutor(2),
                new SecretFilter(),
                false
        );
        String response = shell.shSync("echo 'foo' > /tmp/foo.txt");
        response = shell.shSync("ls -al /tmp/foo.txt");
        File toRead = null;
        Local local = new Local(getBuilder().buildConfig(Parser.getInstance()));
        try{
            toRead = File.createTempFile("tmp","local");
            //toRead.deleteOnExit();
            local.download("/tmp/foo.txt",toRead.getPath(),host);
            assertTrue("downloaded file should exist",toRead.exists());
            String read = readFile(toRead.toPath());
            assertEquals("foo",read);

        } catch (IOException e) {
            fail(e.getMessage());
        } finally {
//            if(toRead !=null && toRead.exists()){
//                toRead.delete();
//            }
        }
    }
    @Test
    public void remote_ssh_download(){
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

            toRead = File.createTempFile("tmp","local");
            toRead.deleteOnExit();

            local.download("/tmp/destination.txt",toRead.getPath(),host);

            read = readFile(toRead.toPath());
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
