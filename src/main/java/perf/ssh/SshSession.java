package perf.ssh;


import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.channel.PtyMode;
import org.apache.sshd.common.util.io.NoCloseOutputStream;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.server.Signal;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.CommandResult;
import perf.ssh.stream.FilteredStream;
import perf.ssh.stream.LineEmittingStream;
import perf.ssh.stream.SemaphoreStream;


import java.io.*;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

import static perf.ssh.config.RunConfigBuilder.DEFAULT_IDENTITY;
import static perf.ssh.config.RunConfigBuilder.DEFAULT_KNOWN_HOSTS;
import static perf.ssh.config.RunConfigBuilder.DEFAULT_PASSPHRASE;

/**
 * Created by wreicher
 * Provides the remote connection to run shell commands and monitor the output.
 * Needs to be updated WITH the current Cmd, CommandResult before sending a command to the remote host
 */

//Todo separate out the prompt, the command, and the output of the command
public class SshSession implements Runnable, Consumer<String>{

    final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

    private static final String prompt = "#%@_ssh_!*> "; // a string unlikely to appear in the output of any command


    private SshClient sshClient;
    private ClientSession clientSession;
    private ChannelShell channelShell;

    private Properties sshConfig;



    private PrintStream commandStream;
    private ByteArrayOutputStream shStream;

    private Semaphore shellLock;

    private SemaphoreStream semaphoreStream;
    private FilteredStream filteredStream;
    private FilteredStream stripStream;

    private LineEmittingStream lineEmittingStream;

    private boolean isOpen = false;

    private Cmd command;
    private CommandResult result;

    private Host host;

