package io.hyperfoil.tools.qdup;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HostTest {


   @Test
   public void username_with_password(){
      Host h = Host.parse("username:pwd@hostname.with.domain");
      assertEquals("username",h.getUserName());
      assertEquals("pwd",h.getPassword());
      assertEquals("hostname.with.domain",h.getHostName());
      assertEquals("hostname",h.getShortHostName());
      assertEquals(Host.DEFAULT_PORT,h.getPort());
   }
}
