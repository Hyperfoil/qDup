package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.shell.AbstractShell;
import io.hyperfoil.tools.yaup.StringUtil;
import io.hyperfoil.tools.yaup.json.Json;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.hyperfoil.tools.qdup.config.RunConfig;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by wreicher
 * Performs actions on the local system (without an qdup connection)
 * Currently just used to upload / download files WITH the SUT
 * uses rsync instead of scp because it was much faster in our lab
 */
// Has a lot of extra code for download queueing and progress updates which are not used at the moment
public class Local {

   private static final String SSH_PATH = "/usr/bin/ssh";

   final static Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


   private String ssh = SSH_PATH;

   private final String identity;
   private final String knownHosts;
   private final String passphrase;

   public Local() {
      this(null, null, null);
   }
   public Local(RunConfig config) {
      this(
         config != null && config.hasCustomIdentity() ? config.getIdentity() : null,
         config != null && config.hasCustomKnownHosts() ? config.getKnownHosts() : null,
         config != null && config.hasCustomPassphrase() ? config.getPassphrase() : null
      );
   }
   public Local(String identity, String knownHosts, String passphrase) {
      this.identity = identity;
      this.knownHosts = knownHosts;
      this.passphrase = passphrase;

      if (hasIdentity() && hasPassphrase()) {
         //TODO need fix: removed because we get an exception if there is a passPhrase to store
         //storePassphrase(getIdentity(), getPassphrase());
      }
   }



   public boolean hasIdentity() {
      return this.identity != null;
   }

   public boolean hasKnownHosts() {
      return this.knownHosts != null;
   }

   public boolean hasPassphrase() {
      return this.passphrase != null;
   }

   public String getIdentity() {
      return this.identity;
   }

   public String getKnownHosts() {
      return this.knownHosts;
   }

   public String getPassphrase() {
      return this.passphrase;
   }

   public boolean upload(String path, String destination, Host host) {
      if (path == null || path.isEmpty() || destination == null || destination.isEmpty() || !host.hasUpload()) {
         return false;
      } else if (!((new File(path)).exists())){
         logger.error("File does not exist for upload: "+path);
         return false;
      } else if (!host.isLocal() && host.hasContainerId()){
         logger.info("Local.upload({},{}:{})", path, host.getSafeString(), destination);
         Host remoteHost = host.withoutContainer();
         AbstractShell shell = AbstractShell.getShell(
            remoteHost, 
            "",
            new ScheduledThreadPoolExecutor(1), 
            new SecretFilter(), 
            false);
         String remoteDestination = shell.shSync("mktemp -d");
         boolean uploaded = upload(path,remoteDestination,remoteHost);
         if(!uploaded){
            logger.error("failed to upload "+path+" to remote host as part of upload to "+host);
            return false;
         }
         File localFile = new File(path);
         Path remotePath = Path.of(remoteDestination,localFile.getName());
         String remoteString = remotePath.toString();
         Json json = new Json();
         json.set("host",host.toJson());
         json.set("source",remoteString);
         json.set("destination",destination);
         json.set("knownHost",hasKnownHosts()?getKnownHosts():false);
         json.set("identity",hasIdentity()?getIdentity():false);
         if(host.hasContainerId()){
            json.set("container",host.getContainerId());
         }
         //we need to upload command to send to host, we will send the file from remoteHost
         List<String> populated = Cmd.populateList(json,host.getUpload()).stream().filter(v->v!=null && !v.isBlank()).collect(Collectors.toUnmodifiableList());
         if(Cmd.hasPatternReference(populated, StringUtil.PATTERN_PREFIX)){
            logger.error("failed to populate remote upload pattern: "+populated.stream().collect(Collectors.joining(" "))+"\nhost: "+remoteHost);
            return false;
         }
         String mergedUpload = populated.stream().collect(Collectors.joining(" "));
         String mergedResponse = shell.shSync(mergedUpload);
         //output
         //cleanup the folder we created on the remoteHost
         String rmResponse = shell.shSync("rm -rf "+remoteDestination);
         //
         return true;
      } else {
         logger.info("Local.upload({},{}:{})", path, host.getSafeString(), destination);
         Json json = new Json();
         json.set("host",host.toJson());
         json.set("source",path);
         json.set("destination",destination);
         json.set("knownHost",hasKnownHosts()?getKnownHosts():false);
         json.set("identity",hasIdentity()?getIdentity():false);
         if(host.hasContainerId()){
            json.set("container",host.getContainerId());
         }

         List<String> populated = Cmd.populateList(json,host.getUpload()).stream().filter(v->v!=null && !v.isBlank()).collect(Collectors.toUnmodifiableList());
         if(Cmd.hasPatternReference(populated,StringUtil.PATTERN_PREFIX)){
            logger.error("failed to populate upload pattern: "+populated.stream().collect(Collectors.joining(" "))+"\nhost: "+host);

            return false;
         }else{

         }
         return runSyncProcess(populated,"upload",(line)->{
            logger.debug(line);
         });
      }
   }

