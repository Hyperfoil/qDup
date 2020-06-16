package io.hyperfoil.tools.qdup;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class HostTest {


   @Test
   public void parse_username_password_hostname_port(){
      Host h = Host.parse("username:pwd@hostname.with.domain:31337");
      assertEquals("username",h.getUserName());
      assertEquals("pwd",h.getPassword());
      assertEquals("hostname.with.domain",h.getHostName());
      assertEquals("hostname",h.getShortHostName());
      assertEquals("port",31337,h.getPort());
      assertEquals(Host.DEFAULT_PORT,h.getPort());
   }

   @Test
   public void parse_username_password_hostname(){
      Host h = Host.parse("username:pwd@hostname.with.domain");
      assertEquals("username",h.getUserName());
      assertEquals("pwd",h.getPassword());
      assertEquals("hostname.with.domain",h.getHostName());
      assertEquals("hostname",h.getShortHostName());
      assertEquals(Host.DEFAULT_PORT,h.getPort());
   }

   @Test
   public void parse_username_hostname(){
      Host h = Host.parse("username@hostname");
      assertEquals("username",h.getUserName());
      assertEquals("hostname",h.getHostName());
      assertEquals("hostname",h.getShortHostName());
   }



   @Test
   public void parse_missing_username(){
      Host h = Host.parse("hostname");
      assertNull("invalid host should be null",h);
   }


}
