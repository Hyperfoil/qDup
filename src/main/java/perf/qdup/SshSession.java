package perf.qdup;


import org.apache.sshd.client.ClientFactoryManager;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.PropertyResolverUtils;
import org.apache.sshd.common.channel.PtyMode;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.CommandResult;
import perf.qdup.stream.*;


import java.io.*;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static perf.qdup.config.RunConfigBuilder.DEFAULT_IDENTITY;
import static perf.qdup.config.RunConfigBuilder.DEFAULT_KNOWN_HOSTS;
import static perf.qdup.config.RunConfigBuilder.DEFAULT_PASSPHRASE;

/**
 * Created by wreicher
 * Provides the remote connection to run shell commands and monitor the output.
 * Needs to be updated WITH the current Cmd, CommandResult before sending a command to the remote host
 */

//Todo separate out the PROMPT, the command, and the output of the command
public class SshSession implements Runnable, Consumer<String>{

    private static final AtomicInteger counter = new AtomicInteger();

    final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

    public static final String PROMPT = "<_#%@_qdup_@%#_> "; // a string unlikely to appear in the output of any command

    private SshClient sshClient;
    private ClientSession clientSession;
    private ChannelShell channelShell;

    private Properties sshConfig;

    private PrintStream commandStream;
    private ByteArrayOutputStream shStream;

    private Semaphore shellLock;

    private PipedInputStream pipeIn;
    private PipedOutputStream pipeOut;

    private EscapeFilteredStream escapeFilteredStream;
    private SuffixStream semaphoreStream;
    private FilteredStream filteredStream;
    private SuffixStream promptStream;

    private LineEmittingStream lineEmittingStream;

    private BlockingQueue<String> outputQueue;

    private Cmd command;
    private CommandResult result;

    private Host host;
    private String knownHosts;
    private String identity;
    private String passphrase;

    public SshSession(Host host){
        this(host,DEFAULT_KNOWN_HOSTS,DEFAULT_IDENTITY,DEFAULT_PASSPHRASE);
    }
    public SshSession(Host host,String knownHosts,String identity,String passphrase){

        this.host = host;
        this.knownHosts = knownHosts;
        this.identity = identity;
        this.passphrase = passphrase;

        shellLock = new Semaphore(1);
        sshClient = SshClient.setUpDefaultClient();

        PropertyResolverUtils.updateProperty(sshClient,ClientFactoryManager.IDLE_TIMEOUT,Long.MAX_VALUE);
        PropertyResolverUtils.updateProperty(sshClient,ClientFactoryManager.NIO2_READ_TIMEOUT,Long.MAX_VALUE);

        sshClient.start();
        //StrictHostKeyChecking=no
        sshClient.setServerKeyVerifier((clientSession1,remoteAddress,serverKey)->{return true;});


        connect(-1);

//        sshConfig = new Properties();
//        sshConfig.put("StrictHostKeyChecking", "no");
    }

    public int permits(){
        return shellLock.availablePermits();
    }