   private void storePassphrase(String identity, String passphrase) {
      if (passphrase != RunConfigBuilder.DEFAULT_PASSPHRASE) {
         ProcessBuilder builder = new ProcessBuilder();
         builder.command("/usr/bin/ssh-add", identity);
         try {
            Process p = builder.start();
            final InputStream inputStream = p.getInputStream();
            final OutputStream outputStream = p.getOutputStream();
            final InputStream errorStream = p.getErrorStream();

            outputStream.write(passphrase.getBytes());
            outputStream.flush();
            int result = p.waitFor();
            logger.debug("ssh-add.result = {}", result);
            String line = null;
            BufferedReader reader = null;
            reader = new BufferedReader(new InputStreamReader(errorStream));
            while ((line = reader.readLine()) != null) {
               logger.error("  E: {}", line);
            }
            reader = new BufferedReader(new InputStreamReader(inputStream));
            while ((line = reader.readLine()) != null) {
               logger.trace("  I: {}", line);
            }
         } catch (IOException e) {
            e.printStackTrace();
         } catch (InterruptedException e) {
            e.printStackTrace();
         }
      }
   }

   public String getRemote(String path){
      String rtrn = null;
      if (rtrn == null && path.contains(":") && !path.startsWith("http") && !path.matches(".*?:\\d+\\S+")){ //try to download the file
         String hostString = path.substring(0,path.indexOf(":"));
         String pathString = path.substring(path.indexOf(":"));
         Host host = Host.parse(hostString);
         try {
            File tmpDest = File.createTempFile("qdup-remote","yaml");
            boolean ok = download(pathString,tmpDest.getPath(),host);
            if(ok){
               rtrn = Files.readString(tmpDest.toPath());
            }
         } catch (IOException e) {
            logger.debug("failed to download from "+path);
         }
      }
      if (rtrn == null){
         HttpClient client = HttpClient.newBuilder().build();
         HttpRequest request = null;
         URI uri = null;
         try {
            URIBuilder uriBuilder = new URIBuilder(path);
            if(uriBuilder.getScheme()==null){
               uriBuilder = new URIBuilder("https://"+path);
            }
            uri = uriBuilder.build();
         }catch (URISyntaxException e){
            logger.debug("failed to build url from "+path);
         }
         try {
            request = HttpRequest.newBuilder(uri).build();
            HttpResponse response = client.send(request, HttpResponse.BodyHandlers.ofString());
            rtrn = response.body().toString();
         } catch (IOException e) {
            logger.debug("failed to use "+path+" as a url",e);
         } catch (InterruptedException e) {
            logger.debug("interrupted using "+path+" as a url",e);
         }
      }
      return rtrn;
   }

