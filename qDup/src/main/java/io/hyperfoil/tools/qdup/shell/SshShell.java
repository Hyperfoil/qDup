package io.hyperfoil.tools.qdup.shell;

import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.SecretFilter;
import io.hyperfoil.tools.qdup.stream.MultiStream;
import io.hyperfoil.tools.qdup.stream.SessionStreams;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * This shell uses an ssh connection. SshShell can use either identity files or username and password authentication.
 * The authentication mechanism is based on the information in the Host used to connect the shell.
 */
public class SshShell extends AbstractShell{

    private SshClient sshClient;
    private ClientSession clientSession;
    private ChannelShell channelShell;
    private long timeout = 10_000;

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
                    statusUpdater.set(SshShell.this,Status.Disconnected); //only change to disconnected if not connecting
                }
                //release any permits
                if(isActive()){
                    if(Status.Ready.equals(previousStatus)){
                        logger.warn("reconnect invoking semaphoreCallback due to active command during disconnect\n  command:"+getFilter().filter(currentAction.getCommand()));
                        if(getSessionStreams()!=null) {
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
                            actionUpdater.set(SshShell.this,null);
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


    public SshShell(String name,Host host, String setupCommand, ScheduledThreadPoolExecutor executor, SecretFilter filter, boolean trace) {
        super(name,host, setupCommand, executor, filter, trace);
    }

    @Override
    void updateSessionStream(SessionStreams sessionStreams){
        channelShell.setOut(getSessionStreams());//efs or ss
        channelShell.setErr(getSessionStreams());//PROMPT goes to error stream so have to listen there too
    }
    @Override
    PrintStream connectShell() {
        if(isOpen()){
            return commandStream;
        }
        statusUpdater.set(this,Status.Connecting);
        logger.tracef("%s connecting",getName());
        boolean rtrn = false;
        try {
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
            sshClient = SshClient.setUpDefaultClient();
            sshClient.addSessionListener(new SessionListener() {
                @Override
                public void sessionEstablished(Session session) {
                    logger.debugf("%s client established",getName());
                }

                @Override
                public void sessionCreated(Session session) {
                    logger.debugf("%s client created",getName());
                }

                @Override
                public void sessionDisconnect(Session session, int reason, String msg, String language, boolean initiator) {
                    logger.debugf("%s client disconnected",getName());
                }

                @Override
                public void sessionClosed(Session session) {
                    logger.debugf("%s client disconnected",getName());
                }
            });
            CoreModuleProperties.IDLE_TIMEOUT.set(sshClient, Duration.ofSeconds(7*24*3600));
            CoreModuleProperties.NIO2_READ_TIMEOUT.set(sshClient, Duration.ofSeconds(7*24*3600));
            CoreModuleProperties.NIO_WORKERS.set(sshClient, 1);
            // StrictHostKeyChecking=no
            //        sshConfig = new Properties();
            //        sshConfig.put("StrictHostKeyChecking", "no");
            sshClient.setServerKeyVerifier((clientSession1, remoteAddress, serverKey) -> {
                logger.tracef("%s accept server key for %s",getName(),remoteAddress);
                return true;
            });
            if(getHost().hasPassword()){
                logger.tracef("%s setting client password provider %s",getName());
                sshClient.setPasswordIdentityProvider((sc) -> Arrays.asList(getHost().getPassword()));
            }
            if(getHost().hasPassphrase()){
                logger.tracef("%s setting client passphrase",getName());
                sshClient.setFilePasswordProvider((SessionContext sessionContext, NamedResource namedResource, int i)->{
                    return getHost().getPassphrase();
                });
            }
            if(getHost().hasIdentity()){
                logger.tracef("%s setting client identity %s",getName(),getHost().getIdentity());
                try {
                    URLResource urlResource = new URLResource(Paths.get(getHost().getIdentity()).toUri().toURL());
                    try (InputStream inputStream = urlResource.openInputStream()) {
                        Iterable<KeyPair> keyPairs = SecurityUtils.loadKeyPairIdentities(
                                clientSession,
                                urlResource,
                                inputStream,
                                (session, resourceKey, retryIndex) -> getHost().getPassphrase()
                        );
                        KeyPair keyPair = GenericUtils.head(keyPairs);
                        if (keyPair == null) {
                            if (!getHost().hasPassphrase()) {
                                logger.errorf("%s cannot set client identity %s without a passphrase", getName(), getHost().getIdentity());
                            } else {
                                logger.errorf("%s cannot set client identity %s using the provided passphrase", getName(), getHost().getIdentity());
                            }
                            return null; // we failed to connect
                        }
                        sshClient.setKeyIdentityProvider((sessionContext -> {
                            return keyPairs;
                        }));
                    } catch (IOException | GeneralSecurityException e) {
                        logger.errorf("%s client failed to connect with custom identity before timeout", getHost().getHostName(), e);
                        return null;
                    }
                } catch (MalformedURLException e) {
                    //due to bad identity?
                    logger.errorf("%s client failed to access custom identity", getHost().getHostName());
                    return null;
                }
            }
            sshClient.start();
            ConnectFuture future = sshClient.connect(getHost().getUserName(), getHost().getHostName(), getHost().getPort());
            future.await(10, TimeUnit.SECONDS);
            if(!future.isConnected()){
                logger.errorf("%s client failed to connect before 10s timeout",getHost().getHostName());
                return null;
            }
            future = future.verify(timeout);
            future.await(10, TimeUnit.SECONDS);
            if(!future.isConnected()){
                logger.errorf("%s client failed to verify connection before timeout",getName());
                return null;
            }
            clientSession = future.getSession();
            clientSession.addSessionListener(new SessionListener() {
                @Override
                public void sessionEstablished(Session session) {
                    logger.tracef("%s session established",getName());
                }

                @Override
                public void sessionCreated(Session session) {
                    logger.tracef("%s session created",getName());
                }

                @Override
                public void sessionPeerIdentificationReceived(Session session, String version, List<String> extraLines) {
                    logger.tracef("%s session identification received",getName());
                }

                @Override
                public void sessionNegotiationStart(Session session, Map<KexProposalOption, String> clientProposal, Map<KexProposalOption, String> serverProposal) {
                    logger.tracef("%s session negotiation start",getName());
                }

                @Override
                public void sessionNegotiationEnd(Session session, Map<KexProposalOption, String> clientProposal, Map<KexProposalOption, String> serverProposal, Map<KexProposalOption, String> negotiatedOptions, Throwable reason) {
                    logger.tracef("%s session negotiation end",getName());
                }

                @Override
                public void sessionEvent(Session session, Event event) {

                }

                @Override
                public void sessionException(Session session, Throwable t) {
                    logger.tracef("%s session exception: %s",getName(),t.getMessage());
                }

                @Override
                public void sessionDisconnect(Session session, int reason, String msg, String language, boolean initiator) {
                    logger.tracef("%s session disconnect",getName());
                }

                @Override
                public void sessionClosed(Session session) {
                    logger.tracef("%s session closed",getName());
                }
            });

            if(!getHost().hasPassphrase()){
                logger.tracef("%s using %s identity without passphrase",getName(),getHost().getIdentity());
            }else{
                logger.tracef("%s using %s identity with a passphrase",getName(),getHost().getIdentity());
            }
            URLResource urlResource = new URLResource(Paths.get(getHost().getIdentity()).toUri().toURL());
            try (InputStream inputStream = urlResource.openInputStream()) {
                Iterable<KeyPair> keyPairs = SecurityUtils.loadKeyPairIdentities(
                        clientSession,
                        urlResource,
                        inputStream,
                        (session, resourceKey, retryIndex) -> getHost().getPassphrase()
                );
                KeyPair keyPair = GenericUtils.head(keyPairs);
                if(keyPair == null){
                    if(!getHost().hasPassphrase()){
                        logger.errorf("cannot connect %s using %s without a passphrase",getName(),getHost().getIdentity());
                    }else{
                        logger.errorf("cannot connect %s using %s using the provided passphrase",getName(),getHost().getIdentity());
                    }
                    return null; // we failed to connect
                }
                clientSession.addPublicKeyIdentity(keyPair);
            } catch (GeneralSecurityException e) {
                logger.errorf("%s failed to load identity %s\n%s",getName(),getHost().getIdentity(),e.getMessage());
                return null;
            }
            if (getHost().hasPassword()) {
                logger.tracef("%s adding password-identity",getName());
                clientSession.addPasswordIdentity(getHost().getPassword());
            }

            logger.tracef("%s authenticating client session",getName());
            boolean sessionResponse = clientSession.auth().verify().await(timeout * 1_000);
            logger.tracef("%s waiting for authentication",getName());
            clientSession.waitFor(EnumSet.of(ClientSession.ClientSessionEvent.AUTHED), 0L);

            channelShell = clientSession.createShellChannel();
            channelShell.getPtyModes().put(PtyMode.ECHO, 1);//need echo for \n from real SH but adds gargage chars for test :(
            channelShell.setPtyType("vt100"); // channelShell.setPtyType("xterm");
            channelShell.setPtyColumns(10 * 1024);//hack to get around " \r" when line is longer than shell width
            channelShell.setPtyWidth(10 * 1024);//TODO add " \r" to the suffix stream?
            channelShell.setPtyHeight(80);
            channelShell.setPtyLines(80);
            channelShell.setUsePty(true);

            channelShell.setOut(getSessionStreams());//efs or ss
            channelShell.setErr(getSessionStreams());//PROMPT goes to error stream so have to listen there too
            channelShell.addChannelListener(new SessionWatcher());

            long timeoutMillis = 5;

            if (timeoutMillis > 0) {
                logger.tracef("%s opening and verifying channel shell with %s timeout",getName(),timeoutMillis);
                boolean response = channelShell.open().verify().await(timeoutMillis);
                //channelShell.open().verify(timeoutMillis).isOpened();

            } else {
                logger.tracef("%s opening and verifying channel shell",getName());
                channelShell.open().verify().isOpened();
            }

            return new PrintStream(channelShell.getInvertedIn());

        } catch (IOException e) {//sshClient.connect or future.await
            //throw new RuntimeException(e);
            logger.errorf("%s failed to connect client to %s %s",getName(),getHost().getSafeString(),e.getMessage());
        }
        return null;
    }

    @Override
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
                logger.errorf("%s failed to connect client to {} {}",getName(),getHost().getSafeString(),e.getMessage());

            }
        }
    }

    @Override
    public boolean isOpen(){
        boolean rtrn = channelShell != null && channelShell.isOpen() && clientSession != null && clientSession.isOpen();
        return rtrn;
    }

    @Override
    public AbstractShell copy() {
        return new SshShell(
                getName(),
                getHost(),
                setupCommand,
                executor,
                getFilter(),
                trace
        );
    }

    @Override
    public void close() {
        statusUpdater.set(this,Status.Closing);
        try {
            if(channelShell!=null && channelShell.isOpen()) {
                channelShell.close();
            }
            if(clientSession!=null && clientSession.isOpen()) {
                clientSession.close();
            }
            if(sshClient!=null && sshClient.isStarted()) {
                sshClient.stop();
            }
        } catch (IOException e) {
            logger.errorf("%s error while closing %s",getName(),e.getMessage(),e);
        }

    }
}