    public SshSession(Host host){
        this(host,DEFAULT_KNOWN_HOSTS,DEFAULT_IDENTITY,DEFAULT_PASSPHRASE);
    }
    public SshSession(Host host,String knownHosts,String identity,String passphrase){

        this.host = host;

        shellLock = new Semaphore(1);

        sshClient = SshClient.setUpDefaultClient();
        //StrictHostKeyChecking=no
        sshClient.start();
        sshClient.setServerKeyVerifier((clientSession1,remoteAddress,serverKey)->{return true;});

        try {
            clientSession = sshClient.connect(host.getUserName(),host.getHostName(),host.getPort()).verify().getSession();
            clientSession.addPublicKeyIdentity(SecurityUtils.loadKeyPairIdentity(
                    identity,
                    Files.newInputStream((new File(identity)).toPath()),
                    (resourceKey)->{return passphrase;}
            ));
            clientSession.auth().verify(5_000);

            channelShell = clientSession.createShellChannel();
            channelShell.getPtyModes().put(PtyMode.ECHO,1);//need echo for \n from real SH but adds gargage chars for test :(
            channelShell.setPtyType("vt100");
            channelShell.setPtyColumns(1024);
            channelShell.setPtyWidth(1024);
            channelShell.setPtyHeight(80);
            channelShell.setPtyLines(80);
            channelShell.setUsePty(true);

            PipedInputStream pipeIn = new PipedInputStream();
            PipedOutputStream pipeOut = new PipedOutputStream(pipeIn);

            channelShell.setIn(pipeIn);

            //the output of the current sh command
            shStream = new ByteArrayOutputStream();

            commandStream = new PrintStream(pipeOut);

            filteredStream = new FilteredStream();
            stripStream = new FilteredStream();

            semaphoreStream = new SemaphoreStream(shellLock,("\n"+prompt).getBytes());

            SemaphoreStream errStream = new SemaphoreStream(shellLock,("\n"+prompt).getBytes());

            //channelShell.setOut(semaphoreStream);
            channelShell.setOut(semaphoreStream);
            channelShell.setErr(semaphoreStream);//prompt goes to error stream so have to listen there too

            filteredStream.addFilter("prompt","\n"+prompt, "$:"/*newPrompt*/);//replace the unique string prompt WITH something more user friendly
            stripStream.addFilter("prompt","$:","");

            stripStream.addFilter("^C",new byte[]{0,0,0,3});
            stripStream.addFilter("^P",new byte[]{0,0,0,16});
            stripStream.addFilter("^T",new byte[]{0,0,0,20});
            stripStream.addFilter("^X",new byte[]{0,0,0,24});
            stripStream.addFilter("^@",new byte[]{0,0,0});

            lineEmittingStream = new LineEmittingStream();
            lineEmittingStream.addConsumer(this);
            filteredStream.addStream("strip",stripStream); // echo the entire channelSession to sh output

            stripStream.addStream("lines",lineEmittingStream);
            stripStream.addStream("sh",shStream);

            semaphoreStream.addStream("filtered",filteredStream);
            semaphoreStream.setRunnable(this);

            channelShell.open();
            isOpen=true;



            sh("unset PROMPT_COMMAND; export PS1='" + prompt + "'");
            sh("");//forces the thread to wait for the previous sh to complete

            try {
                shellLock.acquire();
                try{
                    shellLock.release();
                }finally{

                }
                //moved release to finally block, check permits to ensure it was acquired
                //shellLock.release();//reset so the next call to aquire for sh(...) isn't blocked
            } catch (InterruptedException e) {
                logger.warn("{}@{} interrupted while waiting for initial prompt",host.getUserName(),host.getHostName());
                e.printStackTrace();
                Thread.interrupted();
            }

        } catch (GeneralSecurityException | IOException e) {

            e.printStackTrace();
            logger.error("Exception while connecting to {}@{}",host.getUserName(),host.getHostName());
            isOpen = false;

        } finally {
            logger.debug("{} session.isOpen={} shell.isOpen={}",
                    this.getHostName(),
                    clientSession.isOpen(),
                    channelShell==null?"false":channelShell.isOpen()
            );
        }
        sshConfig = new Properties();
        sshConfig.put("StrictHostKeyChecking", "no");
    }
    public boolean isOpen(){return isOpen;}
    public String getUserName(){return host.getUserName();}
    public String getHostName(){return host.getHostName();}
    public Host getHost(){return host;}
    public void addConsumer(Consumer<String> consumer){
        lineEmittingStream.addConsumer(consumer);
    }
    public void removeConsumer(Consumer<String> consumer){
        lineEmittingStream.removeConsumer(consumer);
    }
    public void setCommand(Cmd command,CommandResult result){
        this.command = command;
        this.result = result;
    }
    public void ctrlC() {
        if (!channelShell.isOpen()) {
            isOpen = false;
            logger.error("Shell is not connected for ctrlC");
        } else {
            try {
                commandStream.write(3);//works for real ssh, not TestServer
                commandStream.flush();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    public String getOutput(){
        if(shellLock.availablePermits()>0){
            return shStream.toString();
        }else{
            try {
                shellLock.acquire();
                try{
                    return shStream.toString();
                }finally {
                    shellLock.release();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {

            }
        }
        return shStream.toString();
    }
    public void sh(String command){
        sh(command,true);
    }
    public void sh(String command,boolean acquireLock){
        //System.out.println("SshSesion.sh w/ lock="+acquireLock+" @ "+shellLock.availablePermits()+" command="+command);
        logger.trace("sh: {}, lock: {}",command,acquireLock);
        if(command==null){
            return;
        }
        if(!channelShell.isOpen()){
            isOpen = false;
            logger.error("Shell is not connected for "+command);
        } else {
            if(acquireLock){
                try {
                    shellLock.acquire();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Thread.interrupted();
                }
            }

            if(!command.isEmpty()){
                //filteredStream.addFilter("command",command,AsciiArt.ANSI_RESET+AsciiArt.ANSI_CYAN+command+AsciiArt.ANSI_RESET+AsciiArt.ANSI_MAGENTA);
                stripStream.addFilter("command",command,"");
            }
            shStream.reset();
            commandStream.println(command);
            commandStream.flush();
            logger.debug("{}@{} flushed {}",this.command==null?"?":this.command.getUid(),host.getHostName(),command);
            //BUG this includes the prompt and the output of the command, should filter up to the command
            //TODO test shStream reset before and after the command is sent (so sh output does not contain the command)
        }
    }
    public void close(){
        close(true);
    }
    public void close(boolean wait) {
        if (this.isOpen) {
            try {
                if (wait) {
                    try {
                        if(shellLock.availablePermits()<=0){
                            logger.info("{} still locked by {}",this.getHost(),this.command);
                        }
                        shellLock.acquire();

                        //do we really care about release?
                        //we are going to destroy the semaphore / shell
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        Thread.interrupted();
                    }
                }

                channelShell.getIn().close();
                channelShell.setIn(null);
                semaphoreStream.close();
                channelShell.setOut(null);
                channelShell.close();

                clientSession.close();
                sshClient.stop();

                this.isOpen = false;

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    //Called when the semaphore is released
    @Override
    public void run() {
        if(this.command !=null){
            Cmd thisCommand = this.command;
            this.command = null;
            this.result.next(thisCommand,shStream.toString());
        }
    }

    //Called when there is a new line of output
    @Override
    public void accept(String s) {
        if(result!=null && command!=null){
            result.update(command,s);
        }
    }
}
