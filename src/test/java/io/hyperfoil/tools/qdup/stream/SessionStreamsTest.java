package io.hyperfoil.tools.qdup.stream;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SessionStreamsTest {


   private SessionStreams getStreams(){
      SessionStreams sessionStreams = new SessionStreams("test",new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors()/2,new ThreadFactory() {
         AtomicInteger count = new AtomicInteger(0);
         @Override
         public Thread newThread(Runnable runnable) {
            return new Thread(runnable,"schedule-"+count.getAndAdd(1));
         }
      }));
      return sessionStreams;
   }

   private void write(SessionStreams sessionStreams, String toWrite) throws IOException {
      sessionStreams.getEscapeFilteredStream().write(toWrite.getBytes(),0,toWrite.getBytes().length);
   }

   @Test
   public void line_emitting_post_filter(){
      SessionStreams sessionStreams = getStreams();

      List<String> emitted = new ArrayList<>();
      sessionStreams.getLineEmittingStream().addConsumer(emitted::add);

      sessionStreams.getFilteredStream().addFilter("command","command","");
      try{
         write(sessionStreams,"command");
         write(sessionStreams,"\r\n");
         //works
//         write(sessionStreams,"command\r\n");
         write(sessionStreams,"foo\r\n");
         write(sessionStreams,"bar\r\n");
      }catch(IOException e){
         fail(e.getMessage());
      }


      assertEquals("expect 2 entries in array: "+emitted.toString(),2,emitted.size());
   }
}
