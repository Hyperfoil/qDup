package io.hyperfoil.tools.qdup;


import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.stream.EscapeFilteredStream;
import io.hyperfoil.tools.qdup.stream.FilteredStream;
import io.hyperfoil.tools.qdup.stream.LineEmittingStream;
import io.hyperfoil.tools.qdup.stream.MultiStream;
import io.hyperfoil.tools.qdup.stream.SessionStreams;
import io.hyperfoil.tools.qdup.stream.SuffixStream;
import io.hyperfoil.tools.yaup.AsciiArt;
import org.apache.sshd.client.ClientFactoryManager;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.future.OpenFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.PropertyResolverUtils;
import org.apache.sshd.common.channel.Channel;
import org.apache.sshd.common.channel.ChannelListener;
import org.apache.sshd.common.channel.PtyMode;
import org.apache.sshd.common.future.SshFutureListener;
import org.apache.sshd.common.util.GenericUtils;
import org.apache.sshd.common.util.io.resource.URLResource;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Created by wreicher
 * Provides the remote connection to run shell commands and monitor the output.
 * Needs to be updated WITH the current Cmd, CommandResult before sending a command to the remote host
 */

//Todo separate out the PROMPT, the command, and the output of the command
public class SshSession {

    private static final AtomicInteger UID = new AtomicInteger();

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

    private static final AtomicInteger counter = new AtomicInteger();

    private static final XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

    public static final String PROMPT = "<_#%@_qdup_@%#_> "; // a string unlikely to appear in the output of any command

    private SshClient sshClient;
    private ClientSession clientSession;
    private ChannelShell channelShell;

    private Properties sshConfig;

    private PrintStream commandStream;

    private Semaphore shellLock;

    SessionStreams sessionStreams;

    private Host host;
    private String knownHosts;
    private String identity;
    private String passphrase;
    private int timeout;
    private String setupCommand;
    private boolean trace;

    private Consumer<String> semaphoreCallback;
    private Semaphore blockingSemaphore;
    private Consumer<String> blockingConsumer;
    private StringBuffer blockingResponse;
    private Map<String, Consumer<String>> lineObservers;
    private Map<String, Consumer<String>> shObservers;
    private ScheduledThreadPoolExecutor executor;

    public String getName() {
        return name;
    }

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

    private String name = "";
    private String lastCommand = "";

    private boolean connected = false;
    long shStart = -1;
    long shStop = -1;

    public SshSession(Host host) {
        this(host, RunConfigBuilder.DEFAULT_KNOWN_HOSTS, RunConfigBuilder.DEFAULT_IDENTITY, RunConfigBuilder.DEFAULT_PASSPHRASE, RunConfigBuilder.DEFAULT_SSH_TIMEOUT, "", null, false);
    }

    public SshSession(Host host, String knownHosts, String identity, String passphrase, int timeout, String setupCommand, ScheduledThreadPoolExecutor executor, boolean trace) {

        this.host = host;
        this.name = host != null ? host.toString() : "null";
        this.knownHosts = knownHosts;
        this.identity = identity;
        this.passphrase = passphrase;
        this.timeout = timeout;
        this.setupCommand = setupCommand;
        this.trace = trace;
        this.trace = true;

        shellLock = new Semaphore(1);

        sshClient = SshClient.setUpDefaultClient();

        PropertyResolverUtils.updateProperty(sshClient, ClientFactoryManager.IDLE_TIMEOUT, Long.MAX_VALUE);
        PropertyResolverUtils.updateProperty(sshClient, ClientFactoryManager.NIO2_READ_TIMEOUT, Long.MAX_VALUE);
        PropertyResolverUtils.updateProperty(sshClient, ClientFactoryManager.NIO_WORKERS, 1);

        // StrictHostKeyChecking=no
        //        sshConfig = new Properties();
        //        sshConfig.put("StrictHostKeyChecking", "no");
        sshClient.setServerKeyVerifier((clientSession1, remoteAddress, serverKey) -> {
            return true;
        });

        sshClient.start();

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
        connected = connect(this.timeout * 1_000, setupCommand, this.trace);

    }

