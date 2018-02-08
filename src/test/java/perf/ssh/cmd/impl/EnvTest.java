package perf.ssh.cmd.impl;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EnvTest {

    @Test
    public void onLine(){
        Env env = new Env();
        env.onLine("key=value=with=equal");
        Map<String,String> environment = env.getEnvironment();
        assertTrue("environment should contain key",environment.containsKey("key"));
        assertEquals("key should = value=with=equal","value=with=equal",environment.get("key"));
    }
}