   public boolean download(String path, String destination, Host host) {
      if (path == null || path.isEmpty() || destination == null || destination.isEmpty() || !host.hasDownload()) {
         return false;
      } else if (!host.isLocal() && host.hasContainerId()){
         logger.info("Local.download({}:{},{})", host.getSafeString(), path, destination);
         Host remoteHost = host.withoutContainer();
         ScheduledThreadPoolExecutor stpe = new ScheduledThreadPoolExecutor(1);
         AbstractShell downloadShell = AbstractShell.getShell(
            remoteHost, 
            "",
            stpe,
            new SecretFilter(), 
            false);
         //TODO this temp remomte directory should be created based on the host type
         String remoteDestination = downloadShell.shSync("mktemp -d");
         Json json = new Json();
         json.set("host",host.toJson());
         json.set("source",path);
         //need a tmp destination on the remoteHost
         json.set("destination",remoteDestination);
         json.set("knownHost",hasKnownHosts()?getKnownHosts():false);
         json.set("identity",hasIdentity()?getIdentity():false);
         if(host.hasContainerId()){
            json.set("container",host.getContainerId());
         }
         List<String> populated = Cmd.populateList(json,host.getDownload()).stream().filter(v->v!=null && !v.isBlank()).collect(Collectors.toUnmodifiableList());
         String mergedPopulated = populated.stream().collect(Collectors.joining(" "));
         if(mergedPopulated.contains(StringUtil.PATTERN_PREFIX)){
            logger.error("unable to fully populate download: "+mergedPopulated);
            return false;
         }
         
         String mergeResponse = downloadShell.shSync(mergedPopulated);
         String remoteFileStr = downloadShell.shSync("ls -c1 "+remoteDestination);
         List<String> remoteFiles = Arrays.asList(remoteFileStr.split(System.lineSeparator()));
         //if there's only one file we need to directly target it
         //otherwise we download everything in the remote folder
         String suffix = remoteFiles.size() == 1 ? "/"+remoteFiles.get(0) + (path.endsWith("/") ? "/" : "") : "/";
         //at this point the files should exist on remoteDestination
         //adding the / to end of remoteDestination to transfer content not folder
         boolean rtrn = download(remoteDestination+suffix, destination, remoteHost);
         //at this point the files are local, we can delete the remote dir
         String remoteDeleteResp = downloadShell.shSync("rm -rf "+remoteDestination);
         downloadShell.close(false);
         stpe.shutdownNow();
         return rtrn;
      }else{
         logger.info("Local.download({}:{},{})", host.getSafeString(), path, destination);
         Json json = new Json();
         json.set("host",host.toJson());
         json.set("source",path);
         json.set("destination",destination);
         json.set("knownHost",hasKnownHosts()?getKnownHosts():false);
         json.set("identity",hasIdentity()?getIdentity():false);
         if(host.hasContainerId()){
            json.set("container",host.getContainerId());
         }
         //this is painfully slow in vscode debugger :(
         List<String> populated = Cmd.populateList(json,host.getDownload()).stream().filter(v->v!=null && !v.isBlank()).collect(Collectors.toUnmodifiableList());
         if(Cmd.hasPatternReference(populated,StringUtil.PATTERN_PREFIX)){
            return false;
         }
         StringBuffer sb = new StringBuffer();
         boolean rtrn =  runSyncProcess(populated,"download",(line)->{
            logger.debug(line);
            sb.append(line);
            sb.append(System.lineSeparator());
         });
         return rtrn;
      }
   }

   public long remoteFileSize(String path, Host host){
      if (path == null || path.isEmpty() || !host.hasFileSize()) {
         return -1;
      } else {
         logger.info("Local.fileSize({}:{},{})", host.getSafeString(), path );
         //TODO do we create a "getJson for host commands"
         Json json = new Json();
         json.set("host",host.toJson());
         json.set("source",path);
         json.set("knownHost",hasKnownHosts()?getKnownHosts():false);
         json.set("identity",hasIdentity()?getIdentity():false);
         List<String> populated = Cmd.populateList(json,host.getGetFileSize()).stream().filter(v->{
            return v!=null && !v.isEmpty();
         }).collect(Collectors.toUnmodifiableList());
         StringBuilder sb = new StringBuilder();
         boolean ok = runSyncProcess(populated,"file-size",(line)->{
            if(sb.length()>0){
               sb.append(System.lineSeparator());
            }
            sb.append(line);
         });
         String response = sb.toString();
         if(response.matches("\\d+(?:[,.]\\d{3})*")){
            return Long.parseLong(response.replaceAll(",",""));
         }else{
            return -1;
         }
      }
   }