    public SshSession openCopy() {
        return new SshSession(host, knownHosts, identity, passphrase, timeout, setupCommand, executor, trace);
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

    public void addShObserver(String name, Consumer<String> consumer) {
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

    private void shConsumers(String output) {
        shObservers.forEach((name,consumer)->{
            consumer.accept(output);
        });
    }

    public int permits() {
        return shellLock.availablePermits();
    }

    public boolean connect(long timeoutMillis, String setupCommand, boolean trace) {
        if (isOpen()) {
            return true;
        }
        boolean rtrn = false;

        try {
            clientSession = sshClient.connect(host.getUserName(), host.getHostName(), host.getPort()).verify(this.timeout * 2_000).getSession();
            URLResource urlResource = new URLResource(Paths.get(identity).toUri().toURL());

            try (InputStream inputStream = urlResource.openInputStream()) {
                clientSession.addPublicKeyIdentity(GenericUtils.head(SecurityUtils.loadKeyPairIdentities(
                        clientSession,
                        urlResource,
                        inputStream,
                        (session, resourceKey, retryIndex) -> passphrase
                )));
            }
            if (host.hasPassword()) {
                clientSession.addPasswordIdentity(host.getPassword());

            }
            clientSession.auth().verify(this.timeout * 1_000);
            //setup all the streams

            //the output of the current sh command
            if (sessionStreams != null) {
                sessionStreams.close();
            }

            sessionStreams = new SessionStreams(getName(), executor);

            semaphoreCallback = (name) -> {
                sessionStreams.flushBuffer();
                String streamString = sessionStreams.currentOutput();

                String output = streamString
                        .replaceAll("^[\r\n]+", "")  //replace leading newlines
                        .replaceAll("[\r\n]+$", "") //replace trailing newlines
                        .replaceAll("\r\n", "\n"); //change \r\n to just \n



                //TODO use atomic boolean to set expecting response and check for true bfore release?
                shellLock.release();

                //shellLock.release so we can use shSync in consumer? causing InterruptedException
                shConsumers(output);

                if (isTracing()) {
                    try {
                        sessionStreams.getTrace().write("RELEASE".getBytes());
                    } catch (IOException e) {
                    }
                }
                if (permits() > 1) {
                    logger.error("ShSession " + getName() + " " + getLastCommand() + " release -> permits==" + permits() + "\n" + output);
                    assert permits() == 1;
                }
            };
            sessionStreams.addPrompt("PROMPT", PROMPT, "");
            sessionStreams.addPromptCallback(this.semaphoreCallback);

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

            setTrace(trace);

            if (timeoutMillis > 0) {
                channelShell.open().verify(timeoutMillis).isOpened();
            } else {
                channelShell.open().verify().isOpened();
            }

            commandStream = new PrintStream(channelShell.getInvertedIn());

            //TODO do we wait 1s for slow connections?

            sh("unset PROMPT_COMMAND; export PS1='" + PROMPT + "'");
            String out = shSync("set +o history");
            out = shSync("export HISTCONTROL=\"ignoreboth\"");
            sh("");//forces the thread to wait for the previous sh to complete
            if (setupCommand != null && !setupCommand.trim().isEmpty()) {
                sh(setupCommand);
            } else {

            }
            //is this what is bugging out envTest?
            try {
                try {
                    shellLock.acquire();//technically should be before try{ for cases where acquire throws the exception
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
        } catch (GeneralSecurityException | IOException e) {
            logger.error("Exception while connecting to {}@{}\n{}", host.getUserName(), host.getHostName(), e.getMessage(), e);
        } finally {
            logger.trace("{} session.isOpen={} shell.isOpen={}",
                    this.getHost().getHostName(),
                    clientSession == null ? "false" : clientSession.isOpen(),
                    channelShell == null ? "false" : channelShell.isOpen()
            );
            rtrn = isOpen();
        }
        if (sessionStreams != null) { //sessionStreams can be null if an exception was thrown trying to connect
            //allow session to be fully setup before adding watcher support to lineEmittingStream
            sessionStreams.addLineConsumer(this::lineConsumers);
        } else {
            logger.error("failed to setup terminal streams for {}", host);
        }
        assert permits() == 1; //only one permit available for next sh(...)
        connected = rtrn; //set connected in case something external calls connect(...)
        return rtrn;
    }

    public void setTrace(boolean trace) throws IOException {
        if (trace) {
            String path = getHost().toString() + (hasName() ? "." + getName() : "");
            sessionStreams.setTrace(path);
        }
    }

    public boolean isOpen() {
        boolean rtrn = channelShell != null && channelShell.isOpen() && clientSession != null && clientSession.isOpen();
        return rtrn;
    }

    public boolean usesDelay() {
        return sessionStreams.getDelay() > 0;
    }

    public int getDelay() {
        return sessionStreams.getDelay();
    }

    public void setDelay(int delay) {
        sessionStreams.setDelay(delay);
    }

    public boolean isConnected() {
        return connected;
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
        if (isOpen()) {
            if (!channelShell.isOpen()) {
                logger.error("Shell is not connected for response");
            } else {
                try {
                    TimeUnit.SECONDS.sleep(1);
                    commandStream.println((command));
                    commandStream.flush();
                } catch (Exception e) {
                }
            }
        } else {
            logger.error("Shell is not connected for response");
            //TODO abort because session isn't open?
        }
    }

    //has to NOT acquire the lock
    public void reboot() {
        sh("reboot", false, null, null);
    }

    public String shSync(String command) {
        return shSync(command, null);
    }

    public String shSync(String command, Map<String, String> prompt) {
        addShObserver(SH_BLOCK_CALLBACK, blockingConsumer);
        if (blockingSemaphore.availablePermits() != 0 ){
            logger.error("ERROR: blockingSemaphorePermits = {}\n  command = {}",blockingSemaphore.availablePermits(),command);
        }
        sh(command, prompt);
        try {
            blockingSemaphore.acquire();//released in the observer
        } catch (InterruptedException e) {
            logger.error("Interrupted waiting for shSync " + command, e);
        } finally {
            removeShObserver(SH_BLOCK_CALLBACK);
        }
        assert blockingSemaphore.availablePermits() == 0;
        return blockingResponse.toString();
    }

    public void sh(String command) {
        sh(command, true, null, null);
    }

    public void sh(String command, Map<String, String> prompt) {
        sh(command, true, null, prompt);
    }

    public void sh(String command, Consumer<String> callback) {
        sh(command, true, callback, null);
    }

    public void sh(String command, Consumer<String> callback, Map<String, String> prompt) {
        sh(command, true, callback, prompt);
    }

    //TODO clear the buffers before sending the current command?
    private void sh(String command, boolean acquireLock, Consumer<String> callback, Map<String, String> prompt) {
        command = command.replaceAll("[\r\n]+$", ""); //replace trailing newlines
        logger.trace("{} sh: {}, lock: {}", host, command, acquireLock);
        lastCommand = command;
        if (isOpen()) {
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
                        if (permits() != 0) {
                            logger.error("ShSession " + getName() + "cmd=" + command + " sh.acquire --> permits==" + permits());
                            assert permits() == 0;
                        }
                    } catch (InterruptedException e) {
                        Thread.interrupted();
                    }
                    sessionStreams.clearInline();
                    if (prompt != null && !prompt.isEmpty()) {
                        sessionStreams.addInlinePrompts(prompt.keySet(), (name) -> {
                            if (prompt.containsKey(name)) {
                                String response = prompt.get(name);
                                this.response(response);
                            }
                        });
                    }
                }
                //moved stream reset to after acquiring lock
                sessionStreams.reset();
                removeShObserver(SH_CALLBACK);
                if (callback != null) {
                    addShObserver(SH_CALLBACK, callback);
                }
                if (!command.isEmpty()) {
                    //TODO race between this and FilteredStream.write before we changed FilterStream to copy the keys into a new Set
                    //Are we releasing the lock too soon in the stream chain?
                    //test with a stream that sleeps in the write?
                    sessionStreams.setCommand(command);
                }
                commandStream.println(command);
                commandStream.flush();
            }
        } else {
            //TODO abort run if sh isn't open or try reconnect
            logger.error("Shell is not connected for " + command);
        }
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

    public void close(boolean wait) {
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
                sessionStreams.close();
                channelShell.close();
                clientSession.close();
                sshClient.stop();
            } catch (IOException e) {
            }
        }
    }
}
