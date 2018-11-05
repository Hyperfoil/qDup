package perf.qdup;


import org.apache.sshd.client.ClientFactoryManager;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.PropertyResolverUtils;
import org.apache.sshd.common.channel.Channel;
import org.apache.sshd.common.channel.ChannelListener;
import org.apache.sshd.common.channel.PtyMode;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import perf.qdup.stream.EscapeFilteredStream;
import perf.qdup.stream.FilteredStream;
import perf.qdup.stream.LineEmittingStream;
import perf.qdup.stream.SuffixStream;
import perf.yaup.AsciiArt;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static perf.qdup.config.RunConfigBuilder.*;

/**
 * Created by wreicher
 * Provides the remote connection to run shell commands and monitor the output.
 * Needs to be updated WITH the current Cmd, CommandResult before sending a command to the remote host
 */

//Todo separate out the PROMPT, the command, and the output of the command
public class SshSession {

    private static final String SH_CALLBACK = "qdup-sh-callback";
    private static final String SH_BLOCK_CALLBACK = "qdup-sh-block-callback";

    private static class ExecWatcher implements ChannelListener {

        Consumer<String> callback;
        ByteArrayOutputStream baos;

        public ExecWatcher(Runnable callback){
            this.callback = a->callback.run();
            this.baos = null;
        }
        public ExecWatcher(Consumer<String> callback,ByteArrayOutputStream baos){
            this.callback = callback;
            this.baos = baos;
        }
        @Override
        public void channelInitialized(Channel channel) {}

        @Override
        public void channelOpenSuccess(Channel channel) {}

        @Override
        public void channelOpenFailure(Channel channel, Throwable reason) {}

        @Override
        public void channelStateChanged(Channel channel, String hint) {}

        @Override
        public void channelClosed(Channel channel, Throwable reason) {
            String response = baos!=null ? baos.toString() : "";
            callback.accept(response);
        }
    }

    private static final AtomicInteger counter = new AtomicInteger();

    private static final XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

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

    private Host host;
    private String knownHosts;
    private String identity;
    private String passphrase;
    private int timeout;
    private Consumer<String> semaphoreCallback;
    private Semaphore blockingSemaphore;
    private Consumer<String> blockingConsumer;
    private StringBuffer blockingResponse;
    private Map<String,Consumer<String>> lineObservers;
    private Map<String,Consumer<String>> shObservers;
    private ScheduledThreadPoolExecutor executor;

    public SshSession(Host host){
        this(host,DEFAULT_KNOWN_HOSTS,DEFAULT_IDENTITY,DEFAULT_PASSPHRASE, DEFAULT_SSH_TIMEOUT,"",null);
    }
    public SshSession(Host host,String knownHosts,String identity,String passphrase, int timeout,String setupCommand,ScheduledThreadPoolExecutor executor){

        this.host = host;
        this.knownHosts = knownHosts;
        this.identity = identity;
        this.passphrase = passphrase;
        this.timeout = timeout;

        shellLock = new Semaphore(1);
        sshClient = SshClient.setUpDefaultClient();

        PropertyResolverUtils.updateProperty(sshClient,ClientFactoryManager.IDLE_TIMEOUT,Long.MAX_VALUE);
        PropertyResolverUtils.updateProperty(sshClient,ClientFactoryManager.NIO2_READ_TIMEOUT,Long.MAX_VALUE);
        long start = System.currentTimeMillis();
        sshClient.start();
        long stop = System.currentTimeMillis();
        System.out.println("sshClient.sshCLient.start="+(stop-start)+"ms");
        //StrictHostKeyChecking=no
        sshClient.setServerKeyVerifier((clientSession1,remoteAddress,serverKey)->{return true;});

        lineObservers = new ConcurrentHashMap<>();
        shObservers = new ConcurrentHashMap<>();

        //for shSync
        blockingSemaphore = new Semaphore(0);
        blockingResponse = new StringBuffer();
        blockingConsumer = (response)->{
            blockingResponse.setLength(0);
            blockingResponse.append(response);
            blockingSemaphore.release();
        };
        this.executor = executor;
        boolean ok = connect(-1,setupCommand);

//        sshConfig = new Properties();
//        sshConfig.put("StrictHostKeyChecking", "no");
    }

