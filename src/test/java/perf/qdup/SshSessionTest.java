package perf.qdup;

import org.junit.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SshSessionTest extends SshTestBase{

    @Test
    public void setupCommand(){
        String userHome = System.getProperty("user.home");
        String currentDir = System.getProperty("user.dir");
        String setupCommand = "export FOO=\"foo\"  BAR=\"bar\"";
        SshSession sshSession = new SshSession(
                getHost(),
                userHome+"/.ssh/known_hosts",
                userHome+"/.ssh/id_rsa",
                null,
                5,
                setupCommand,
                null
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
                userHome+"/.ssh/id_rsa",
                null,
                5,
                "echo 'fooooo'",
                null
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
                userHome+"/.ssh/id_rsa",
                null,
                5,
                "",
                new ScheduledThreadPoolExecutor(5)
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

    @Test
    public void testConnect_shSync(){
        String userHome = System.getProperty("user.home");
        String currentDir = System.getProperty("user.dir");
        SshSession sshSession = new SshSession(
                getHost(),
                userHome+"/.ssh/known_hosts",
                userHome+"/.ssh/id_rsa",
                null,
                5,
                "",
                null
        );
        assertTrue("SshSession should be open after init",sshSession.isOpen());
        String pwdOutput = sshSession.shSync("pwd");

        //NOTE: expect userHome when using sshd but expect currentDir if using a TestServer
        //Test server is not working at the moment so we test for userHome
        assertEquals("pwd should be the current working directory",userHome,pwdOutput);
        sshSession.close();
        assertFalse("SshSession should be closed",sshSession.isOpen());
    }

    @Test
    public void ctrlC_release_permit(){
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(4);
        SshSession session = getSession();
        executor.schedule(()->{
            System.out.println("ctrlC");
            session.ctrlC();
        },1_000,TimeUnit.MILLISECONDS);
        session.sh("sleep 1h");//
        try {
            Thread.sleep(2_000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assertEquals("expect 1 permit",1,session.permits());




    }

    @Test
    public void echo_PS1(){
        SshSession sshSession = new SshSession(getHost());

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
