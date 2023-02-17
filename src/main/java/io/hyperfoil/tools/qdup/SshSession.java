package io.hyperfoil.tools.qdup;


import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.stream.MultiStream;
import io.hyperfoil.tools.qdup.stream.SessionStreams;
import io.hyperfoil.tools.yaup.AsciiArt;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.channel.Channel;
import org.apache.sshd.common.channel.ChannelListener;
import org.apache.sshd.common.channel.PtyMode;
import org.apache.sshd.common.kex.KexProposalOption;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.common.util.GenericUtils;
import org.apache.sshd.common.util.io.resource.URLResource;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.core.CoreModuleProperties;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Created by wreicher
 * Provides the remote connection to run shell commands and monitor the output.
 * Needs to be updated WITH the current Cmd, CommandResult before sending a command to the remote host
 */

//Todo separate out the PROMPT, the command, and the output of the command
public class SshSession {

    private static final AtomicInteger UID = new AtomicInteger();
    private final static AtomicReferenceFieldUpdater<SshSession,Status> statusUpdater = AtomicReferenceFieldUpdater.newUpdater(SshSession.class, Status.class,"status");
    private final static AtomicReferenceFieldUpdater<SshSession,ShAction> actionUpdater = AtomicReferenceFieldUpdater.newUpdater(SshSession.class, ShAction.class,"currentAction");
    public static final int MAX_RECONNECT_ATTEMPTS = 10;
    public static final int RECONNECT_RETRY_DELAY = 10_000;

    public String getLastCommand() {
        return lastCommand;
    }

    public void setLastCommand(String lastCommand) {
        this.lastCommand = lastCommand;
    }

    private static final String SH_CALLBACK = "qdup-sh-callback";
    private static final String SH_BLOCK_CALLBACK = "qdup-sh-block-callback";

    private static class ExecWatcher implements ChannelListener {

        Consumer<String> callback;
        ByteArrayOutputStream baos;
        String name;

        public ExecWatcher(String name, Runnable callback) {
            this.name = name;
            this.callback = a -> callback.run();
            this.baos = null;
        }

        public ExecWatcher(String name, Consumer<String> callback, ByteArrayOutputStream baos) {
            this.name = name;
            this.callback = callback;
            this.baos = baos;
        }

        @Override
        public void channelInitialized(Channel channel) {
        }

        @Override
        public void channelOpenSuccess(Channel channel) {
        }

        @Override
        public void channelOpenFailure(Channel channel, Throwable reason) {
            if (callback != null) {

            }
        }

        @Override
        public void channelStateChanged(Channel channel, String hint) {
        }

        @Override
        public void channelClosed(Channel channel, Throwable reason) {
            String response = baos != null ? baos.toString() : "";
            callback.accept(response);
        }
    }

    private static enum Status {
        Initializing("new connection"),
        Ready("ready"),
        Disconnected("disconnected"),
        Connecting("reconnecting"),
        Closing("closing");

        private String name;
        Status(String name){
            this.name = name;
        }
        public String getName(){return name;}
    }

    private class SessionWatcher implements ChannelListener {
        @Override
        public void channelInitialized(Channel channel) {
        }

        @Override
        public void channelOpenSuccess(Channel channel) {
        }

        @Override
        public void channelOpenFailure(Channel channel, Throwable reason) {
        }

        @Override
        public void channelStateChanged(Channel channel, String hint) {
        }

        @Override
        public void channelClosed(Channel channel, Throwable reason) {
            if(Status.Closing.equals(status)){

            }else{
                Status previousStatus = status;
                if(!Status.Connecting.equals(status)){
                    statusUpdater.set(SshSession.this,Status.Disconnected); //only change to disconnected if not connecting
                }
                //release any permits
                if(isActive()){
                    if(Status.Ready.equals(previousStatus)){
                        logger.warn("reconnect invoking semaphoreCallback due to active command during disconnect\n  command:"+currentAction.getCommand());
                        if(sessionStreams!=null) {
                            String output = getShOutput(true);
                            if(semaphoreCallback!=null){
                                semaphoreCallback.accept("SessionWatcher");
                            }
                            //callback(output);
                        }
                    }else{
                        if(permits() == 0) {
                            if(shellLock.hasQueuedThreads()){
                            }
                            actionUpdater.set(SshSession.this,null);
                            shellLock.release();
                        }
                        //ensureConnected();
                    }
                }else{
                    //ensureConnected();
                    //reconnect();
                }
            }
        }
    }