    public void addLineObserver(String name,Consumer<String> consumer){
        lineObservers.put(name,consumer);
    }
    public void removeLineObserver(String name){
        lineObservers.remove(name);
    }
    public void clearLineObservers(){
        lineObservers.clear();
    }
    public void addShObserver(String name,Consumer<String> consumer){
        shObservers.put(name,consumer);
    }
    public void removeShObserver(String name){
        shObservers.remove(name);
    }
    private void lineConsumers(String line){
        for(Consumer<String> consumer : lineObservers.values()){
            consumer.accept(line);
        }
    }
    private void shConsumers(String output){
        shObservers.forEach((name,consumer)->{
            consumer.accept(output);
        });
    }
    public int permits(){
        return shellLock.availablePermits();
    }

    public boolean connect(long timeoutMillis,String setupCommand){
        long start = System.currentTimeMillis();
        if(isOpen()){
            return true;
        }
        boolean rtrn = false;
        try {
            clientSession = sshClient.connect(host.getUserName(),host.getHostName(),host.getPort()).verify(this.timeout * 2_000).getSession();
            clientSession.addPublicKeyIdentity(SecurityUtils.loadKeyPairIdentity(
                    identity,
                    Files.newInputStream((new File(identity)).toPath()),
                    (resourceKey)->{return passphrase;}
            ));
            long authStart = System.currentTimeMillis();
            clientSession.auth().verify(this.timeout * 1_000);
            long authStop = System.currentTimeMillis();
            System.out.println("SshSession.clientSession.auth="+(authStop-authStart)+"ms");
            //setup all the streams

            long streamStart = System.currentTimeMillis();
            pipeIn = new PipedInputStream();
            pipeOut = new PipedOutputStream(pipeIn);
            //the output of the current sh command
            shStream = new ByteArrayOutputStream();

            commandStream = new PrintStream(pipeOut);

            escapeFilteredStream = new EscapeFilteredStream();
            filteredStream = new FilteredStream();

            semaphoreStream = executor==null ? new SuffixStream() : new SuffixStream(executor);
            promptStream = new SuffixStream();
            lineEmittingStream = new LineEmittingStream();

            escapeFilteredStream.addStream("semaphore",semaphoreStream);

            semaphoreStream.addStream("filtered",filteredStream);
            semaphoreStream.addStream("promptMonitor",promptStream);

            //move before opening connection or sending
            //was getting a ConcurrentModificationException with this after the setup sh() calls
            filteredStream.addStream("lines",lineEmittingStream);
            filteredStream.addStream("sh",shStream);


            filteredStream.addFilter("^C",new byte[]{0,0,0,3});
            filteredStream.addFilter("echo-^C","^C");
            filteredStream.addFilter("^D",new byte[]{0,0,0,4});
            filteredStream.addFilter("echo-^D","^D");
            filteredStream.addFilter("^P",new byte[]{0,0,0,16});
            filteredStream.addFilter("^T",new byte[]{0,0,0,20});
            filteredStream.addFilter("^X",new byte[]{0,0,0,24});
            filteredStream.addFilter("^@",new byte[]{0,0,0});

            semaphoreCallback = (name)->{
                filteredStream.flushBuffer();
                lineEmittingStream.forceEmit();
                String streamString = shStream.toString();

                String output = streamString
                    .replaceAll("^[\r\n]+","")  //replace leading newlines
                    .replaceAll("[\r\n]+$","") //replace trailing newlines
                    .replaceAll("\r\n","\n"); //change \r\n to just \n
                shStream.reset();
                shConsumers(output);
                shellLock.release();
            };
            lineEmittingStream.addConsumer(this::lineConsumers);

            semaphoreStream.addSuffix("PROMPT",PROMPT,"");
            semaphoreStream.addConsumer(this.semaphoreCallback);

            long streamStop = System.currentTimeMillis();
            System.out.println("SshSession.streams="+(streamStop-streamStart)+"ms");

            long channelStart = System.currentTimeMillis();
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
            long channelStop = System.currentTimeMillis();
            System.out.println("SshSession.channelShell="+(channelStop-channelStart)+"ms");
            long openStart = System.currentTimeMillis();
            if(timeoutMillis>0){
                channelShell.open().verify(timeoutMillis).isOpened();
            }else{
                channelShell.open().verify().isOpened();
            }
            long openStop = System.currentTimeMillis();
            System.out.println("SshSession.channelShell.open="+(openStop-openStart)+"ms");
            long syncStart = System.currentTimeMillis();
            sh("unset PROMPT_COMMAND; export PS1='" + PROMPT + "'");
            //shSync("pwd");
            sh("");//forces the thread to wait for the previous sh to complete
            if(setupCommand!=null && !setupCommand.isEmpty()){
                sh(setupCommand);
            }else {

            }
            long syncStop = System.currentTimeMillis();
            System.out.println("SshSession.shSync="+(syncStop-syncStart)+"ms");
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
        } catch (GeneralSecurityException | IOException e) {
            logger.error("Exception while connecting to {}@{}\n{}",host.getUserName(),host.getHostName(),e.getMessage(),e);
        } finally {
            logger.trace("{} session.isOpen={} shell.isOpen={}",
                    this.getHost().getHostName(),
                    clientSession==null?"false":clientSession.isOpen(),
                    channelShell==null?"false":channelShell.isOpen()
            );
            rtrn = isOpen();
        }
        long stop = System.currentTimeMillis();
        System.out.println("SshSession.connect="+(stop-start)+"ms");
        return rtrn;
    }
    public boolean isOpen(){
        boolean rtrn =channelShell!=null && channelShell.isOpen() && clientSession!=null && clientSession.isOpen();
        return rtrn;}

