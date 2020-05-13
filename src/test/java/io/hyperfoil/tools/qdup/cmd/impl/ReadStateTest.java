package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.SpyContext;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ReadStateTest {


   @Test
   public void read_from_state(){
      SpyContext context = new SpyContext();
      context.getState().set("key","value");
      ReadState readState = new ReadState("${{key}}");
      readState.doRun("input",context);
      assertEquals("readState should call next with value","value",context.getNext());
   }
   @Test
   public void read_from_with(){
      SpyContext context = new SpyContext();
      context.getState().set("key","value");
      ReadState readState = new ReadState("${{key}}");
      readState.with("key","sneaky");
      readState.doRun("input",context);
      assertEquals("readState should call next with value","sneaky",context.getNext());
   }
   @Test
   public void read_missing_state(){
      SpyContext context = new SpyContext();
      context.getState().set("key","value");
      ReadState readState = new ReadState("${{missing}}");
      readState.doRun("input",context);
      assertFalse("readState should not have called next :["+context.getNext()+"]",context.hasNext());
      assertEquals("readState should call skip with input","input",context.getSkip());
   }
   @Test
   public void read_empty_state(){
      SpyContext context = new SpyContext();
      context.getState().set("key","");
      ReadState readState = new ReadState("${{key}}");
      readState.doRun("input",context);
      assertFalse("readState should not have called next :["+context.getNext()+"]",context.hasNext());
      assertEquals("readState should call skip with input","input",context.getSkip());
   }
}