    private static class ShAction {
        private final String command;
        private final boolean acquireLock;
        private final BiConsumer<String,String> callback;
        private final Map<String,String> prompts;

        private ShAction(String command, boolean acquireLock, Consumer<String> callback, Map<String, String> prompts) {
            this(
                command,
                acquireLock,
                (output,promptName)->callback.accept(output),
                prompts
            );
        }
        private ShAction(String command, boolean acquireLock, BiConsumer<String,String> callback, Map<String, String> prompts) {
            this.command = command;
            this.acquireLock = acquireLock;
            this.callback = callback;
            this.prompts = prompts;
        }
        public String getCommand() {return command;}

        public boolean isAcquireLock() {return acquireLock;}

        public boolean hasCallback(){return callback!=null;}
        public BiConsumer<String,String> getCallback() {return callback;}

        public Map<String, String> getPrompts() {return prompts;}
    }

    private static final AtomicInteger counter = new AtomicInteger();

    private static final XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

    public static final String PROMPT = "<_#%@_qdup_@%#_> "; // a string unlikely to appear in the output of any command

    private SshClient sshClient;
    private ClientSession clientSession;
    private ChannelShell channelShell;

    //private Properties sshConfig;
    private PrintStream commandStream;
    private Semaphore shellLock;

    SessionStreams sessionStreams;

    private Host host; //
    private String knownHosts;
    private String identity;
    private String passphrase;
    private int  timeout;
    private String setupCommand;
    private boolean trace;

    private Consumer<String> semaphoreCallback;
    private Semaphore blockingSemaphore;
    private Consumer<String> blockingConsumer;
    private StringBuffer blockingResponse;
    private Map<String, Consumer<String>> lineObservers;
    private Map<String, BiConsumer<String,String>> shObservers;
    private ScheduledThreadPoolExecutor executor;

    private String name = "";
    private String lastCommand = "";

    private volatile Status status = Status.Initializing;
    private volatile ShAction currentAction = null;

    //private AtomicBoolean closed = new AtomicBoolean(false);
    private Semaphore connectingSemaphore = new Semaphore(1);
    private StampedLock connectingLock = new StampedLock();
    public SshSession(Host host) {
        this(host.getHostName(),host, RunConfigBuilder.DEFAULT_KNOWN_HOSTS, RunConfigBuilder.DEFAULT_IDENTITY, RunConfigBuilder.DEFAULT_PASSPHRASE, RunConfigBuilder.DEFAULT_SSH_TIMEOUT, "", null, false);
    }

    public SshSession(String name,Host host, String knownHosts, String identity, String passphrase, int timeout, String setupCommand, ScheduledThreadPoolExecutor executor, boolean trace) {

        this.host = host;
        this.name = name;
        this.knownHosts = knownHosts;
        this.identity = identity;
        this.passphrase = passphrase;
        this.timeout = timeout;
        this.setupCommand = setupCommand;
        this.trace = trace;
        shellLock = new Semaphore(1);

        lineObservers = new ConcurrentHashMap<>();
        shObservers = new ConcurrentHashMap<>();

        //for shSync
        blockingSemaphore = new Semaphore(0);
        blockingResponse = new StringBuffer();
        blockingConsumer = (response) -> {
            blockingResponse.setLength(0);
            blockingResponse.append(response);
            blockingSemaphore.release();
        };
        this.executor = executor;
        connect(this.timeout * 1_000, setupCommand, this.trace);
    }

    public String getName() {
        return name;
    }

    public boolean isActive(){return currentAction!=null;}
    public boolean isIdle(){return currentAction==null;}

    public boolean isTracing() {
        return sessionStreams.hasTrace();
    }

    public boolean hasName() {
        return name != null && !name.isEmpty();
    }