    public boolean usesDelay(){
        return this.semaphoreStream.usesExecutor();
    }
    public int getDelay(){
        return this.semaphoreStream.getExecutorDelay();
    }
    public void setDelay(int delay){
        this.semaphoreStream.setExecutorDelay(delay);
    }

    public Host getHost(){return host;}
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
        sh("reboot",false,null,null);
    }
    public String shSync(String command){
        return shSync(command,null);
    }
    public String shSync(String command, Map<String,String> prompt){
        addShObserver(SH_BLOCK_CALLBACK,blockingConsumer);
        sh(command,prompt);
        try {
            blockingSemaphore.acquire();
            removeShObserver(SH_BLOCK_CALLBACK);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return blockingResponse.toString();
    }
    public void sh(String command){
        sh(command,true,null,null);
    }
    public void sh(String command, Map<String,String> prompt){
        sh(command,true,null,prompt);
    }
    public void sh(String command, Consumer<String> callback){
        sh(command,true,callback,null);
    }
    public void sh(String command, Consumer<String> callback,Map<String,String> prompt){
        sh(command,true,callback,prompt);
    }
    private void sh(String command,boolean acquireLock,Consumer<String> callback, Map<String,String> prompt){
        logger.trace("{} sh: {}, lock: {}",host,command,acquireLock);
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
                        assert permits()==0;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        Thread.interrupted();
                    }
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
                //moved stream reset to after acquiring lock
                lineEmittingStream.reset();
                shStream.reset();
                removeShObserver(SH_CALLBACK);
                if(callback!=null){
                    addShObserver(SH_CALLBACK,callback);
                }
                if (!command.isEmpty()) {
                    filteredStream.addFilter("command", command, "");
                }
                if(acquireLock) {
                    shStream.reset();
                }
                commandStream.println(command);
                commandStream.flush();
            }
        }else{
            //TODO abort run if sh isn't open or try reconnect
            logger.error("Shell is not connected for "+command);
        }
    }
    public void exec(String command){
        exec(command,null);
    }
    public void exec(String command,Consumer<String> callback){
        if(isOpen()) {
            try {
                ChannelExec channelExec = clientSession.createExecChannel(command);
                if(callback!=null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ExecWatcher watcher = new ExecWatcher(callback,baos);
                    channelExec.setOut(baos);
                    channelExec.addChannelListener(watcher);
                }
                channelExec.open();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    public String peekOutput(){
        return shStream.toString();
    }
//    public String peekCleanedOutput(){
//        String output = shStream.toString()
//                .replaceAll("^[\r\n]+","")  //replace leading newlines
//                .replaceAll("[\r\n]+$","") //replace trailing newlines
//                .replaceAll("\r\n","\n"); //change \r\n to just \n
//        return output;
//    }
    public void close(){
        close(true);
    }
    public void close(boolean wait) {
        if (this.isOpen()) {
            try {
                if (wait) {
                    try {
                        if(shellLock.availablePermits()<=0){
                            logger.info("{} closing but shell still locked",this.getHost());
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
}
