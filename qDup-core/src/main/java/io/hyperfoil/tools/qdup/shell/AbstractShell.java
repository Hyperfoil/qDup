package io.hyperfoil.tools.qdup.shell;

import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.SecretFilter;
import io.hyperfoil.tools.qdup.stream.SessionStreams;
import io.hyperfoil.tools.yaup.AsciiArt;
import org.apache.sshd.common.session.Session;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.StampedLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * This is the base shell for qDup's command execution. 
 * qDup creates an instance of AbstractShell based on the Host information and uses that shell to execute all `sh` or `exec` commands.
 * The shell uses an instance of SessionStreams and a shared semaphore to coordinate command execution.
 */
public abstract class AbstractShell {

    public static final String PROMPT = "<_#__qdup__#_> "; // a string unlikely to appear in the output of any command
    public static final int RECONNECT_RETRY_DELAY = 10_000;
    public static final int MAX_RECONNECT_ATTEMPTS = 10;

    protected final static Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass());

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
    String tracePath;

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
    private SessionStreams sessionStreams;
    private Map<String,Boolean> isPromptShell;

    private SecretFilter filter;

    private Host host;

    public static final AbstractShell getShell(String name, Host host,ScheduledThreadPoolExecutor executor,SecretFilter filter, String tracePath){
        return getShell(name,host,"",executor,filter,tracePath);
    }
    public static final AbstractShell getShell(String name, Host host,String setupCommand, ScheduledThreadPoolExecutor executor,SecretFilter filter, String tracePath){
        AbstractShell shell = null;
        if(host.isContainer()){
            shell = new ContainerShell(name,host,setupCommand,executor,filter,tracePath);
        }else if (host.isLocal()){
            shell = new LocalShell(name,host,setupCommand,executor,filter,tracePath);
        }else {
            shell = new SshShell(name,host,setupCommand,executor,filter,tracePath);
        }
        //should this conect the shell or just create the correct shell?
        boolean connected = shell.connect();
        return shell;
    }

    public AbstractShell(String name,Host host, String setupCommand, ScheduledThreadPoolExecutor executor, SecretFilter filter, String tracePath){
        this.name = name;
        this.host = host;
        this.setupCommand = setupCommand;
        this.executor = executor;


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
        this.isPromptShell = new HashMap<>();
        this.tracePath = tracePath;
    }

    abstract PrintStream connectShell();
    abstract void updateSessionStream(SessionStreams sessionStreams);
    final void setSessionStreams(SessionStreams sessionStreams){
        //TODO copy prompts from original sessionStreams to new sessionStreams
        this.sessionStreams.sharePrompts(sessionStreams);
        if(this.sessionStreams.hasTrace()){
            sessionStreams.setTrace(this.sessionStreams.getTraceName());
        }

        this.sessionStreams = sessionStreams;
        updateSessionStream(sessionStreams);
        //trying fix shSync after setSessionStreams is called
        if(this.sessionStreams!=null) {
            this.sessionStreams.addPromptCallback(this.semaphoreCallback);
            this.sessionStreams.addLineConsumer(this::lineConsumers);
        }



    }
    SessionStreams getSessionStreams(){return this.sessionStreams;}

    
    public ScheduledThreadPoolExecutor getScheduledExector(){
        return executor;
    }
    public SecretFilter getFilter(){return filter;}
    public final boolean connect(){
        Status previousStates = status;
        if(isOpen()){
            return true;
        }
        statusUpdater.set(this, Status.Connecting);
        logger.tracef("%s connecting",this.getName());
        boolean rtrn = false;

        try {
            if (Status.Disconnected.equals(previousStates)) {
                logger.tracef("%s connect was disconnected, stopping previous client and shell", this.getName());
                close(false);
            }


            if( sessionStreams != null){
                sessionStreams.close();
            }
            //is setting this new breaking something in LocalShell??
            sessionStreams = new SessionStreams(getName(),getScheduledExector());
            //TODO need to replace lambda with method access for changes to sessionStream to be visible
            semaphoreCallback = (name) -> {
                String output = getShOutput(true);
                if(isTracing()){

                }
                //TODO use atomic boolean to set expecting response and check for true before release?
                if(permits() == 0) {
                    shellLock.release();
                    if (isTracing()) {
                        try {
                            sessionStreams.getTrace().write(("RELEASE "+"\n").getBytes());
                        } catch (IOException e) {
                        }
                    }
                } else {
                    //this should only happen if reconnected
                    if (isTracing()) {
                        try {
                            sessionStreams.getTrace().write(("RECONNECT "+permits()+"\n").getBytes());
                        } catch (IOException e) {
                        }
                    }
                    logger.debug("skipping release, suspect reconnect "+permits());
                }
                shObservers(output,name);
                if(permits() > 1){
                    logger.error("ShSession " + getName() + " " + getLastCommand() + " release -> permits==" + permits() + "\n" + output);
                    assert permits() == 1;
                }
            };
            addPrompt(PROMPT,true);
            if(getHost().hasPrompt()){ //TODO should we only add default prompt if host does NOT have a prompt?
                addPrompt(getHost().getPrompt(), getHost().isShell());
            }
            sessionStreams.addPromptCallback(this.semaphoreCallback);

            commandStream = connectShell();
            if(commandStream == null){
                if(getHost().hasAlias()){
                    logger.errorf("%s failed to connect to %s %s",getName(),getHost().getAlias(),getHost().getSafeString());
                }else {
                    logger.errorf("%s failed to connect to %s", getName(), getHost().getSafeString());
                }
                return false;
            }
            if(getHost().isShell()){
                //bash
                shConnecting("unset PROMPT_COMMAND; export PS1='" + PROMPT + "'; set +o history; export HISTCONTROL=\"ignoreboth\"; unset PS0;");// "function fish_prompt; echo -n \""+ PROMPT+"\"; end");
                //fish
                shConnecting("function fish_prompt; echo -n \""+ PROMPT+"\"; end");
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
                logger.warnf("%s interrupted while waiting for initial PROMPT", host.getSafeString());
                Thread.interrupted();
            }
            if (sessionStreams != null) { //sessionStreams can be null if an exception was thrown trying to connect
                //allow session to be fully setup before adding watcher support to lineEmittingStream
                sessionStreams.addLineConsumer(this::lineConsumers);
            } else {
                logger.errorf("failed to setup terminal streams for %s", host);
            }
            statusUpdater.compareAndSet(this, Status.Connecting, Status.Ready);
            sessionStreams.flush(); //to remove any motd that may be in the stream
            sessionStreams.reset(); //to remove any motd that may be in the stream

        } catch (IOException e) {
            logger.debugf("Exception while connecting to %s@%s \n%s", host.getUserName(), host.getHostName(), e.getMessage(), e);
        } finally {
            logger.tracef("%s shell.isOpen=%s",getName(),isOpen());
            rtrn = isOpen();
        }
        if(rtrn){
            //enable tracing
            if(this.tracePath!=null){
                String path = Path.of(tracePath,hasName() ? getName() : getHost().toString()).toString() ;
                sessionStreams.setTrace(path);
            }
            rtrn = postConnect();
        }
        if(!rtrn){//something went wrong in post connect, this shell is no good
            //statusUpdater.set(this,Status.Closing);
            commandStream = null;//appears to break things
        }
        return rtrn;
    }
    void shConnecting(String command){
        if(commandStream == null){
            return;
        }
        Semaphore semaphore = new Semaphore(0);
        BiConsumer<String,String> consumer = (response,promptName) -> {
            String output = getShOutput(true);
            semaphore.release();
        };
        sh(command,false, consumer,null);
        try{

            semaphore.acquire();
        } catch (InterruptedException e) {
            logger.warn("interrupted waiting for response to: "+command);
            Thread.currentThread().interrupt();
        }
    }

    public boolean postConnect(){return true;}

    public String shSync(String command) {
        return shSync(command, null);
    }

    public String shSync(String command, Map<String, String> prompt) {
        return shSync(command,prompt,0).output();
    }
    public static record SyncResponse(String output,boolean timedOut){};
    public SyncResponse shSync(String command, Map<String, String> prompt,int seconds) {
        logger.debugf(getName()+".shSync "+command);
        if(commandStream==null || Status.Closing.equals(status)){
            return new SyncResponse("",false);
        }
        blockingResponse.setLength(0);//clear the blockingConsumer
        addShObserver(SH_BLOCK_CALLBACK, blockingConsumer);
        if (blockingSemaphore.availablePermits() != 0 ){
            logger.errorf("ERROR: blockingSemaphorePermits = %s\n  command = %s",blockingSemaphore.availablePermits(),command);
        }
        sh(command, prompt);
        boolean acquired = false;
        try {
            if(seconds > 0){
              acquired = blockingSemaphore.tryAcquire(seconds,TimeUnit.SECONDS);

            } else {
                blockingSemaphore.acquire();//released in the observer
                acquired = true;
            }
        } catch (InterruptedException e) {
            if(isReady()) {
                logger.error("Interrupted waiting for shSync " + command, e);
            }else{
                //this is expected
            }
            Thread.currentThread().interrupt();

        } finally {
            removeShObserver(SH_BLOCK_CALLBACK);
        }
        assert blockingSemaphore.availablePermits() == 0;
        //adding peek output to see what was written before the timeout
        if(!acquired) {
            //TODO does this need to reset the shellLock in
            int availale = shellLock.availablePermits(); //TODO reset to 1
            if(availale == 0){
                shellLock.release();
            }
            String peeked = peekOutput();
        }
        return new SyncResponse(blockingResponse.toString()+(!acquired?peekOutput():""),!acquired);
    }
    public void sh(String command) {
        sh(command, true,  (BiConsumer)null, null);
    }

    public void addLineObserver(String name, Consumer<String> consumer) {
        lineObservers.put(name, consumer);
    }
    public void removeLineObserver(String name) {
        lineObservers.remove(name);
    }
    public boolean hasLineObserver(String name) {
        return lineObservers.containsKey(name);
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
        logger.tracef("%s sh: %s, lock: %s", getHost(), command, acquireLock);
        ShAction newAction = new ShAction(command,acquireLock,callback,prompt);
        lastCommand = command;
        if (command == null || commandStream == null) {
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
            logger.error(getName()+" shell is not connected for " + getFilter().filter(command));
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
        return execSync(command,0).output();
    }
    public SyncResponse execSync(String command,int seconds){
        Semaphore semaphore = new Semaphore(0);
        StringBuilder sb = new StringBuilder();
        exec(command, (output) -> {
            sb.append(output);
            semaphore.release();
        });
        /*
        boolean acquired = false;
        try {
            if(seconds > 0){
              acquired = blockingSemaphore.tryAcquire(seconds,TimeUnit.SECONDS);

            } else {
                blockingSemaphore.acquire();//released in the observer
                acquired = true;
            }
        } catch (InterruptedException e) {
            if(isReady()) {
                logger.error("Interrupted waiting for shSync " + command, e);
            }else{
                //this is expected
            }
            Thread.currentThread().interrupt();
        } finally {
            removeShObserver(SH_BLOCK_CALLBACK);
        }
                                     */
        boolean acquired = false;
        try {

            if( seconds > 0){
                //assert semaphore.availablePermits() == 0;
                acquired = semaphore.tryAcquire(seconds, TimeUnit.SECONDS);
            }else{
                semaphore.acquire();
                acquired = true;
            }
        } catch (InterruptedException e) {
            if(isReady()){
                logger.error("Interrupted waiting for execSync "+ command, e);
            }
            Thread.currentThread().interrupt();
        }
        return  new SyncResponse(sb.toString(),!acquired);
    }
    public void exec(String command) {
        exec(command, null);
    }
    public abstract void exec(String command, Consumer<String> callback);

    public void addPrompt(String prompt,boolean isShell) {
        sessionStreams.addPrompt(prompt);
        isPromptShell.put(prompt,isShell);
    }
    public boolean isPromptShell(String prompt){
        return isPromptShell.getOrDefault(prompt,false);
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
        while(!Status.Ready.equals(status) && Status.Connecting.equals(status)){
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
    public void markAborting(){
        statusUpdater.set(this, Status.Closing);
    }
    public boolean usesDelay() {
        return sessionStreams!=null && sessionStreams.getDelay() > 0;
    }
    public int getDelay() {
        return sessionStreams==null ? 0 : sessionStreams.getDelay();
    }
    public void setDelay(int delay) {
        if(sessionStreams!=null) {
            sessionStreams.setDelay(delay);
        }
    }
    public boolean isReady() {
        return Status.Ready.equals(status);
    }

    public void flushAndResetBuffer(){
        if(sessionStreams!=null) {
            try {
                sessionStreams.flush(); //to remove any motd that may be in the stream
                sessionStreams.reset(); //to remove any motd that may be in the stream
            } catch (IOException e) {
                e.printStackTrace();
            }
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
        return tracePath != null && sessionStreams!=null && sessionStreams.hasTrace();
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

    public String bufferJson(){

        return sessionStreams==null ? "{}" : sessionStreams.jsonBuffers();
    }
    public String peekOutput() {

        return sessionStreams==null ? "" : sessionStreams.currentOutput();
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
                        logger.infof("%s closing but shell still locked %s", getHost(), getFilter().filter(lastCommand));
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