   private String prepSshString(int port) {
      String rtrn = this.ssh;
      //rtrn+=" -o StrictHostKeyChecking=no";

      if(hasKnownHosts()){
         rtrn+=" -o UserKnownHostsFile="+(getKnownHosts().contains(" ") ? StringUtil.quote(getKnownHosts(),"'") : getKnownHosts());
      }else{
         rtrn+=" -o UserKnownHostsFile=/dev/null"; // added to stop adding entries to ~/.ssh/known_hosts when testing
         rtrn+=" -o LogLevel=ERROR"; // disables messages about adding to known_hosts
      }
      if(hasIdentity()){
         rtrn+=" -i "+(getIdentity().contains(" ") ? StringUtil.quote(getIdentity(),"'") : getIdentity());
      }
      if(Host.DEFAULT_PORT != port){
         rtrn+=" -p "+port;
      }
      if (rtrn != null) {
         rtrn = rtrn + " -o StrictHostKeyChecking=no";
      }
      return rtrn;
   }

   private List<String> rsyncBaseCmd(Host host){

      List<String> cmd = new LinkedList<>();

      String sshOpt = prepSshString(host.getPort());

      if(host.hasPassword()){
         cmd.add("sshpass");
         cmd.add("-p");
         cmd.add(host.getPassword());
      }
      cmd.add("/usr/bin/rsync");
      cmd.add("--archive");
      cmd.add("--verbose");
      cmd.add("--compress");
      //TODO only add --rsh sshOpt if needed?
      //use = and quote opts with full syntax
//      cmd.add("--rsh="+StringUtil.quote(sshOpt));
      cmd.add("-e");
      //cmd.add(StringUtil.quote(sshOpt));
      cmd.add(sshOpt);
      return cmd;
   }

   private boolean rsyncSend(Host host, String path, String dest) {
      File sourceFile = new File(path);
      if(!sourceFile.exists()){
         logger.error("Cannot send {} because it does not exist",path);
         return false;
      }

      List<String> cmd = rsyncBaseCmd(host);
      cmd.add("--ignore-times");
      cmd.add(path);
      cmd.add(host.getUserName() + "@" + host.getHostName() + ":" + dest);

      return runSyncProcess(cmd, "rsyncSend", line -> logger.trace("  I: {}", line));

   }

   private long rsyncFileSize(Host host, String path){
      AtomicLong fileSize = new AtomicLong(-1);
      Pattern p = Pattern.compile("total size is (?<fileSize>\\d{1,3}(?:[,.]\\d{3})*)(\\s|\\w|[,.]|\\(|\\))*");

      List<String> cmd = rsyncBaseCmd(host);
      if(path.contains("/./")){
         cmd.add("--relative");
      }
      cmd.add(host.getUserName() + "@" + host.getHostName() + ":" + path);
      cmd.add("--stats");
      cmd.add("--dry-run");

      runSyncProcess(cmd, "rsyncFetch", line -> {
                 Matcher m = p.matcher(line);
                 while (m.find()) {
                    String matchedFileSize = m.group("fileSize");
                    if(matchedFileSize != null && !"".equals(matchedFileSize)){
                       matchedFileSize = matchedFileSize.replaceAll(",","");
                       try {
                          fileSize.set(Long.parseLong(matchedFileSize));
                       } catch (NumberFormatException nfe){
                          logger.error("Failed to parse fileSize as a number: {}", matchedFileSize);
                       }
                    }
                 }
              });

      return fileSize.get();
   }

   private boolean rsyncFetch(Host host, String path, String dest) {
      File destinationFile = new File(dest);
      if (!destinationFile.exists()) {
         if (destinationFile.isDirectory()) {
            logger.trace("creating {} for rsyncFetch {}", dest, path);
            destinationFile.mkdirs();
         } else {
            logger.trace("creating {} for rsyncFetch {}", destinationFile.getParentFile().getPath(), path);
            destinationFile.getParentFile().mkdirs();
         }
      }

      List<String> cmd = rsyncBaseCmd(host);
      if(path.contains("/./")){
         cmd.add("--relative");
      }
      cmd.add(host.getUserName() + "@" + host.getHostName() + ":" + path);
      cmd.add(dest);

      return runSyncProcess(cmd, "rsyncFetch", line -> logger.trace("  I: {}", line));

   }

