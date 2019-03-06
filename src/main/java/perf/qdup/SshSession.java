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
import perf.qdup.stream.*;

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

    public String getLastCommand() {
        return lastCommand;
    }

    public void setLastCommand(String lastCommand) {
        this.lastCommand = lastCommand;
    }

    public enum Stream {Escape,Semaphore,Filtered,Prompt};

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
    private SuffixStream suffixStream;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        if(escapeFilteredStream!=null){
            escapeFilteredStream.setName(name);
        }
    }

    private String name="";
    private String lastCommand="";

    private boolean connected = false;
    long shStart = -1;
    long shStop = -1;

    public SshSession(Host host){
        this(host,DEFAULT_KNOWN_HOSTS,DEFAULT_IDENTITY,DEFAULT_PASSPHRASE, DEFAULT_SSH_TIMEOUT,"",null);
    }
    public SshSession(Host host,String knownHosts,String identity,String passphrase, int timeout,String setupCommand,ScheduledThreadPoolExecutor executor){

        this.host = host;
        this.name = host != null ? host.toString() : "null";
        this.knownHosts = knownHosts;
        this.identity = identity;
        this.passphrase = passphrase;
        this.timeout = timeout;

        shellLock = new Semaphore(1);
        sshClient = SshClient.setUpDefaultClient();

        PropertyResolverUtils.updateProperty(sshClient,ClientFactoryManager.IDLE_TIMEOUT,Long.MAX_VALUE);
        PropertyResolverUtils.updateProperty(sshClient,ClientFactoryManager.NIO2_READ_TIMEOUT,Long.MAX_VALUE);
        sshClient.start();
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
        connected = connect(-1,setupCommand);

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
        if(!lineObservers.isEmpty()){
            for(Consumer<String> consumer : lineObservers.values()){
                consumer.accept(line);
            }
        }
    }
    private void shConsumers(String output){

        for(Consumer<String> consumer : shObservers.values()){
            consumer.accept(output);
        }
    }
    public int permits(){
        return shellLock.availablePermits();
    }

    public boolean connect(long timeoutMillis,String setupCommand){
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
            clientSession.auth().verify(this.timeout * 1_000);
            //setup all the streams

            pipeIn = new PipedInputStream();
            pipeOut = new PipedOutputStream(pipeIn);
            //the output of the current sh command
            shStream = new ByteArrayOutputStream();

            commandStream = new PrintStream(pipeOut);

            escapeFilteredStream = new EscapeFilteredStream();
            filteredStream = new FilteredStream();

            suffixStream = executor==null ? new SuffixStream() : new SuffixStream(executor);
            promptStream = new SuffixStream();
            lineEmittingStream = new LineEmittingStream();

            escapeFilteredStream.addStream("semaphore", suffixStream);

            suffixStream.addStream("filtered",filteredStream);
            suffixStream.addStream("prompt-callback",promptStream);

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
                //shStream.reset();
                shConsumers(output);
                shellLock.release();
                if(permits()>1){
                    logger.error("ShSession "+getName()+" "+getLastCommand()+" release -> permits=="+permits()+"\n"+output);
                }
            };
            //lineEmittingStream.addConsumer(this::lineConsumers);

            suffixStream.addSuffix("PROMPT",PROMPT,"");
            suffixStream.addConsumer(this.semaphoreCallback);

            channelShell = clientSession.createShellChannel();
            channelShell.getPtyModes().put(PtyMode.ECHO,1);//need echo for \n from real SH but adds gargage chars for test :(
            channelShell.setPtyType("vt100");
            //channelShell.setPtyType("xterm");
            channelShell.setPtyColumns(10*1024);//hack to get around " \r" when line is longer than shell width
            channelShell.setPtyWidth(10*1024);//TODO add " \r" to the suffix stream?
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
            //shSync("pwd");
            sh("");//forces the thread to wait for the previous sh to complete
            if(setupCommand!=null && !setupCommand.isEmpty()){
                sh(setupCommand);
            }else {

            }
            //is this what is bugging out envTest?
            try {
                try{
                    shellLock.acquire();//technically should be before try{ for cases where acquire throws the exception
                    if(permits()!=0){
                        logger.error("ShSession "+getName()+" connect.acquire --> permits=="+permits());
                    }

                }finally{
                    shellLock.release();
                    if(permits()!=1){
                        logger.error("ShSession "+getName()+" connect.release --> permits=="+permits());
                    }
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
        //allow session to be fully setup before adding watcher support to lineEmittingStream
        if(lineEmittingStream!=null) {
            lineEmittingStream.addConsumer(this::lineConsumers);

        }
        return rtrn;
    }
    public void addStreamObserver(Stream target,String key,OutputStream stream){
        MultiStream targetStream = getTarget(target);
        if(targetStream!=null){
            targetStream.addStream(key,stream);
        }
    }
    public void removeStreamObserver(Stream target,String key){
        MultiStream targetStream = getTarget(target);
        if(targetStream!=null){
            targetStream.removeStream(key);
        }
    }
    private MultiStream getTarget(Stream target){
        MultiStream targetStream;
        switch (target){
            case Prompt:
                targetStream = promptStream;
                break;
            case Filtered:
                targetStream = filteredStream;
                break;
            case Semaphore:
                targetStream = suffixStream;
                break;
            case Escape:
            default:
                targetStream = escapeFilteredStream;
                break;
        }
        return targetStream;
    }
    public boolean isOpen(){
        boolean rtrn =channelShell!=null && channelShell.isOpen() && clientSession!=null && clientSession.isOpen();
        return rtrn;}

    public boolean usesDelay(){
        return this.suffixStream.usesExecutor();
    }
    public int getDelay(){
        return this.suffixStream.getExecutorDelay();
    }
    public void setDelay(int delay){
        this.suffixStream.setExecutorDelay(delay);
    }

    public boolean isConnected() {
        return connected;
    }

    public Host getHost(){return host;}
    protected int ctrlInt(char key) {
        return (Character.toUpperCase(key) & 0x1f);
    }
    public void ctrlC(){
        ctrl('C');//end process
    }
    public void ctrlZ(){
        ctrl('Z');//send to background
    }
    public void ctrl(char key) {
        if(isOpen()) {
            if (!channelShell.isOpen()) {
                logger.error("Shell is not connected for ctrlC");
            } else {
                try {
                    commandStream.write(ctrlInt(key));//b3 works for real qdup, not TestServer
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
            logger.error("Interrupted waiting for shSync "+command,e);
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
        lastCommand = command;
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
                        if(permits()!=0){
                            logger.error("ShSession "+getName()+" sh.acquire --> permits=="+permits());
                        }

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
//                if(acquireLock) {
//                    shStream.reset();
//                }
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
    public String peekOutputTail(){
        String rtrn = peekOutput().replaceAll("[\r\n]+$",""); //replace trailing newlines;
        if(rtrn.lastIndexOf("\n")>-1){
            rtrn = rtrn.substring(rtrn.lastIndexOf("\n"));
        }
        return rtrn;
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
                            logger.info("{} closing but shell still locked {}",this.getHost(),lastCommand);
                        }
                        shellLock.acquire();
                        if(permits()!=0){
                            logger.error("ShSession "+getName()+" close.acquire --> permits=="+permits());
                        }

                        //do we really care about release?
                        //we are going to destroy the semaphore / shell
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        Thread.interrupted();
                    }
                }

                channelShell.getIn().close();
                suffixStream.close();
                channelShell.close();

                clientSession.close();
                sshClient.stop();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
