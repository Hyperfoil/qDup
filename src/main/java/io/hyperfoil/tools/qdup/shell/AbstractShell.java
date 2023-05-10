package io.hyperfoil.tools.qdup.shell;

import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.SecretFilter;
import io.hyperfoil.tools.qdup.SshSession;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.stream.SessionStreams;
import io.hyperfoil.tools.yaup.AsciiArt;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.StampedLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class AbstractShell {

    public static final String PROMPT = "<_#%@_qdup_@%#_> "; // a string unlikely to appear in the output of any command
    public static final int RECONNECT_RETRY_DELAY = 10_000;
    public static final int MAX_RECONNECT_ATTEMPTS = 10;

    static final XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

    final static AtomicReferenceFieldUpdater<AbstractShell, Status> statusUpdater = AtomicReferenceFieldUpdater.newUpdater(AbstractShell.class, Status.class,"status");
    final static AtomicReferenceFieldUpdater<AbstractShell, ShAction> actionUpdater = AtomicReferenceFieldUpdater.newUpdater(AbstractShell.class, ShAction.class,"currentAction");

    static class ShAction {
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
    static enum Status {
        Initializing("new connection"),
        Ready("ready"),
        Disconnected("disconnected"),
        Connecting("connecting"),
        Closing("closing");

        private String name;
        Status(String name){
            this.name = name;
        }
        public String getName(){return name;}
    }

    String setupCommand;
    ScheduledThreadPoolExecutor executor;
    boolean trace;

    private StampedLock connectingLock = new StampedLock();
    private static final String SH_CALLBACK = "qdup-sh-callback";
    private static final String SH_BLOCK_CALLBACK = "qdup-sh-block-callback";

    private String name = "";
    private String lastCommand = "";

    volatile Status status = Status.Initializing;
    volatile ShAction currentAction = null;

    Consumer<String> semaphoreCallback;
    private Semaphore blockingSemaphore;
    private Consumer<String> blockingConsumer;
    private StringBuffer blockingResponse;
    private Map<String, Consumer<String>> lineObservers;
    private Map<String, BiConsumer<String,String>> shObservers;
    PrintStream commandStream;
    Semaphore shellLock;
    SessionStreams sessionStreams;

    private SecretFilter filter;

    private Host host;

    public static final AbstractShell getShell(Host host,ScheduledThreadPoolExecutor executor,SecretFilter filter, boolean trace){
        return getShell(host,"",executor,filter,trace);
    }
    public static final AbstractShell getShell(Host host,String setupCommand, ScheduledThreadPoolExecutor executor,SecretFilter filter, boolean trace){
        AbstractShell shell = null;
        if(host.isContainer()){
            shell = new ContainerShell(host,setupCommand,executor,filter,trace);
        }else if (host.isLocal()){
            shell = new LocalShell(host,setupCommand,executor,filter,trace);
        }else {
            shell = new SshShell(host,setupCommand,executor,filter,trace);
        }
        //should this conect the shell or just create the correct shell?
        boolean connected = shell.connect();
        return shell;
    }

    public AbstractShell(Host host, String setupCommand, ScheduledThreadPoolExecutor executor, SecretFilter filter, boolean trace){
        this.host = host;
        this.setupCommand = setupCommand;
        this.executor = executor;
        this.trace=false;

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
        sessionStreams = new SessionStreams(getName(),executor);
        this.filter = filter;
    }

    abstract PrintStream connectShell();

    public SecretFilter getFilter(){return filter;}
    public boolean connect(){
        Status previousStates = status;
        if(isOpen()){
            return true;
        }
        statusUpdater.set(this, Status.Connecting);
        logger.trace("{} connecting",this.getName());
        boolean rtrn = false;

        try {
            if (Status.Disconnected.equals(previousStates)) {
                logger.trace("{} connect was disconnected, stopping previous client and shell", this.getName());
                close(false);
            }


            if( sessionStreams != null){
                sessionStreams.close();
            }
            //is setting this new breaking something in LocalShell??
            sessionStreams = new SessionStreams(getName(),executor);
            //TODO need to replace lambda with method access for changes to sessionStrema to be visible
            semaphoreCallback = (name) -> {
                String output = getShOutput(true);
                //TODO use atomic boolean to set expecting response and check for true before release?
                if(permits() == 0) {
                    shellLock.release();
                    if (isTracing()) {
                        try {
                            sessionStreams.getTrace().write("RELEASE".getBytes());
                        } catch (IOException e) {
                        }
                    } else {
                        //this should only happen if reconnected
                        logger.debug("skipping release, suspect reconnect");
                    }
                }
                shObservers(output,name);
                if(permits() > 1){
                    logger.error("ShSession " + getName() + " " + getLastCommand() + " release -> permits==" + permits() + "\n" + output);
                    assert permits() == 1;
                }

            };
            sessionStreams.addPrompt(PROMPT,PROMPT,"");
            if(getHost().hasPrompt()){
                sessionStreams.addPrompt(getHost().getPrompt(),getHost().getPrompt(),"");
            }
            sessionStreams.addPromptCallback(this.semaphoreCallback);
            commandStream = connectShell();
            if(commandStream == null){
                logger.error("{} failed to connect to {}",getName(),getHost().getSafeString());
                return false;
            }
            if(getHost().isSh()){
                shConnecting("unset PROMPT_COMMAND; export PS1='" + PROMPT + "'; set +o history; export HISTCONTROL=\"ignoreboth\"");
            }
            if(setupCommand !=null && !setupCommand.trim().isEmpty()){
                shConnecting(setupCommand);
            }
            //is this what is bugging out envTest?
            try {
                shellLock.acquire();//technically should be before try{ for cases where acquire throws the exception
                try {
                    if (permits() != 0) {
                        logger.error("Shell " + getName() + " connect.acquire --> permits==" + permits());
                    }
                } finally {
                    shellLock.release();
                    if (permits() != 1) {
                        logger.error("Shell " + getName() + " connect.release --> permits==" + permits());
                        assert permits() == 1;
                    }
                }
            } catch (InterruptedException e) {
                logger.warn("{} interrupted while waiting for initial PROMPT", host.getSafeString());
                Thread.interrupted();
            }
            if (sessionStreams != null) { //sessionStreams can be null if an exception was thrown trying to connect
                //allow session to be fully setup before adding watcher support to lineEmittingStream
                sessionStreams.addLineConsumer(this::lineConsumers);
            } else {
                logger.error("failed to setup terminal streams for {}", host);
            }
            statusUpdater.compareAndSet(this, Status.Connecting, Status.Ready);
            sessionStreams.flush(); //to remove any motd that may be in the stream
            sessionStreams.reset(); //to remove any motd that may be in the stream

        } catch (IOException e) {
            logger.debug("Exception while connecting to {}@{} \n{}", host.getUserName(), host.getHostName(), e.getMessage(), e);
        } finally {
            logger.trace("{} shell.isOpen={}",getName(),isOpen());
            rtrn = isOpen();
        }
        return rtrn;
    }
    void shConnecting(String command){
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
    public void sh(String command) {
        sh(command, true,  (BiConsumer)null, null);
    }

    public void addLineObserver(String name, Consumer<String> consumer) {
        lineObservers.put(name, consumer);
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
    void sh(String command, boolean acquireLock, BiConsumer<String,String> callback, Map<String, String> prompt) {
        command = command.replaceAll("[\r\n]+$", ""); //replace trailing newlines
        logger.trace("{} sh: {}, lock: {}", getHost(), command, acquireLock);
        ShAction newAction = new ShAction(command,acquireLock,callback,prompt);
        lastCommand = command;
        if (command == null) {
            return;
        }
        if (acquireLock) {
            try {
                shellLock.acquire();
                if (permits() != 0) {
                    logger.error("ShSession " + getName() + "cmd=" + getFilter().filter(command) + " sh.acquire --> permits==" + permits());
                    assert permits() == 0;
                }
            } catch (InterruptedException e) {
                logger.error("interrupted acquiring shellLock for " + getName() + "@" + getHost());
                Thread.interrupted();
            }
            //moved out of acquireLock, and it broke shConnecting
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
            //end of normal acquire lock
        }

        if(sessionStreams!=null){
            sessionStreams.reset();
        }
        removeShObserver(SH_CALLBACK);
        if (callback != null){
            addShObserver(SH_CALLBACK,this::callback);
        }
        boolean sendCommand = ensureConnected();
        if ( sendCommand ){
            if (!command.isEmpty()){
                //is there a reace between sessionStreams updating command and the response filtering out the command for the previous command
                //could test with a slow writing stream on sessionStreams that occurs after promptStream
                sessionStreams.setCommand(command);
            }
            actionUpdater.set(this,newAction);
            commandStream.println(command);
            commandStream.flush();
        } else {
            logger.error("Shell is not connected for " + getFilter().filter(command));
        }

    }

    /**
     * This is the callback that is invoked when a shell prompt is found at the end of the input
     * @param input
     * @param name
     */
    public void callback(String input,String name){
        ShAction target = currentAction;
        if(target!=null) {
            if (actionUpdater.compareAndSet(this, currentAction, null) ) {
                if(target.hasCallback()){
                    target.callback.accept(input,name);
                }
            }else{
                logger.warn("failed to perform callback for "+getFilter().filter(target.getCommand()));
            }
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
            logger.error("Shell is not connected for response "+getFilter().filter(command));
        }
    }
    public String execSync(String command){
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
    public abstract void exec(String command, Consumer<String> callback);

    public void addPrompt(String prommpt) {
        sessionStreams.addPrompt(prommpt);
    }
    public void reboot(){
        throw new UnsupportedOperationException("shell does not support reboot");
    }

    public void ctrlC(){//SIGINT
        ctrl('C');
    }
    public void ctrl(char key) {
        //TODO
        if(ensureConnected()){
            try {
                commandStream.write(ctrlInt(key));
                commandStream.flush();
            }catch(Exception e){
                logger.error("error writing ctrl-"+key+" to "+getHost());
            }

        }else{
            logger.error("shell is not connected for ctrl-"+key);
        }
    }
    protected int ctrlInt(char key) {
        return (Character.toUpperCase(key) & 0x1f);
    }
    public boolean ensureConnected() {
        if(isOpen()){
            return true;
        }
        boolean rtrn = false;
        if(Status.Disconnected.equals(status) || Status.Initializing.equals(status)){
            long lock = connectingLock.writeLock();
            synchronized (this) {
                try {
                    if (Status.Disconnected.equals(status) || Status.Initializing.equals(status)) { //double check status before proceeding with
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
    public boolean waitForReady(){
        while(!Status.Ready.equals(status)){
            logger.debug("getting connecting semaphore with status: "+status);
            long lock = connectingLock.readLock();
            connectingLock.unlockRead(lock);
        }
        return Status.Ready.equals(status);
    }
    private boolean reconnect(){

        boolean rtrn = false;
        if(!isOpen() && (Status.Disconnected.equals(status) || Status.Initializing.equals(status)) ){
            int attempts = 0;
            do {
                try {
                    rtrn = connect();
                }finally{
                    attempts++;
                    if(!isOpen() || !isReady()){
                        try {
                            Thread.sleep(RECONNECT_RETRY_DELAY);
                        }catch(InterruptedException e){

                        }
                    }
                }
            }while ( (!isReady() || !isOpen()) && attempts < MAX_RECONNECT_ATTEMPTS);
        }
        return isOpen() && isReady();
    }

    public void setTrace(boolean trace) throws IOException {
        this.trace = trace;
        if (trace) {
            String path =  hasName() ? getName() : getHost().toString();
            sessionStreams.setTrace(path);
        }

    }
    public void markAborting(){
        statusUpdater.set(this, Status.Closing);
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
    public boolean isReady() {
        return Status.Ready.equals(status);
    }

    public void flushAndResetBuffer(){
        try {
            sessionStreams.flush(); //to remove any motd that may be in the stream
            sessionStreams.reset(); //to remove any motd that may be in the stream
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    public String getLastCommand() {
        return lastCommand;
    }
    public void setLastCommand(String lastCommand) {
        this.lastCommand = lastCommand;
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

    private void shObservers(String output,String promptName) {
        shObservers.forEach((name,consumer)->{
            consumer.accept(output,promptName);
        });
    }
    public int permits() {
        return shellLock.availablePermits();
    }
    public boolean isTracing() {
        return sessionStreams!=null && sessionStreams.hasTrace();
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


    public abstract boolean isOpen();
    public String getName() {
        return name;
    }
    public boolean hasName() {
        return name != null && !name.isEmpty();
    }

    public boolean isActive(){return currentAction!=null;}
    public boolean isIdle(){return currentAction==null;}

    public void setName(String name) {
        this.name = name;
        if(sessionStreams!=null){
            sessionStreams.setName(name);
        }
    }
    public abstract AbstractShell copy();

    public AbstractShell openCopy(){
        AbstractShell rtrn = copy();
        rtrn.connect();
        return rtrn;
    }

    private void lineConsumers(String line) {
        if (!lineObservers.isEmpty()) {
            for (Consumer<String> consumer : lineObservers.values()) {
                consumer.accept(line);
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

    public abstract void close();

    public Host getHost(){return host;}

    public boolean close(boolean wait){
        if(isOpen()){

            if(wait){
                try {
                    if (shellLock.availablePermits() <= 0) {
                        logger.info("{} closing but shell still locked {}", getHost(), getFilter().filter(lastCommand));
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
            close();
            try {
                sessionStreams.close();
            } catch (IOException e) {
                logger.error("{} error closing shell streams",getName(),e);
            }
        }
        return true;
    }
}
