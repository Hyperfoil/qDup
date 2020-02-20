package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.yaup.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.hyperfoil.tools.qdup.config.RunConfig;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.util.LinkedList;
import java.util.List;
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
         storePassphrase(getIdentity(), getPassphrase());
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

   public void upload(String path, String destination, Host host) {
      if (path == null || path.isEmpty() || destination == null || destination.isEmpty()) {

      } else {
         logger.info("Local.upload({},{}@{}:{})", path, host.getUserName(), host.getHostName(), destination);
         rsyncSend(host, path, destination);
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

   public void download(String path, String destination, Host host) {

      if (path == null || path.isEmpty() || destination == null || destination.isEmpty()) {

      } else {
         logger.info("Local.download({}@{}:{},{})", host.getUserName(), host.getHostName(), path, destination);
         rsyncFetch(host, path, destination);
      }
   }

   private String prepSshString(int port) {
      String rtrn = this.ssh;
      rtrn+=" -o StrictHostKeyChecking=no";

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
   private void rsyncSend(Host host, String path, String dest) {
      File sourceFile = new File(path);
      if(!sourceFile.exists()){
         logger.error("Cannot send {} because it does not exist",path);
         return;
      }
      ProcessBuilder builder = new ProcessBuilder();
      String sshOpt = prepSshString(host.getPort());
      List<String> cmd = new LinkedList<>();
      if(host.hasPassword()){
         cmd.add("sshpass");
         cmd.add("-p");
         cmd.add(host.getPassword());
      }
      cmd.add("/usr/bin/rsync");
      cmd.add("--archive");
      cmd.add("--verbose");
      cmd.add("--compress");
      cmd.add("--ignore-times");
      //TODO only add --rsh sshOpt if needed?
      cmd.add("--rsh");
      cmd.add(sshOpt);
      cmd.add(path);
      cmd.add(host.getUserName() + "@" + host.getHostName() + ":" + dest);
      builder.command(cmd);
      logger.debug("Running rsync command : " + cmd.stream().collect(Collectors.joining(" ")));
      try {
         Process p = builder.start();
         final InputStream inputStream = p.getInputStream();
         final OutputStream outputStream = p.getOutputStream();
         final InputStream errorStream = p.getErrorStream();

         int result = p.waitFor();
         logger.debug("rsyncSend.result = {}", result);
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
         logger.warn("rysnc was interrupted: " + path);
         Thread.interrupted();
      }

   }
   private void rsyncFetch(Host host, String path, String dest) {
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

      ProcessBuilder builder = new ProcessBuilder();
      String sshOpt = prepSshString(host.getPort());
      List<String> cmd = new LinkedList<>();
      if(host.hasPassword()){
         cmd.add("sshpass");
         cmd.add("-p");
         cmd.add(host.getPassword());
      }
      cmd.add("/usr/bin/rsync");
      cmd.add("--archive");
      cmd.add("--verbose");
      cmd.add("--compress");
      if(path.contains("/./")){
         cmd.add("--relative");
      }
      //TODO only add --rsh sshOpt if needed?
      cmd.add("--rsh");
      cmd.add(sshOpt);
      cmd.add(host.getUserName() + "@" + host.getHostName() + ":" + path);
      cmd.add(dest);
      builder.command(cmd);
      logger.debug("Running rsync command : " + cmd.stream().collect(Collectors.joining(" ")));
      try {
         Process p = builder.start();
         final InputStream inputStream = p.getInputStream();
         final OutputStream outputStream = p.getOutputStream();
         final InputStream errorStream = p.getErrorStream();

         int result = p.waitFor();
         logger.debug("rsyncFetch.result = {}", result);
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
