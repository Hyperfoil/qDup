package io.hyperfoil.tools.qdup;

import org.junit.Test;

import static org.junit.Assert.*;

public class HostTest {

   @Test
   public void pase_local_container(){
      Host h = Host.parse(Host.LOCAL+Host.CONTAINER_SEPARATOR+"redhat/ubi10");
      assertTrue(h.isLocal());
      assertTrue(h.isContainer());
      assertEquals("redhat/ubi10",h.getDefinedContainer());
   }

   @Test
   public void parse_windows_domain_username_password_hostname_port(){
      Host h = Host.parse("domain/username:pwd@hostname.with.domain:31337");
      assertEquals("domain/username",h.getUserName());
      assertEquals("pwd",h.getPassword());
      assertEquals("hostname.with.domain",h.getHostName());
      assertEquals("hostname",h.getShortHostName());
      assertEquals("port",31337,h.getPort());
   }
   @Test
   public void parse_username_password_hostname_port(){
      Host h = Host.parse("username:pwd@hostname.with.domain:31337");
      assertEquals("username",h.getUserName());
      assertEquals("pwd",h.getPassword());
      assertEquals("hostname.with.domain",h.getHostName());
      assertEquals("hostname",h.getShortHostName());
      assertEquals("port",31337,h.getPort());
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
      assertNotNull("host should not be null",h);
      assertEquals("username",h.getUserName());
      assertEquals("hostname",h.getHostName());
      assertEquals("hostname",h.getShortHostName());
   }



   @Test
   public void parse_invalid_missing_username(){
      Host h = Host.parse("hostname");
      assertNull("invalid host should be null",h);
   }
   @Test
   public void parse_image(){
      Host h = Host.parse("quay.io/foo/bar");
      assertNotNull("host should not be null",h);
      assertTrue("host should be local",h.isLocal());
      assertTrue("host should be containerized",h.isContainer());
      assertEquals("quay.io/foo/bar",h.getDefinedContainer());
   }
   @Test
   public void parse_local_image(){
      Host h = Host.parse(Host.LOCAL+Host.CONTAINER_SEPARATOR+"quay.io/foo/bar");
      assertNotNull("host should not be null",h);
      assertTrue("host should be local",h.isLocal());
      assertTrue("host should be containerized",h.isContainer());
      assertEquals("quay.io/foo/bar",h.getDefinedContainer());
   }
   @Test
   public void parse_remote_image(){
      Host h = Host.parse("foo@bar"+Host.CONTAINER_SEPARATOR+"quay.io/foo/bar");
      assertNotNull("host should not be null",h);
      assertFalse("host should not be local",h.isLocal());
      assertEquals("foo",h.getUserName());
      assertEquals("bar",h.getHostName());
      assertFalse("should not have a password",h.hasPassword());
      assertTrue("host should be containerized",h.isContainer());
      assertEquals("quay.io/foo/bar",h.getDefinedContainer());
   }


}
