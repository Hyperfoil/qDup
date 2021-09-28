package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.yaup.AsciiArt;
import io.hyperfoil.tools.yaup.StringUtil;
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
import java.util.LinkedList;
import java.util.List;
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
      if (path == null || path.isEmpty() || destination == null || destination.isEmpty()) {
         return false;
      } else {
         logger.info("Local.upload({},{}@{}:{})", path, host.getUserName(), host.getHostName(), destination);
         return rsyncSend(host, path, destination);
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
      if (path == null || path.isEmpty() || destination == null || destination.isEmpty()) {
         return false;
      } else {
         logger.info("Local.download({}:{},{})", host.getSafeString(), path, destination);
         return rsyncFetch(host, path, destination);
      }
   }

   public long remoteFileSize(String path, Host host){
      if (path == null || path.isEmpty() ) {
         return -1;
      } else {
         logger.info("Local.remoteFileSize({}:{})", host.getSafeString(), path);
         return rsyncFileSize(host, path);
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

      return runRsyncCmd(cmd, "rsyncSend", line -> logger.trace("  I: {}", line));

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

      runRsyncCmd(cmd, "rsyncFetch", line -> {
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

      return runRsyncCmd(cmd, "rsyncFetch", line -> logger.trace("  I: {}", line));

   }

   private boolean runRsyncCmd(List<String> cmd, String action, Consumer<String> inputStreamConsumer){
      boolean rtrn = true;//worked
      ProcessBuilder builder = new ProcessBuilder();
      builder.command(cmd);
      logger.debug("Running rsync command : " + cmd.stream().collect(Collectors.joining(" ")));
      try {
         Process p = builder.start();

         final InputStream inputStream = p.getInputStream();
         final OutputStream outputStream = p.getOutputStream();
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
