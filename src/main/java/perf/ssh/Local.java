package perf.ssh;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.CommandResult;
import perf.ssh.config.RunConfig;
import perf.ssh.config.RunConfigBuilder;
import perf.yaup.AsciiArt;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static perf.ssh.Host.DEFAULT_PORT;

/**
 * Created by wreicher
 * Performs actions on the local system (without an ssh connection)
 * Currently just used to upload / download files WITH the SUT
 * uses rsync instead of scp because it was much faster in our lab
 *
 */
// Has a lot of extra code for download queueing and progress updates which are not used at the moment
public class Local {

    private static final String SSH_PATH = "/usr/bin/ssh";

    final static Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private String ssh;

    public Local(RunConfig config){
        if(config!=null && (config.hasCustomIdentity() || config.hasCustomKnownHosts() || config.hasCustomPassphrase())){
            this.ssh=SSH_PATH;
            if(config.hasCustomKnownHosts()){
                this.ssh+="-o UserKnownHostsFile="+config.getKnownHosts()+" ";
            }
            if(config.hasCustomIdentity()){
                this.ssh+="-i "+config.getIdentity()+" ";
            }
            if(config.hasCustomPassphrase()){
                storePassphrase(config.getIdentity(),config.getPassphrase());
            }
        }else{
            this.ssh = null;
        }
    }

    public void upload(String path,String destination,Host host){
        if(path == null || path.isEmpty() || destination == null || destination.isEmpty()){

        }else{
            String hostName = host.getHostName();
            String userName = host.getUserName();
            logger.info("Local.upload({},{}@{}:{})",path,userName,hostName,destination);
            rsyncSend(userName, hostName, host.getPort(), path, destination);

        }
    }
    private void storePassphrase(String identity, String passphrase){
        if(passphrase!= RunConfigBuilder.DEFAULT_PASSPHRASE){
            ProcessBuilder builder = new ProcessBuilder();
            builder.command("/usr/bin/ssh-add", identity);
            try {
                Process p =  builder.start();
                final InputStream inputStream = p.getInputStream();
                final OutputStream outputStream = p.getOutputStream();
                final InputStream errorStream = p.getErrorStream();

                outputStream.write(passphrase.getBytes());
                outputStream.flush();
                int result = p.waitFor();
                logger.debug("ssh-add.result = {}",result);
                String line = null;
                BufferedReader reader = null;
                reader = new BufferedReader(new InputStreamReader(errorStream));
                while( (line=reader.readLine())!=null){
                    logger.error("  E: {}",line);
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));
                while( (line=reader.readLine())!=null){
                    logger.trace("  I: {}",line);
                }

            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
    }
    public void download(String path,String destination, Host host){

        if(path == null || path.isEmpty() || destination == null || destination.isEmpty()){

        } else {
            String hostName = host.getHostName();
            String userName = host.getUserName();
            logger.info("Local.download({}@{}:{},{})",userName,hostName,path,destination);
            rsyncFetch(userName, hostName, host.getPort(), path, destination);
        }
    }
    private void rsyncSend(String userName, String hostName, int port, String path, String dest){
        ProcessBuilder builder = new ProcessBuilder();


        String sshOpt = prepSshString(port);
        if(sshOpt==null || sshOpt.isEmpty()) {
            builder.command("/usr/bin/rsync", "-avz", path, userName + "@" + hostName + ":" + dest);
        }else{
            builder.command("/usr/bin/rsync", "-avz", "-e",sshOpt,path, userName + "@" + hostName + ":" + dest);
        }
        logger.debug( "Running rsync command: " + builder.command().stream().collect( Collectors.joining(" ")));
        try {
            Process p =  builder.start();
            final InputStream inputStream = p.getInputStream();
            final OutputStream outputStream = p.getOutputStream();
            final InputStream errorStream = p.getErrorStream();

            int result = p.waitFor();
            logger.debug("rsyncSend.result = {}",result);
            String line = null;
            BufferedReader reader = null;
            reader = new BufferedReader(new InputStreamReader(errorStream));
            while( (line=reader.readLine())!=null){
                logger.error("  E: {}",line);
            }
            reader = new BufferedReader(new InputStreamReader(inputStream));
            while( (line=reader.readLine())!=null){
                logger.trace("  I: {}",line);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    private String prepSshString(int port){
        String rtrn = this.ssh;
        if(port != DEFAULT_PORT){
            if(rtrn == null ){
                rtrn = SSH_PATH;
            }
            rtrn+=" -p "+port+" ";
        }
        return rtrn;
    }
    private void rsyncFetch(String userName, String hostName,int port, String path, String dest){
        File destinationFile = new File(dest);
        if(!destinationFile.exists()){
            if(destinationFile.isDirectory()){
                logger.trace("creating {} for rsyncFetch {}",dest,path);
                destinationFile.mkdirs();
            }else{
                logger.trace("creating {} for rsyncFetch {}",destinationFile.getParentFile().getPath(),path);
                destinationFile.getParentFile().mkdirs();
            }
        }

        ProcessBuilder builder = new ProcessBuilder();

        String sshOpt = prepSshString(port);
        if(sshOpt==null || sshOpt.isEmpty()) {
            builder.command("/usr/bin/rsync", "-avz", userName + "@" + hostName + ":" + path, dest);
        }else{
            builder.command("/usr/bin/rsync", "-avz", "-e",sshOpt, userName + "@" + hostName + ":" + path, dest);
        }
        try {
            Process p =  builder.start();
            final InputStream inputStream = p.getInputStream();
            final OutputStream outputStream = p.getOutputStream();
            final InputStream errorStream = p.getErrorStream();

            int result = p.waitFor();
            logger.debug("rsyncFetch.result = {}",result);
            String line = null;
            BufferedReader reader = null;
            reader = new BufferedReader(new InputStreamReader(errorStream));
            while( (line=reader.readLine())!=null){
                logger.error("  E: {}",line);
            }
            reader = new BufferedReader(new InputStreamReader(inputStream));
            while( (line=reader.readLine())!=null){
                logger.trace("  I: {}",line);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
}
