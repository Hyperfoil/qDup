package io.hyperfoil.tools.qdup.shell;

import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.RunTest;
import io.hyperfoil.tools.qdup.SecretFilter;
import io.hyperfoil.tools.qdup.SshTestBase;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.junit.Assert.*;

public class LocalShellTest extends SshTestBase {
    @Test
    public void sh_without_connect(){
        Host host = new Host();
        AbstractShell shell = new LocalShell(
            host,
            "",
            new ScheduledThreadPoolExecutor(2),
            new SecretFilter(),
            false
        );
        try {
            String response = shell.shSync("echo 'foo' > /tmp/foo.txt");
        }catch(Exception e){
            fail("should not throw exception: "+e.getMessage());
        }
    }

    @Test
    public void sh_createFile(){
        Host host = new Host();
        AbstractShell shell = new LocalShell(
                host,
                "",
                new ScheduledThreadPoolExecutor(2),
                new SecretFilter(),
                false
        );
        boolean connected = shell.connect();
        if(!connected){
            fail("failed to connect shell");
        }
        assertTrue("shell should be open",shell.isOpen());
        assertTrue("shell should be ready",shell.isReady());

        String exec = shell.shSync("mktemp");
        try {
            Thread.sleep(1_000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        File f = new File(exec);
        assertTrue("path should exist on local system: "+exec,f.getAbsoluteFile().exists());
        f.delete();
        assertFalse("path should be cleaned up by File.delete",f.getAbsoluteFile().exists());
    }
    @Test
    public void exec(){
        Host host = new Host();
        AbstractShell shell = new LocalShell(
                host,
                "",
                new ScheduledThreadPoolExecutor(2),
                new SecretFilter(),
                false
        );
        boolean connected = shell.connect();
        if(!connected){
            fail("failed to connect shell");
        }
        assertTrue("shell should be open",shell.isOpen());
        assertTrue("shell should be ready",shell.isReady());
        String response = shell.execSync("date +%s");
        assertNotNull("should get a response from shell",response);
        //need trim because we are getting a newline (or CR) after the output
        assertTrue("response should numbers: ["+response+"]",response.trim().matches("^[0-9]+$"));
    }

    @Test
    public void exec_during_sh(){
        Host host = new Host();
        AbstractShell shell = new LocalShell(
                host,
                "",
                new ScheduledThreadPoolExecutor(2),
                new SecretFilter(),
                false
        );
        boolean connected = shell.connect();
        if(!connected){
            fail("failed to connect shell");
        }
        assertTrue("shell should be open",shell.isOpen());
        assertTrue("shell should be ready",shell.isReady());
        StringBuilder shResponse = new StringBuilder();
        shell.sh("ping 127.0.0.1",(response)->{
            shResponse.append(response);
        });
        String dateResponse = shell.execSync("date +%s");
        shell.ctrlC();//end the sh
        try {//wait for the terminal prompt
            Thread.sleep(1_00);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


}
