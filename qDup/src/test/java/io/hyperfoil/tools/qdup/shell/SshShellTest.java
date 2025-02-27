package io.hyperfoil.tools.qdup.shell;

import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.SecretFilter;
import io.hyperfoil.tools.qdup.SshTestBase;
import org.junit.Test;

import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.junit.Assert.*;

public class SshShellTest extends SshTestBase {


    @Test(timeout = 10_000)
    public void sh_without_connect(){
        Host host = getHost();
        AbstractShell shell = new SshShell(
                host,
                "",
                new ScheduledThreadPoolExecutor(2),
                new SecretFilter(),
                false
        );

        assertFalse("shell should not be open",shell.isOpen());
        assertFalse("shell should not be ready",shell.isReady());
        shell.connect();
        try {
            String dateResponse = shell.shSync("date +%s");
            assertNotNull("response should not be null",dateResponse);
            assertTrue("response should be a number: "+dateResponse,dateResponse.trim().matches("^\\d+$"));
        }catch(Exception e){
            fail("should not throw exception: "+e.getMessage());
        }
    }
}
