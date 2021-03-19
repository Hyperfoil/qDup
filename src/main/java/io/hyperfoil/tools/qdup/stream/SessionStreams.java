package io.hyperfoil.tools.qdup.stream;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.Consumer;

public class SessionStreams extends MultiStream {


   public EscapeFilteredStream getEscapeFilteredStream() {
      return escapeFilteredStream;
   }

   public SuffixStream getSuffixStream() {
      return suffixStream;
   }

   public SuffixStream getPromptStream() {
      return promptStream;
   }

   public LineEmittingStream getLineEmittingStream() {
      return lineEmittingStream;
   }

   public FilteredStream getFilteredStream() {
      return filteredStream;
   }

   public ByteArrayOutputStream getShStream() {
      return shStream;
   }

   private EscapeFilteredStream escapeFilteredStream = null;
   private SuffixStream suffixStream = null;
   private SuffixStream promptStream = null;
   private LineEmittingStream lineEmittingStream = null;
   private FilteredStream filteredStream = null;
   private ByteArrayOutputStream shStream = null;

   /* Stream hierarchy
    * escapeFilteredStream - removes bash escape sequences
    *    suffixStream - looks for substrings at the end of the write buffer (bash prompt)
    *       filteredStream - removes sequences from anywhere in the write buffer (the bash command)
    *          lineEmittingStream - sends each line (\n or \r\n) of the write buffer to listeners (watching commands)
    *          shStream - stores all of the write buffer for a command
    *       promptStream - watches for suffixes and sends a prompt response (Y/n, Ok?, ...)
    */
   public SessionStreams(String name, ScheduledThreadPoolExecutor executor){
      super(name);
      shStream = new ByteArrayOutputStream();
      escapeFilteredStream = new EscapeFilteredStream(name);
      filteredStream = new FilteredStream(name);
      suffixStream = new SuffixStream(name+"-suffix", executor);
      promptStream = new SuffixStream(name+"-prompt",null);
      lineEmittingStream = new LineEmittingStream(name);

      addStream("efs",escapeFilteredStream);

      escapeFilteredStream.addStream("semaphore", suffixStream);

      suffixStream.addStream("filtered", filteredStream);
      suffixStream.addStream("prompt-callback", promptStream);

      //move before opening connection or sending
      //was getting a ConcurrentModificationException with this after the setup sh() calls
      filteredStream.addStream("lines", lineEmittingStream);
      filteredStream.addStream("sh", shStream);

      filteredStream.addFilter("^C", new byte[]{0, 0, 0, 3});
      filteredStream.addFilter("echo-^C", "^C");
      filteredStream.addFilter("^D", new byte[]{0, 0, 0, 4});
      filteredStream.addFilter("echo-^D", "^D");
      filteredStream.addFilter("^P", new byte[]{0, 0, 0, 16});
      filteredStream.addFilter("^T", new byte[]{0, 0, 0, 20});
      filteredStream.addFilter("^X", new byte[]{0, 0, 0, 24});
      filteredStream.addFilter("^@", new byte[]{0, 0, 0});
   }

   public void write(String towrite) throws IOException {
      super.write(towrite.getBytes());
   }

   @Override
   public void write(int b) throws IOException {
      logger.debug(getName()+".write(int)="+b);
      super.write(b);
   }


   @Override
   public void write(byte b[], int off, int len) throws IOException {
      super.write(b,off,len);
      //escapeFilteredStream.write(b,off,len);
   }
   @Override
   public void flush() throws IOException{
      escapeFilteredStream.flush();
   }

   public void flushBuffer(){
      filteredStream.flushBuffer();
      lineEmittingStream.forceEmit();
   }

   @Override
   public void close() throws IOException {
      escapeFilteredStream.close();
      suffixStream.close();
   }

   public void addLineConsumer(Consumer<String> consumer){
      lineEmittingStream.addConsumer(consumer);
   }
   public void removeLineConsumer(Consumer<String> consumer){
      lineEmittingStream.removeConsumer(consumer);
   }

   @Override
   public void setName(String name){
      super.setName(name);
      escapeFilteredStream.setName(name);
      filteredStream.setName(name);
      lineEmittingStream.setName(name);
      suffixStream.setName(name+"-suffix");
      promptStream.setName(name+"-prompt");
   }
   public boolean hasTrace(){
      return escapeFilteredStream.hasStream("trace");
   }
   public void setTrace(String path) throws IOException{
      if(!hasTrace()){
         String rawTracePath = Files.createTempFile("qdup."+path,".raw.log").toAbsolutePath().toString();
         FileOutputStream rawTraceStream = new FileOutputStream(rawTracePath);
         String efsTracePath = Files.createTempFile("qdup."+path,".efs.log").toAbsolutePath().toString();
         FileOutputStream efsTraceStream = new FileOutputStream(efsTracePath);
         escapeFilteredStream.addStream("trace",efsTraceStream);
         addStream("trace",rawTraceStream);
      }
   }
   public OutputStream getTrace(){
      return escapeFilteredStream.getStream("trace");
   }

   public void setCommand(String command){
      filteredStream.addFilter("command",command,"");
   }

   public void trace(String output){
      if(hasTrace()){
         try {
            getTrace().write(output.getBytes());
         } catch (IOException e) {
            //e.printStackTrace();
         }
      }
   }
   public void setDelay(int delay){
      suffixStream.setExecutorDelay(delay);
   }
   public int getDelay(){
      return suffixStream.getExecutorDelay();
   }

   public void clearInline(){
      promptStream.clear();
      promptStream.clearConsumers();
   }
   public void addInlinePrompts(Set<String> prompt,Consumer<String> callback){
      prompt.forEach(promptStream::addSuffix);
      promptStream.addConsumer(callback);
   }
   public Set<String> getInlinePrompts(){
      return suffixStream.getSuffixes();
   }
   public void addPrompt(String prompt){
      addPrompt(prompt,prompt,"");
   }
   public void addPrompt(String name,String prompt,String replacement){
      suffixStream.addSuffix(name,prompt,replacement);
   }

   public void addPromptCallback(Consumer<String> callback){
      suffixStream.addConsumer(callback);
   }
   public void removePromptCallback(Consumer<String> callback){
      suffixStream.removeConsumer(callback);
   }

   public void reset(){
      lineEmittingStream.reset();
      shStream.reset();
   }

   public String currentOutput(){
      return shStream.toString();
   }
   public String tail(int lines){
      if(lines<=0){
         lines=1;
      }

      byte[] b= shStream.toByteArray();
      int idx = b.length;
      while(lines > 0 && idx > 0){
         idx--;
         if( b[idx] == '\n' ){
            lines--;
         }
      }
      return new String(b,idx,b.length-(idx));
   }
}
