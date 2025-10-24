package io.hyperfoil.tools.qdup.stream;

import io.hyperfoil.tools.yaup.AsciiArt;
import io.hyperfoil.tools.yaup.json.Json;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    *          shStream - stores the write buffer for a command
    *       promptStream - watches for suffixes and sends a prompt response (Y/n, Ok?, ...)
    */

   private String traceName;

   public SessionStreams(String name, ScheduledThreadPoolExecutor executor){
      super(name);
      shStream = new ByteArrayOutputStream();
      escapeFilteredStream = new EscapeFilteredStream(name+"-efs");
      filteredStream = new FilteredStream(name+"-fs");
      suffixStream = new SuffixStream(name+"-suffix", executor);
      promptStream = new SuffixStream(name+"-prompt",null);
      lineEmittingStream = new LineEmittingStream(name+"-les");

      addStream("efs",escapeFilteredStream);

      escapeFilteredStream.addStream("semaphore", suffixStream);

      suffixStream.addStream("filtered", filteredStream);
      suffixStream.addStream("prompt-callback", promptStream);
      filteredStream.addInjectable((byte)'\r'); //for column width wrapping
      filteredStream.addObserver(this::clearCommand);

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
      logger.warn(getName()+".write(int)="+b);
      super.write(b);
   }


   @Override
   public void write(byte b[], int off, int len) throws IOException {
      if(hasTrace()){
         trace("[>>qdup["+off+","+len+"]--]\n");
         trace(MultiStream.printByteCharacters(b,off,len));
         trace("\n[<<qdup["+off+","+len+"]--]\n");
      }
      super.write(b,off,len);
      //escapeFilteredStream.write(b,off,len);
   }
   public String jsonBuffers(){
      Json rtrn = new Json();
      rtrn.set("escapeFiltered",new Json());
      rtrn.getJson("escapeFiltered").set("buffer",escapeFilteredStream.getBuffered().replaceAll("[\r\n]","\\\\n"));
      rtrn.getJson("escapeFiltered").set("suffixStream",new Json());
      rtrn.getJson("escapeFiltered").getJson("suffixStream").set("buffer",suffixStream.getBuffered().replaceAll("[\r\n]","\\\\n"));
      rtrn.getJson("escapeFiltered").getJson("suffixStream").set("filteredStream",new Json());
      rtrn.getJson("escapeFiltered").getJson("suffixStream").getJson("filteredStream").set("buffer",filteredStream.getBuffered().replaceAll("[\r\n]","\\\\n"));
      rtrn.getJson("escapeFiltered").getJson("suffixStream").getJson("filteredStream").set("lineEmitting",new Json());
      rtrn.getJson("escapeFiltered").getJson("suffixStream").getJson("filteredStream").getJson("lineEmitting").set("buffer",lineEmittingStream.getBuffered().replaceAll("[\r\n]","\\\\n"));
      rtrn.getJson("escapeFiltered").getJson("suffixStream").getJson("filteredStream").set("shStream",new Json());
      rtrn.getJson("escapeFiltered").getJson("suffixStream").getJson("filteredStream").getJson("shStream").set("buffer",shStream.toString().replaceAll("[\r\n]","\\\\n"));
      rtrn.getJson("escapeFiltered").getJson("suffixStream").set("promptStream",new Json());
      rtrn.getJson("escapeFiltered").getJson("suffixStream").getJson("promptStream").set("buffer",promptStream.getBuffered().replaceAll("[\r\n]","\\\\n"));

      return rtrn.toString();
   }
   public String printBuffers(){
      StringBuilder sb = new StringBuilder();
      sb.append(getName()+"\n");
      sb.append("escapeFiltered "+escapeFilteredStream.getBuffered().replaceAll("[\r\n]","\\\\n")+"_\n");
      sb.append("└ suffixStream "+suffixStream.getBuffered().replaceAll("[\r\n]","\\\\n")+"_\n");
      sb.append("  ├ filteredStream "+filteredStream.getBuffered().replaceAll("[\r\n]","\\\\n")+"_\n");
      sb.append("  │ ├ lineEmitting "+lineEmittingStream.getBuffered().replaceAll("[\r\n]","\\\\n")+"_\n");
      sb.append("  │ └ shStream "+shStream.toString().replaceAll("[\r\n]","\\\\n")+"_\n");
      sb.append("  └ promptStream "+promptStream.getBuffered().replaceAll("[\r\n]","\\\\n")+"_\n");
      return sb.toString();
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
   public String getTraceName(){return traceName;}
   public void setTrace(String traceName){
      if(!hasTrace()){
         this.traceName = traceName;
         try {
            String tDir = System.getProperty("java.io.tmpdir");
            logger.info("streamtracing " + traceName + " to " + tDir);
            Path rawTracePath = Paths.get(tDir, "qdup." + traceName + ".raw.log");
            if(!Files.exists(rawTracePath)){
               Files.createFile(rawTracePath);
            }
            FileOutputStream rawTraceStream = new FileOutputStream(rawTracePath.toFile());
            Path efsTracePath = Paths.get(tDir, "qdup." + traceName + ".efs.log");
            if(!Files.exists(efsTracePath)){
               Files.createFile(efsTracePath);
            }
            FileOutputStream efsTraceStream = new FileOutputStream(efsTracePath.toFile());
            escapeFilteredStream.addStream("trace", efsTraceStream);
            addStream("trace", rawTraceStream);
         }catch(Exception e){
            logger.error("Error trying to create trace for stream "+getName(),e);
         }
      }
   }
   public OutputStream getTrace(){
      return escapeFilteredStream.getStream("trace");
   }
   public OutputStream getRawTrace(){
      return getStream("trace");
   }

   private void clearCommand(String name){
      if("command".equals(name)) {
         filteredStream.remove("command");
      }
   }

   public void setCommand(String command){
      filteredStream.addFilter("command",command,"");
   }

   public void trace(String output){
      if(hasTrace()){
         try {
            getTrace().write(output.getBytes());
            getRawTrace().write(output.getBytes());
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

   public void sharePrompts(SessionStreams otherStreams){
      if(otherStreams==null){
         return;
      }
      suffixStream.getSuffixes().forEach((name)->{
         otherStreams.addPrompt(name,suffixStream.getSuffix(name), suffixStream.getReplacement(name));
      });
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
