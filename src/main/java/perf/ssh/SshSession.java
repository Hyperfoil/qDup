package perf.ssh;

import com.jcraft.jsch.*;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.CommandResult;
import perf.ssh.stream.FilteredStream;
import perf.ssh.stream.LineEmittingStream;
import perf.ssh.stream.SemaphoreStream;
import perf.util.AsciiArt;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

/**
 * Created by wreicher
 * Provides the remote connection to run shell commands and monitor the output.
 * Needs to be updated with the current Cmd, CommandResult before sending a command to the remote host
 */

//Todo separate out the prompt, the command, and the output of the command
public class SshSession implements Runnable, Consumer<String>{

    final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

    private static final String prompt = "#%@_ssh_!*> "; // a string unlikely to appear in the output of any command

    public static final String DEFAULT_KNOWN_HOSTS = "~/.ssh/known_hosts";
    public static final String DEFAULT_IDENTITY = "~/.ssh/id_rsa";

    private static String knownHosts = DEFAULT_KNOWN_HOSTS;
    private static String identity = DEFAULT_IDENTITY;
    private static String passphrase = null;

    private Session session;
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

    public SshSession(Host host){ this(host,new Semaphore(1)); }
    public SshSession(Host host,Semaphore semaphore){

        this.host = host;
        shellLock = semaphore;
        sshConfig = new Properties();
        sshConfig.put("StrictHostKeyChecking", "no");
        JSch jsch = new JSch();
        try {
            jsch.setKnownHosts(knownHosts);
            String passphrase = System.getProperty( "passphrase" );
            if ( passphrase == null ) {
                jsch.addIdentity( identity );
            } else {
                jsch.addIdentity( identity, passphrase );
            }
            session = jsch.getSession(host.getUserName(),host.getHostName(),host.getPort());
            session.setConfig(sshConfig);
            session.connect(5*60_000);

            if( !session.isConnected() ) {
                logger.error("failed to connect session for {}@{}",host.getUserName(),host.getHostName());
            }

            ChannelExec channelExec = (ChannelExec)session.openChannel("exec");

            channelShell = (ChannelShell)session.openChannel("shell");

            channelShell.setPty(true);
            channelShell.setPtySize(1024,80,1024,80);

            PipedInputStream pipeIn = new PipedInputStream();
            PipedOutputStream pipeOut = new PipedOutputStream(pipeIn);

            //the output of the current sh command
            shStream = new ByteArrayOutputStream();

            //the stream that sends commands to the channelSession
            commandStream = new PrintStream(pipeOut);

            channelShell.setInputStream(pipeIn);


            //the stream that receives the output of the chanelSession (including prompt and sent commands)
            semaphoreStream = new SemaphoreStream(shellLock,prompt.getBytes());

            channelShell.connect(5*60_000);
            if ( !channelShell.isConnected() ) {
                logger.error("failed to connect shell to {}",host.getHostName());
            }
            channelShell.setOutputStream(semaphoreStream,true);


            filteredStream = new FilteredStream();
            stripStream = new FilteredStream();

            //set the prompt so semaphoreStream will detect the expected prompt and release the lock to start sending commands
            sh("export PS1='" + prompt + "'");

            sh("");//forces the thread to wait for the previous sh to complete

            String newPrompt = AsciiArt.ANSI_RESET+AsciiArt.ANSI_RED+"$:"+AsciiArt.ANSI_RESET+AsciiArt.ANSI_BLACK;

            filteredStream.addFilter("prompt",prompt, "$:"/*newPrompt*/);//replace the unique string prompt with something more user friendly
            stripStream.addFilter("prompt","$:","");

            lineEmittingStream = new LineEmittingStream();
            lineEmittingStream.addConsumer(this);
            //semaphoreStream.addStream("sh",shStream); // echo the entire channelSession to sh output

            filteredStream.addStream("strip",stripStream); // echo the entire channelSession to sh output

            stripStream.addStream("lines",lineEmittingStream);
            stripStream.addStream("sh",shStream);

            semaphoreStream.addStream("filtered",filteredStream);
            semaphoreStream.setRunnable(this);

            try {
                shellLock.acquire();
                //moved release to finally block, check permits to ensure it was acquired
                //shellLock.release();//reset so the next call to aquire for sh(...) isn't blocked
            } catch (InterruptedException e) {
                logger.warn("{}@{} interrupted while waiting for initial prompt",host.getUserName(),host.getHostName());
                e.printStackTrace();
                Thread.interrupted();
            } finally {
                if(shellLock.availablePermits()<=0){
                    shellLock.release();
                }
            }

            //echoes the prompt to output if desired
            //filteredStream.write(prompt.getBytes());

            isOpen = true;

        } catch (JSchException|IOException e) {
            logger.error("Exception while connecting to {}@{}",host.getUserName(),host.getHostName(),e);
            isOpen = false;
        } finally {
            logger.debug("{} session.isConnected={} shell.isConnected={}",
                    this.getHostName(),
                    session.isConnected(),
                    channelShell==null?"false":channelShell.isConnected()
            );
        }
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
        if (!channelShell.isConnected()) {
            isOpen = false;
            logger.error("Shell is not connected for ctrlC");
        } else {
            try {
                //channelShell.sendSignal("2");
                commandStream.write(3);
                commandStream.flush();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    public String getOutput(){
        return shStream.toString();
    }
    public void sh(String command){
        sh(command,true);
    }
    public void sh(String command,boolean acquireLock){
        logger.trace("sh: {}, lock: {}",command,acquireLock);

        if(command==null){
            return;
        }
        if(!channelShell.isConnected()){
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
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        Thread.interrupted();
                    }
                }

                channelShell.getInputStream().close();
                channelShell.setInputStream(null);
                //semaphoreStream.removeStream("out");
                semaphoreStream.close();
                channelShell.setOutputStream(null);
                channelShell.disconnect();

                //channelShell.getSession().disconnect();

                session.disconnect();

                this.isOpen = false;

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    public Semaphore getShellLock(){return shellLock;}
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
