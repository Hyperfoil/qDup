package io.hyperfoil.tools.qdup;

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SshSessionTest extends SshTestBase{

    static ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(4);

    @Test
    public void parallel_exec(){

        List<String> outputs = new ArrayList<>();
        SshSession sshSession = getSession(false);
        executor.submit(()->{
            sshSession.exec("echo 'first'",(out)->{
                outputs.add(out);
            });
        });
        executor.submit(()->{
            sshSession.exec("echo 'second'",(out)->{
                outputs.add(out);
            });
        });
        executor.submit(()->{
            sshSession.exec("echo 'third'",(out)->{
                outputs.add(out);
            });
        });
       try {
          Thread.sleep(5_000);
       } catch (InterruptedException e) {
          e.printStackTrace();
       }
       assertEquals("expect 3 values "+outputs,3,outputs.size());
    }

    @Test
    public void echo_dollar_pwd(){
        String userHome = System.getProperty("user.home");
        String currentDir = System.getProperty("user.dir");
        String setupCommand = "export FOO=\"foo\"  BAR=\"bar\"";

        SshSession sshSession = new SshSession(
                getHost(),
                userHome+"/.ssh/known_hosts",
                getIdentity(),
                null,
                5,
                setupCommand,
                executor,
           false
        );
        String foo = sshSession.shSync("echo \"pwd is: $(pwd)\"");
    }

    @Test
    public void setupCommand(){
        String userHome = System.getProperty("user.home");
        String currentDir = System.getProperty("user.dir");
        String setupCommand = "export FOO=\"foo\"  BAR=\"bar\"";
        SshSession sshSession = new SshSession(
                getHost(),
                userHome+"/.ssh/known_hosts",
                getIdentity(),
                null,
                5,
                setupCommand,
                executor,
           false
        );
        String foo = sshSession.shSync("echo $FOO");
        String bar = sshSession.shSync("echo $BAR");

        assertEquals("foo",foo);
        assertEquals("bar",bar);
    }

    @Test
    public void setupCmd_output(){
        String userHome = System.getProperty("user.home");
        String currentDir = System.getProperty("user.dir");
        SshSession sshSession = new SshSession(
                getHost(),
                userHome+"/.ssh/known_hosts",
                getIdentity(),
                null,
                5,
                "echo 'fooooo'",
                executor,
           false
        );
        assertTrue("SshSession should be open after init",sshSession.isOpen());
        String pwd = sshSession.shSync("pwd");
        assertTrue("pwd should start with / but was "+pwd,pwd.startsWith("/"));
    }
    @Test
    public void executor_sh_output(){
        String userHome = System.getProperty("user.home");
        String currentDir = System.getProperty("user.dir");
        SshSession sshSession = new SshSession(
                getHost(),
                userHome+"/.ssh/known_hosts",
                getIdentity(),
                null,
                5,
                "",
                executor,
           false
        );

        assertTrue("SshSession should be open after init",sshSession.isOpen());

        List<String> outputs = new LinkedList<>();
        sshSession.addShObserver("out",outputs::add);

        sshSession.sh("env");
        sshSession.sh("pwd");
        sshSession.sh("");//to wait for previous to complete

        Env env = new Env();
        env.loadBefore(outputs.get(0));

    }

    //using docker test container requires executor or export PS1 is releasing semaphore
    @Test
    public void testConnect_shSync(){
        String userHome = System.getProperty("user.home");
        String currentDir = System.getProperty("user.dir");
        SshSession sshSession = new SshSession(
                getHost(),
                userHome+"/.ssh/known_hosts",
                getIdentity(),
                null,
                5,
                "",
                executor,
           false
        );

        assertTrue("SshSession should be open after init",sshSession.isOpen());
        String pwdOutput = sshSession.shSync("pwd");

        //NOTE: expect userHome when using sshd but expect currentDir if using a TestServer
        //Test server is not working at the moment so we test for userHome
        //TODO expected home folder depends on target system, add to Base?
        //sometimes pwdOutput is empty string
        assertEquals("pwd should be the current working directory","/root",pwdOutput);
        sshSession.close();
        assertFalse("SshSession should be closed",sshSession.isOpen());
    }

    @Test
    public void ctrlC_release_permit(){
        SshSession session = getSession();
        executor.schedule(() -> {
            session.ctrlC();
        }, 1_000, TimeUnit.MILLISECONDS);
        assertEquals("expect 1 permit", 1, session.permits());
        session.sh("sleep 1h");//
        try {
            Thread.sleep(2_000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assertEquals("expect 1 permit", 1, session.permits());
    }

    //failing sometimes?
    @Test
    public void echo_PS1(){
            SshSession sshSession = new SshSession(getHost(),
               "/dev/null",
               getIdentity(),
               null,
               5,
               "",
               executor,
               false);
            String out = sshSession.shSync("echo \""+SshSession.PROMPT+"\"");
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            assertEquals("output should only be prompt",SshSession.PROMPT,out);
            assertEquals("one prompt permit expected",1,sshSession.permits());
            out = sshSession.shSync("echo foo");
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            assertEquals("output should only be foo","foo",out);
            assertEquals("one foo permit expected",1,sshSession.permits());
    }

}