   public static void pipelineSplit(String input,List<List<String>> list){
      if(list.isEmpty()){
         list.add(new ArrayList<>());
      }
      Pattern p = Pattern.compile("^(?<split>(?:\\s*\\|\\s*)|\\s+).*");
      Matcher m = p.matcher(input);
      //cannot use split because we lose the substring that matched the split (spaces or characters)
      boolean inQuote = false;
      int quoteStart = -1;
      int flushed = 0;
      char quoteChar = ' ';
      for(int i=0; i<input.length(); i++){
         char targetChar = input.charAt(i);
         if(inQuote){
            if(targetChar == quoteChar && input.charAt(i-1) != '\\'){
               inQuote=false;
            }
         }else{
            if(targetChar == '\'' || targetChar == '"'){
               inQuote=true;
               quoteStart=i;
               quoteChar=input.charAt(i);
            }else if (targetChar == ' ' || targetChar == '|' ){
               String targetStr = input.substring(i);
               m.reset(targetStr);
               if(m.matches()){
                  String splitChars = m.group("split");
                  if(flushed<i){
                     list.get(list.size()-1).add(StringUtil.removeQuotes( input.substring(flushed,i) ));
                     flushed = i + splitChars.length();
                  }
                  i+=splitChars.length()-1;
                  if(splitChars.contains("|")) {
                     //new pipeline
                     list.add(new ArrayList<>());
                  }
               }
            }
         }
      }
      //flush the remainder
      if(flushed < input.length()){
         list.get(list.size()-1).add(StringUtil.removeQuotes( input.substring(flushed,input.length() )));
      }
   }

   public static boolean isPipeline(List<String> args){
      return args.stream().anyMatch(v->"|".equals(v) || (v!=null && v.contains("|")));
   }
   public static List<List<String>> splitPipelines(List<String> args){
      List<List<String>> rtrn = new ArrayList<>();
      List<String> current = new ArrayList<>();
      rtrn.add(current);
      for(String arg : args){
         if("|".equals(arg)) {
            current = new ArrayList<>();
            rtrn.add(current);
         }else if (arg.contains("|")){
            pipelineSplit(arg,rtrn);
         }else{
            current.add(arg);
         }
      }
      if(current.isEmpty()){
         rtrn.remove(rtrn.size()-1);
      }
      return rtrn;
   }
   private static boolean
   runSyncProcess(List<String> cmd, String action, Consumer<String> inputStreamConsumer){
      boolean rtrn = true;//worked
      //ProcessBuilder builder = new ProcessBuilder();
      List<ProcessBuilder> processes = new ArrayList<>();
      List<List<String>> splitCommand = splitPipelines(cmd);
      for(int i=0; i<splitCommand.size(); i++){
         List<String> args = splitCommand.get(i);
         ProcessBuilder pipe = new ProcessBuilder();
         pipe.command(args);
         //was mentioned in https://stackoverflow.com/questions/3776195/using-java-processbuilder-to-execute-a-piped-command but appears wrong
//         if(splitCommand.size()>1){
//            pipe.redirectInput(ProcessBuilder.Redirect.INHERIT);
//         }
//         if(i<splitCommand.size()-1){
//            pipe.inheritIO().redirectOutput(ProcessBuilder.Redirect.PIPE);
//         }else if (splitCommand.size()>1){
//            pipe.redirectError(ProcessBuilder.Redirect.INHERIT);
//         }
         processes.add(pipe);
      }
      logger.debug("Running local command : " + cmd);
      try {
         Process p;
         if(processes.size()==1){
            p = processes.get(0).start();
         }else{
            List<Process> started = ProcessBuilder.startPipeline(processes);
            p = started.get(started.size()-1);
         }
         final InputStream inputStream = p.getInputStream();
         //final OutputStream outputStream = p.getOutputStream();
         final InputStream errorStream = p.getErrorStream();

         int result = p.waitFor();
         logger.debug(action.concat("result = {}"), result);
         String line = null;
         BufferedReader reader = null;
         reader = new BufferedReader(new InputStreamReader(errorStream));
         while ((line = reader.readLine()) != null) {
            if(rtrn){
               logger.error(" E: {}", cmd.stream().collect(Collectors.joining(" ")));
               rtrn = false;
            }
            logger.error("  E: {}", line);
         }
         reader = new BufferedReader(new InputStreamReader(inputStream));
         while ((line = reader.readLine()) != null) {
            inputStreamConsumer.accept(line);
         }

      } catch (IOException e) {
         rtrn = false;
         e.printStackTrace();
      } catch (InterruptedException e) {
//         logger.warn("rysnc was interrupted: " + path);
         rtrn = false;
         Thread.interrupted();
      }
      return rtrn;
   }
}