    public boolean connect(long timeoutMillis){

        if(isOpen()){
            return true;
        }
        boolean rtrn = false;

        try {
            clientSession = sshClient.connect(host.getUserName(),host.getHostName(),host.getPort()).verify(10_000).getSession();
            clientSession.addPublicKeyIdentity(SecurityUtils.loadKeyPairIdentity(
                    identity,
                    Files.newInputStream((new File(identity)).toPath()),
                    (resourceKey)->{return passphrase;}
            ));

            clientSession.auth().verify(5_000);

            //setup all the streams
            pipeIn = new PipedInputStream();
            pipeOut = new PipedOutputStream(pipeIn);
            //the output of the current sh command
            outputQueue = new LinkedBlockingQueue<>();
            shStream = new ByteArrayOutputStream();

            commandStream = new PrintStream(pipeOut);

            escapeFilteredStream = new EscapeFilteredStream();
            filteredStream = new FilteredStream();

            semaphoreStream = new SuffixStream();
            promptStream = new SuffixStream();
            lineEmittingStream = new LineEmittingStream();

            escapeFilteredStream.addStream("semaphore",semaphoreStream);

            semaphoreStream.addStream("filtered",filteredStream);
            //
            //TODO removed for debugging, at it back or we break su
            //semaphoreStream.addStream("promptMonitor",promptStream);


            //filteredStream.addFilter("PROMPT","\n"+ PROMPT);
            filteredStream.addFilter("^C",new byte[]{0,0,0,3});
            filteredStream.addFilter("echo-^C","^C");
            filteredStream.addFilter("^P",new byte[]{0,0,0,16});
            filteredStream.addFilter("^T",new byte[]{0,0,0,20});
            filteredStream.addFilter("^X",new byte[]{0,0,0,24});
            filteredStream.addFilter("^@",new byte[]{0,0,0});

            lineEmittingStream.addConsumer(this);

            //semaphoreStream.addSuffix("\r\nPROMPT","\r\n"+PROMPT,"");
            semaphoreStream.addSuffix("PROMPT",PROMPT,"");
            semaphoreStream.addConsumer((name)-> this.run());

            channelShell = clientSession.createShellChannel();
            channelShell.getPtyModes().put(PtyMode.ECHO,1);//need echo for \n from real SH but adds gargage chars for test :(
            channelShell.setPtyType("vt100");
            //channelShell.setPtyType("xterm");
            channelShell.setPtyColumns(1024);
            channelShell.setPtyWidth(1024);
            channelShell.setPtyHeight(80);
            channelShell.setPtyLines(80);
            channelShell.setUsePty(true);

            channelShell.setIn(pipeIn);

            channelShell.setOut(escapeFilteredStream);//efs or ss
            channelShell.setErr(escapeFilteredStream);//PROMPT goes to error stream so have to listen there too



            if(timeoutMillis>0){
                channelShell.open().verify(timeoutMillis).isOpened();
            }else{
                channelShell.open().verify().isOpened();
            }

            sh("unset PROMPT_COMMAND; export PS1='" + PROMPT + "'");
            sh("pwd");

            sh("");//forces the thread to wait for the previous sh to complete

            //is this what is bugging out envTest?
            try {
                try{
                    shellLock.acquire();//technically should be before try{ for cases where acquire throws the exception
                }finally{
                    shellLock.release();
                }
            } catch (InterruptedException e) {
                logger.warn("{}@{} interrupted while waiting for initial PROMPT",host.getUserName(),host.getHostName());
                e.printStackTrace();
                Thread.interrupted();
            }

            filteredStream.addStream("lines",lineEmittingStream);
            filteredStream.addStream("sh",shStream);

        } catch (GeneralSecurityException | IOException e) {
            logger.error("Exception while connecting to {}@{}\n{}",host.getUserName(),host.getHostName(),e.getMessage());
        } finally {
            logger.debug("{} session.isOpen={} shell.isOpen={}",
                    this.getHostName(),
                    clientSession==null?"false":clientSession.isOpen(),
                    channelShell==null?"false":channelShell.isOpen()
            );
            rtrn = isOpen();
        }

        return rtrn;
    }
    public boolean isOpen(){return channelShell!=null && channelShell.isOpen() && clientSession!=null && clientSession.isOpen();}
    public String getUserName(){return host.getUserName();}
    public String getHostName(){return host.getHostName();}
    public Host getHost(){return host;}
    public void addConsumer(Consumer<String> consumer){
        lineEmittingStream.addConsumer(consumer);
    }
    public void removeConsumer(Consumer<String> consumer){
        lineEmittingStream.removeConsumer(consumer);
    }
    public void clearCommand(){
        this.command=null;
        this.result = null;
    }
    private void setCommand(Cmd command,CommandResult result){
        logger.trace("{} setCommand={}",host,command);
        this.command = command;
        this.result = result;
    }
    public void ctrlC() {
        if(isOpen()) {
            if (!channelShell.isOpen()) {
                logger.error("Shell is not connected for ctrlC");
            } else {
                try {
                    commandStream.write(3);//works for real qdup, not TestServer
                    commandStream.flush();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }else{
            logger.error("Shell is not connected for ctrlC");
        }
    }
    public String getOutput(){
        String rtrn = "";
        if(isOpen()) {
            try {
                synchronized (outputQueue) {
                    rtrn = outputQueue.take();
                    outputQueue.put(rtrn);
                }
            } catch (InterruptedException e) {
                //TODO handle interruption of getOutput
                //e.printStackTrace();
            }
        }
        return rtrn;
    }
    public void response(String command) {
        if(isOpen()) {
            if (!channelShell.isOpen()) {
                logger.error("Shell is not connected for response");
            } else {
                try {
                    TimeUnit.SECONDS.sleep(1);
                    commandStream.println((command));
                    commandStream.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }else{
            logger.error("Shell is not connected for response");
            //TODO abort because session isn't open?
        }
    }
    //has to NOT acquire the lock
    public void reboot(){
        sh("reboot",false,null,null,null);
    }
    public void sh(String command){
        sh(command,true,null,null,null);
    }
    public void sh(String command, Map<String,String> prompt){
        sh(command,true,null,null,prompt);
    }
    public void sh(String command, Cmd cmd,CommandResult result){
        sh(command,true,cmd,result,null);
    }
    public void sh(String command, Cmd cmd,CommandResult result,Map<String,String> prompt){
        sh(command,true,cmd,result,prompt);
    }
    private void sh(String command,boolean acquireLock,Cmd cmd, CommandResult result, Map<String,String> prompt){
        logger.trace("{} sh: {}, lock: {}",host,command,acquireLock);
        lineEmittingStream.reset();
        if(isOpen()) {
            if (command == null) {
                return;
            }
            if (!channelShell.isOpen()) {
                logger.error("Shell is not connected for " + command);
                //TODO fail the run or reconnect

            } else {
                if (acquireLock) {
                    try {
                        shellLock.acquire();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        Thread.interrupted();
                    }
                    setCommand(cmd, result);

                    promptStream.clear();
                    promptStream.clearConsumers();
                    if(prompt!=null && !prompt.isEmpty()){
                        prompt.keySet().forEach(promptStream::addSuffix);
                        promptStream.addConsumer((name)->{
                            if(prompt.containsKey(name)){
                                String response = prompt.get(name);
                                this.response(response);
                            }
                        });

                    }
                }

                if (!command.isEmpty()) {
                    filteredStream.addFilter("command", command, "");
                }
                if(acquireLock) {
                    synchronized (outputQueue) {
                        outputQueue.clear();
                        shStream.reset();
                    }
                }
                commandStream.println(command);
                commandStream.flush();
                logger.debug("{}@{} flushed {}", this.command == null ? "?" : this.command.getUid(), host.getHostName(), command);
            }
        }else{
            //TODO abort run if sh isn't open or try reconnect
            logger.error("Shell is not connected for "+command);
        }
    }
    public String peekOutput(){
        return shStream.toString();
    }
    public void close(){
        close(true);
    }
    public void close(boolean wait) {
        if (this.isOpen()) {
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
                semaphoreStream.close();
                channelShell.close();

                clientSession.close();
                sshClient.stop();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    //Called when the semaphore is released
    @Override
    public void run() {
        filteredStream.flushBuffer();
        lineEmittingStream.forceEmit();
        String output = shStream.toString()
                .replaceAll("^[\r\n]+","")  //replace leading newlines
                .replaceAll("[\r\n]+$","") //replace trailing newlines
                .replaceAll("\r\n","\n"); //change \r\n to just \n

        shStream.reset();
        try {
            outputQueue.put(output);
        } catch (InterruptedException e) {
            //TODO handle interruption of run
        }
        if(this.command !=null){
            Cmd thisCommand = this.command;

            this.command = null;
            thisCommand.setOutput(output);
            if(this.result!=null) {
                this.result.next(thisCommand, output);
            }
        }else{
            logger.trace("{} cmd = null ",host);
        }
        shellLock.release();
    }

    //Called when there is a new line of output
    @Override
    public void accept(String s) {
        if(result!=null && command!=null){
            result.update(command,s);
        }
    }
}