    public void setName(String name) {
        this.name = name;
        if (sessionStreams != null) { //can be null if this failed to connect
            sessionStreams.setName(name);
        }
    }
    public SshSession openCopy() {
        return new SshSession(getName(),host, knownHosts, identity, passphrase, timeout, setupCommand, executor, trace);
    }

    public void addLineObserver(String name, Consumer<String> consumer) {
        lineObservers.put(name, consumer);
    }

    public void removeLineObserver(String name) {
        lineObservers.remove(name);
    }

    public void clearLineObservers() {
        lineObservers.clear();
    }

    public boolean hasShObserver(String name){
        return shObservers.containsKey(name);
    }
    public void addShObserver(String name, Consumer<String> consumer) {
        addShObserver(name,(output,promptName)->consumer.accept(output));
    }
    public void addShObserver(String name, BiConsumer<String,String> consumer) {
        shObservers.put(name, consumer);
    }
    public void removeShObserver(String name) {
        shObservers.remove(name);
    }

    private void lineConsumers(String line) {
        if (!lineObservers.isEmpty()) {
            for (Consumer<String> consumer : lineObservers.values()) {
                consumer.accept(line);
            }
        }
    }

    public void addPrompt(String prommpt) {
        sessionStreams.addPrompt(prommpt);
    }

    private void shObservers(String output,String promptName) {
        shObservers.forEach((name,consumer)->{
            consumer.accept(output,promptName);
        });
    }

    public int permits() {
        return shellLock.availablePermits();
    }

    public String getShOutput(boolean flush){
        if(flush){
            sessionStreams.flushBuffer();
        }
        String streamString = sessionStreams.currentOutput();

        String output = streamString
                .replaceAll("^[\r\n]+", "")  //replace leading newlines
                .replaceAll("[\r\n]+$", "") //replace trailing newlines
                .replaceAll("\r\n", "\n"); //change \r\n to just \n
        return output;
    }

