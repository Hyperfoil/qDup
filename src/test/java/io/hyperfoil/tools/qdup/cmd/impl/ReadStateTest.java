package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.SpyContext;
import org.junit.Test;

import static org.junit.Assert.*;

public class ReadStateTest {


   @Test
   public void run_getNext_returns_else(){
      SpyContext context = new SpyContext();
      context.getState().set("key","value");
      ReadState readState = new ReadState("${{missing}}");
      readState.onElse(Cmd.NO_OP());
      readState.doRun("input",context);
      assertEquals("read-state should call next with input","input",context.getNext());
      Cmd next = readState.getNext();
      assertNotNull("read-state next should return else when missing",next);
      assertTrue("next should be no-op",next instanceof Cmd.NO_OP);
   }
   @Test
   public void read_expression_true_from_state(){
      SpyContext context = new SpyContext();
      context.getState().set("key","value");
      ReadState readState = new ReadState("${{= '${{key}}' === 'value'}}");
      readState.doRun("input",context);
      assertEquals("readState should call next with value","true",context.getNext());
   }
   @Test
   public void read_expression_false_from_state(){
      SpyContext context = new SpyContext(); 
      context.getState().set("key","value");
      ReadState readState = new ReadState("${{= '${{key}}' === 'NOTvalue'}}");
      readState.doRun("input",context);
      assertEquals("readState should call next with value","false",context.getNext());
   }

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