    public boolean connect(long timeoutMillis, String setupCommand, boolean trace) {
        Status previousStatus = status;
        if (isOpen()) {
            return true;
        }
        statusUpdater.set(this,Status.Connecting);
        logger.trace("{} connecting",this.getName());
        boolean rtrn = false;
        try {
            if(Status.Disconnected.equals(previousStatus)){
                logger.trace("{} connect was disconnected, stopping previous client and shell",this.getName());
                if(sshClient != null && sshClient.isStarted()){
                    sshClient.stop();
                }
                if(clientSession!=null && clientSession.isOpen()){
                    clientSession.close(true);
                    clientSession.waitFor(EnumSet.of(ClientSession.ClientSessionEvent.CLOSED),0L);
                }
                if(channelShell != null && channelShell.isOpen()){
                    channelShell.close(true);
                    channelShell.waitFor(EnumSet.of(ClientChannelEvent.CLOSED),0L);
                }
            }
            sshClient = SshClient.setUpDefaultClient();
            sshClient.addSessionListener(new SessionListener() {
                @Override
                public void sessionEstablished(Session session) {
                    logger.trace("{} client established",SshSession.this.getName());
                }

                @Override
                public void sessionCreated(Session session) {
                    logger.trace("{} client created",SshSession.this.getName());
                }

                @Override
                public void sessionDisconnect(Session session, int reason, String msg, String language, boolean initiator) {
                    logger.trace("{} client disconnected",SshSession.this.getName());
                }

                @Override
                public void sessionClosed(Session session) {
                    logger.trace("{} client disconnected",SshSession.this.getName());
                }
            });
            CoreModuleProperties.IDLE_TIMEOUT.set(sshClient, Duration.ofSeconds(7*24*3600));
            CoreModuleProperties.NIO2_READ_TIMEOUT.set(sshClient, Duration.ofSeconds(7*24*3600));
            CoreModuleProperties.NIO_WORKERS.set(sshClient, 1);
            // StrictHostKeyChecking=no
            //        sshConfig = new Properties();
            //        sshConfig.put("StrictHostKeyChecking", "no");
            sshClient.setServerKeyVerifier((clientSession1, remoteAddress, serverKey) -> {
                logger.trace("{} accept server key for {}",SshSession.this.getName(),remoteAddress);
                return true;
            });
            if(host.hasPassword()){
                logger.trace("{} setting client password provider {}",getName(),identity);
                sshClient.setPasswordIdentityProvider((sc) -> Arrays.asList(host.getPassword()));
            }
            if(passphrase != RunConfigBuilder.DEFAULT_PASSPHRASE){
                logger.trace("{} setting client passphrase",getName());
                sshClient.setFilePasswordProvider((SessionContext sessionContext, NamedResource namedResource, int i)->{
                    return passphrase;
                });
            }
            if(!RunConfigBuilder.DEFAULT_IDENTITY.equals(identity)){
                logger.trace("{} setting client identity {}",getName(),identity);
                URLResource urlResource = new URLResource(Paths.get(identity).toUri().toURL());
                try (InputStream inputStream = urlResource.openInputStream()) {
                    Iterable<KeyPair> keyPairs = SecurityUtils.loadKeyPairIdentities(
                            clientSession,
                            urlResource,
                            inputStream,
                            (session, resourceKey, retryIndex) -> passphrase
                    );
                    KeyPair keyPair = GenericUtils.head(keyPairs);
                    if(keyPair == null){
                        if(passphrase == RunConfigBuilder.DEFAULT_PASSPHRASE){
                            logger.error("{} cannot set client identity {} without a passphrase",getName(),identity);
                        }else{
                            logger.error("{} cannot set client identity {} using the provided passphrase",getName(),identity);
                        }
                        return false; // we failed to connect
                    }
                    sshClient.setKeyIdentityProvider((sessionContext -> {return keyPairs;}));
                }
            }
            sshClient.start();

            ConnectFuture future = sshClient.connect(host.getUserName(), host.getHostName(), host.getPort());
            future.await(10,TimeUnit.SECONDS);
            if(!future.isConnected()){
                logger.trace("{} client failed to connect before timeout",SshSession.this.getHost().getHostName());
                return false;
            }
            future = future.verify(this.timeout * 2_000);
            future.await(10,TimeUnit.SECONDS);
            if(!future.isConnected()){
                logger.trace("{} client failed to verify connection before timeout",SshSession.this.getHost().getHostName());
                return false;
            }
            clientSession = future.getSession();
            clientSession.addSessionListener(new SessionListener() {
                @Override
                public void sessionEstablished(Session session) {
                    logger.trace("{} session established",SshSession.this.getName());
                }

                @Override
                public void sessionCreated(Session session) {
                    logger.trace("{} session created",SshSession.this.getName());
                }

                @Override
                public void sessionPeerIdentificationReceived(Session session, String version, List<String> extraLines) {
                    logger.trace("{} session identification received",SshSession.this.getName());
                }

                @Override
                public void sessionNegotiationStart(Session session, Map<KexProposalOption, String> clientProposal, Map<KexProposalOption, String> serverProposal) {
                    logger.trace("{} session negotiation start",SshSession.this.getName());
                }

                @Override
                public void sessionNegotiationEnd(Session session, Map<KexProposalOption, String> clientProposal, Map<KexProposalOption, String> serverProposal, Map<KexProposalOption, String> negotiatedOptions, Throwable reason) {
                    logger.trace("{} session negotiation end",SshSession.this.getName());
                }

                @Override
                public void sessionEvent(Session session, Event event) {

                }

                @Override
                public void sessionException(Session session, Throwable t) {
                    logger.trace("{} session exception: {}",SshSession.this.getName(),t.getMessage());
                }

                @Override
                public void sessionDisconnect(Session session, int reason, String msg, String language, boolean initiator) {
                    logger.trace("{} session disconnect",SshSession.this.getName());
                }

                @Override
                public void sessionClosed(Session session) {
                    logger.trace("{} session closed",SshSession.this.getName());
                }
            });

            if(RunConfigBuilder.DEFAULT_PASSPHRASE==passphrase){
                logger.trace("{} using {} identity without passphrase",getName(),identity);
            }else{
                logger.trace("{} using {} identity with a passphrase",getName(),identity);
            }
            URLResource urlResource = new URLResource(Paths.get(identity).toUri().toURL());
            try (InputStream inputStream = urlResource.openInputStream()) {
                Iterable<KeyPair> keyPairs = SecurityUtils.loadKeyPairIdentities(
                        clientSession,
                        urlResource,
                        inputStream,
                        (session, resourceKey, retryIndex) -> passphrase
                );
                KeyPair keyPair = GenericUtils.head(keyPairs);
                if(keyPair == null){
                    if(passphrase == RunConfigBuilder.DEFAULT_PASSPHRASE){
                        logger.error("cannot connect {} using {} without a passphrase",getName(),identity);
                    }else{
                        logger.error("cannot connect {} using {} using the provided passphrase",getName(),identity);
                    }
                    return false; // we failed to connect
                }
                clientSession.addPublicKeyIdentity(keyPair);
            }
            if (host.hasPassword()) {
                logger.trace("{} adding password-identity",SshSession.this.getName());
                clientSession.addPasswordIdentity(host.getPassword());
            }
            //clientSession.auth().verify(this.timeout * 1_000);
            logger.trace("{} authenticating client session",getName());
            boolean sessionResponse = clientSession.auth().verify().await(this.timeout * 1_000);
            logger.trace("{} waiting for authentication",getName());
            clientSession.waitFor(EnumSet.of(ClientSession.ClientSessionEvent.AUTHED), 0L);

            //setup all the streams
            //the output of the current sh command
            if (sessionStreams != null) {
                sessionStreams.close();
            }
            sessionStreams = new SessionStreams(getName(), executor);
            semaphoreCallback = (name) -> {
                String output = getShOutput(true);
                //TODO use atomic boolean to set expecting response and check for true bfore release?
                if(permits() == 0) {
                    shellLock.release();
                    if (isTracing()) {
                        try {
                            sessionStreams.getTrace().write("RELEASE".getBytes());
                        } catch (IOException e) {
                        }
                    }
                }else{
                    //this should only happen if reconnected
                    logger.debug("skipping release, suspect reconnect");
                }
                shObservers(output,name);

                if (permits() > 1) {
                    logger.error("ShSession " + getName() + " " + getLastCommand() + " release -> permits==" + permits() + "\n" + output);
                    assert permits() == 1;
                }
            };
            sessionStreams.addPrompt(PROMPT, PROMPT, "");
            if(host.hasPrompt()){
                sessionStreams.addPrompt(host.getPrompt(),host.getPrompt(),"");
            }
            sessionStreams.addPromptCallback(this.semaphoreCallback);

            logger.trace("{} creating channel shell",getName());
            channelShell = clientSession.createShellChannel();

            channelShell.getPtyModes().put(PtyMode.ECHO, 1);//need echo for \n from real SH but adds gargage chars for test :(
            channelShell.setPtyType("vt100"); // channelShell.setPtyType("xterm");
            channelShell.setPtyColumns(10 * 1024);//hack to get around " \r" when line is longer than shell width
            channelShell.setPtyWidth(10 * 1024);//TODO add " \r" to the suffix stream?
            channelShell.setPtyHeight(80);
            channelShell.setPtyLines(80);
            channelShell.setUsePty(true);

            channelShell.setOut(sessionStreams);//efs or ss
            channelShell.setErr(sessionStreams);//PROMPT goes to error stream so have to listen there too
            channelShell.addChannelListener(new SessionWatcher());

            setTrace(trace);

            if (timeoutMillis > 0) {
                logger.trace("{} opening and verifying channel shell with {} timeout",getName(),timeoutMillis);
                boolean response = channelShell.open().verify().await(timeoutMillis);
                //channelShell.open().verify(timeoutMillis).isOpened();

            } else {
                logger.trace("{} opening and verifying channel shell",getName());
                channelShell.open().verify().isOpened();
            }
            commandStream = new PrintStream(channelShell.getInvertedIn());
            if(host.isSh()) {
                shConnecting("unset PROMPT_COMMAND; export PS1='" + PROMPT + "'; set +o history; export HISTCONTROL=\"ignoreboth\"");
            }
            if (setupCommand != null && !setupCommand.trim().isEmpty()) {
                shConnecting(setupCommand);
            } else {

            }
            //is this what is bugging out envTest?
            try {
                shellLock.acquire();//technically should be before try{ for cases where acquire throws the exception
                try {
                    if (permits() != 0) {
                        logger.error("ShSession " + getName() + " connect.acquire --> permits==" + permits());
                    }
                } finally {
                    shellLock.release();
                    if (permits() != 1) {
                        logger.error("ShSession " + getName() + " connect.release --> permits==" + permits());
                        assert permits() == 1;
                    }
                }
            } catch (InterruptedException e) {
                logger.warn("{}@{} interrupted while waiting for initial PROMPT", host.getUserName(), host.getHostName());
                Thread.interrupted();

            }
            if (sessionStreams != null) { //sessionStreams can be null if an exception was thrown trying to connect
                //allow session to be fully setup before adding watcher support to lineEmittingStream
                sessionStreams.addLineConsumer(this::lineConsumers);
            } else {
                logger.error("failed to setup terminal streams for {}", host);
            }
            statusUpdater.compareAndSet(this,Status.Connecting,Status.Ready);
            sessionStreams.flush(); //to remove any motd that may be in the stream
            sessionStreams.reset(); //to remove any motd that may be in the stream

        } catch (GeneralSecurityException e){
            logger.error("Security exception while connecting to {}@{} using {}\n{}",host.getUserName(),host.getHostName(),identity,e.getMessage(), e);
        } catch (IOException e) {
            //e.printStackTrace();
            logger.error("Exception while connecting to {}@{} using {}\n{}", host.getUserName(), host.getHostName(), identity, e.getMessage(), e);
        } finally {
            logger.trace("{} session.isOpen={} shell.isOpen={}",
                    this.getName(),
                    clientSession == null ? "false" : clientSession.isOpen(),
                    channelShell == null ? "false" : channelShell.isOpen()
            );
            rtrn = isOpen();
        }
        logger.trace("{} connect returning {}",
                this.getName(),
                rtrn
        );
        return rtrn;
    }
    public boolean waitForReady(){
        while(!Status.Ready.equals(status)){
//            try {
            logger.debug("getting connecting semaphore with status: "+status);
            long lock = connectingLock.readLock();
            connectingLock.unlockRead(lock);
//                connectingSemaphore.acquire();
//                connectingSemaphore.release();
//            } catch (InterruptedException e) {
//                //e.printStackTrace();
//            } finally {
//
//            }
        }
        return Status.Ready.equals(status);
    }
    public void setTrace(boolean trace) throws IOException {
        this.trace = trace;
        if (trace) {
            String path =  hasName() ? getName() : getHost().toString();
            sessionStreams.setTrace(path);
        }

    }
    public void markAborting(){
        statusUpdater.set(this,Status.Closing);
    }
    public boolean isOpen() {
        boolean rtrn = channelShell != null && channelShell.isOpen() && clientSession != null && clientSession.isOpen();
        return rtrn;
    }
    public boolean usesDelay() {
        return sessionStreams.getDelay() > 0;
    }

    public void flushAndResetBuffer(){
        try {
            sessionStreams.flush(); //to remove any motd that may be in the stream
            sessionStreams.reset(); //to remove any motd that may be in the stream
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getDelay() {
        return sessionStreams.getDelay();
    }
    public void setDelay(int delay) {
        sessionStreams.setDelay(delay);
    }
    public boolean isReady() {
        return Status.Ready.equals(status);
    }
    public Host getHost() {
        return host;
    }
    protected int ctrlInt(char key) {
        return (Character.toUpperCase(key) & 0x1f);
    }
    public void ctrlC() {
        ctrl('C'); //SIGINT
    }

    public void ctrlU() {
        ctrl('U'); //SIGQUIT
    }

    public void ctrlZ() {
        ctrl('Z'); //send to background
    }

    public void ctrl(char key) {
        if (isOpen()) {
            if (!channelShell.isOpen()) {
                logger.error("Shell is not connected for ctrlC");
            } else {
                try {
                    commandStream.write(ctrlInt(key));//b3 works for real qdup, not TestServer
                    commandStream.flush();
                } catch (Exception e) {
                }
            }
        } else {
            logger.error("Shell is not connected for ctrlC");
        }
    }

    public void response(String command) {
        if (ensureConnected()) {
            try {
                TimeUnit.SECONDS.sleep(1);
                commandStream.println(command);
                commandStream.flush();
            } catch (Exception e) {
                //fire and forget
            }
        } else {
            logger.error("Shell is not connected for response "+command);
        }
    }

    //has to NOT acquire the lock
    public void reboot() {
        sh("reboot", false, (BiConsumer)null, null);
    }

    public String shSync(String command) {

        return shSync(command, null);
    }

    public String shSync(String command, Map<String, String> prompt) {
        if(Status.Closing.equals(status)){
            return "";
        }
        addShObserver(SH_BLOCK_CALLBACK, blockingConsumer);
        if (blockingSemaphore.availablePermits() != 0 ){
            logger.error("ERROR: blockingSemaphorePermits = {}\n  command = {}",blockingSemaphore.availablePermits(),command);
        }
        sh(command, prompt);
        try {
            blockingSemaphore.acquire();//released in the observer
        } catch (InterruptedException e) {
            logger.error("Interrupted waiting for shSync " + command, e);
            Thread.interrupted();
        } finally {
            removeShObserver(SH_BLOCK_CALLBACK);
        }
        assert blockingSemaphore.availablePermits() == 0;
        return blockingResponse.toString();
    }

    public void callback(String input,String name){
        ShAction target = currentAction;
        if(target!=null) {
            if (actionUpdater.compareAndSet(this, currentAction, null) ) {
                if(target.hasCallback()){
                    target.callback.accept(input,name);
                }
            }else{
                logger.warn("failed to perform callback for "+target.getCommand());
            }
        }
    }

    private void shConnecting(String command){
        Semaphore semaphore = new Semaphore(0);
        BiConsumer<String,String> consumer = (response,promptName) -> {
            String output = getShOutput(true);
            semaphore.release();
        };
        sh(command,false, consumer,null);
        try{
            semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    public void sh(String command) {
        sh(command, true,  (BiConsumer)null, null);
    }

    public void sh(String command, Map<String, String> prompt) {
        sh(command, true, (BiConsumer)null, prompt);
    }

    public void sh(String command, Consumer<String> callback) {

        sh(command, true, (output,promptName)->callback.accept(output), null);
    }
    public void sh(String command, BiConsumer<String,String> callback) {
        sh(command, true, callback, null);
    }

    public void sh(String command, Consumer<String> callback, Map<String, String> prompt) {
        sh(command, true, (output,promptName)->callback.accept(output), prompt);
    }
    public void sh(String command, BiConsumer<String,String> callback, Map<String, String> prompt) {
        sh(command,true,callback,prompt);
    }
    private void sh(String command, boolean acquireLock, BiConsumer<String,String> callback, Map<String, String> prompt) {
        command = command.replaceAll("[\r\n]+$", ""); //replace trailing newlines
        logger.trace("{} sh: {}, lock: {}", host, command, acquireLock);
        ShAction newAction = new ShAction(command,acquireLock,callback,prompt);
        lastCommand = command;
        if (command == null) {
            return;
        }
            if (acquireLock) {
                try {
                    shellLock.acquire();
                    if (permits() != 0) {
                        logger.error("ShSession " + getName() + "cmd=" + command + " sh.acquire --> permits==" + permits());
                        assert permits() == 0;
                    }
                } catch (InterruptedException e) {
                    logger.error("interrupted acquiring shellLock for " + getName() + "@" + getHost());
                    Thread.interrupted();
                }
                sessionStreams.clearInline();
                if (prompt != null && !prompt.isEmpty()) {
                    sessionStreams.addInlinePrompts(prompt.keySet(), (name) -> {
                        if (prompt.containsKey(name)) {
                            String response = prompt.get(name);
                            if(response.startsWith("^") && response.length()==2){
                              ctrl(response.charAt(1));
                            } else {
                                this.response(response);
                            }
                        }
                    });
                }
            }
            if(sessionStreams!=null){
                //moved stream reset to after acquiring lock
                sessionStreams.reset();
            }
            removeShObserver(SH_CALLBACK);
            if (callback != null) {
                //addShObserver(SH_CALLBACK, callback);
                addShObserver(SH_CALLBACK, this::callback);
            }
            boolean sendCommand = ensureConnected();
            if (sendCommand) {
                if (!command.isEmpty()) {
                    // race between this and FilteredStream.write before we changed FilterStream to copy the keys into a new Set
                    // Are we releasing the lock too soon in the stream chain?
                    // test with a stream that sleeps in the write?
                    sessionStreams.setCommand(command);
                }
                actionUpdater.set(this,newAction);
                commandStream.println(command);
                commandStream.flush();
            } else {
                //TODO abort run if sh isn't open or try reconnect
                logger.error("Shell is not connected for " + command);
            }

    }

    public boolean ensureConnected() {
        boolean rtrn = false;
        if (isOpen()) {
            return true;
        }

        if (Status.Disconnected.equals(status)){
                //connectingSemaphore.acquire();
                long lock = connectingLock.writeLock();
                synchronized (this) {
                        try {
                            if (Status.Disconnected.equals(status)) { //double check status before proceeding with
                                rtrn = reconnect();
                            }
                        } finally {
                            //connectingSemaphore.release();
                            connectingLock.unlockWrite(lock);
                        }

                }

        }
        if (Status.Connecting.equals(status)){
            waitForReady();
        }
        return isOpen();
    }
    private boolean reconnect(){
        if(!isOpen() && Status.Disconnected.equals(status)){
            int attempts = 0;
            do {
                try {
                    connect(this.timeout * 1_000, setupCommand, this.trace);
                }finally {
                    attempts++;
                    if(!isOpen() || !isReady()){
                        try {
                            Thread.sleep(RECONNECT_RETRY_DELAY);
                        } catch (InterruptedException e) {
                            //e.printStackTrace();
                        }
                    }else{
                    }
                }
            } while ( (!isReady() || !isOpen()) && attempts < MAX_RECONNECT_ATTEMPTS);
        }
        return isOpen() && isReady();
    }

    public String execSync(String command) {
        Semaphore semaphore = new Semaphore(0);
        StringBuilder sb = new StringBuilder();
        exec(command, (output) -> {
            sb.append(output);
            semaphore.release();
        });
        try {
            assert semaphore.availablePermits() == 0;
            semaphore.acquire();
        } catch (InterruptedException e) {
        }
        return sb.toString();
    }

    public void exec(String command) {
        exec(command, null);
    }

    public void exec(String command, Consumer<String> callback) {
        if (isOpen()) {
            try {
                ChannelExec channelExec = clientSession.createExecChannel(command);
                if (callback != null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ExecWatcher watcher = new ExecWatcher(command, callback, baos);
                    MultiStream stream = new MultiStream();
                    stream.addStream("baos", baos);
                    //stream.addStream("sout", System.err);
                    channelExec.setOut(stream);
                    //channelExec.setErr(baos); //added to try and catch echo output
                    channelExec.addChannelListener(watcher);
                }
                channelExec.open().verify(9L, TimeUnit.SECONDS);
            } catch (IOException e) {
            }
        }
    }

    public String peekOutput() {
        return sessionStreams.currentOutput();
    }

    public String peekOutputTail() {
        String rtrn = peekOutput().replaceAll("[\r\n]+$", ""); //replace trailing newlines;
        if (rtrn.lastIndexOf("\n") > -1) {
            rtrn = rtrn.substring(rtrn.lastIndexOf("\n"));
        }
        return rtrn;
    }

    public void close() {
        close(true);
    }

    public boolean close(boolean wait) {
        if (this.isOpen()) {
            try {
                if (wait) {
                    try {
                        if (shellLock.availablePermits() <= 0) {
                            logger.info("{} closing but shell still locked {}", this.getHost(), lastCommand);
                        }
                        shellLock.acquire();
                        if (permits() != 0) {
                            logger.error("ShSession " + getName() + " close.acquire --> permits==" + permits());
                            assert permits() == 0;
                        }
                        //do we really care about release?
                        //we are going to destroy the semaphore / shell
                    } catch (InterruptedException e) {
                        Thread.interrupted();
                    }
                }
                statusUpdater.set(this,Status.Closing);
                sessionStreams.close();
                channelShell.close();
                clientSession.close();
                sshClient.stop();
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        } else {
        }
        return true;
    }
}
