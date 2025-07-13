package io.hyperfoil.tools.qdup.shell;

import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.SecretFilter;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.config.yaml.HostDefinition;
import io.hyperfoil.tools.qdup.stream.MultiStream;
import io.hyperfoil.tools.qdup.stream.SessionStreams;
import io.hyperfoil.tools.yaup.AsciiArt;
import io.hyperfoil.tools.yaup.PopulatePatternException;
import io.hyperfoil.tools.yaup.StringUtil;
import io.hyperfoil.tools.yaup.json.Json;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ContainerShell extends AbstractShell{

/*
    podman run --detach <image>
    podman exec --interactive --tty <name> /bin/bash
    podman-attach
 */

    private AbstractShell shell;
    private String containerId = null;

    public ContainerShell(Host host, String setupCommand, ScheduledThreadPoolExecutor executor, SecretFilter filter, boolean trace) {
        super(host, setupCommand, executor, filter, trace);
    }

    public static String populateList(List<String> toPopulate, Json variables){
        Json toUse = variables.clone();
        toUse.set(HostDefinition.QDUP_PROMPT_VARIABLE,AbstractShell.PROMPT);
        List<String> populated = Cmd.populateList(toUse,toPopulate);
        String populatedCommand = populated.stream().filter(v->v!=null && !v.isBlank()).collect(Collectors.joining(" "));
        return populatedCommand;
    }

    @Override
    void updateSessionStream(SessionStreams sessionStreams){

    }

    @Override
    public void setName(String name){
        super.setName(name);
        if(shell!=null){
            shell.setName(getName()+"-sub-shell");
        }
    }

    public boolean hasErrorMessage(String input){
        return Stream.of("Error","error","-bash","command not found","select an image","no such object:")
            .anyMatch(e->input.contains(e) || input.matches(e));
    }

    private void logPopulateError(String pattern,String message){
        try {
            List<String> missing = StringUtil.getPatternNames(pattern, new HashMap<>());
            logger.error(getHost().getSafeString() + " failed to populate " + message + " missing: "+missing);
        }catch(PopulatePatternException e){
            logger.error(getHost().getSafeString() + " failed to populate " + message + " left with "+pattern);
        }
    }

    //TODO should also accept the state or some state representation
    @Override
    PrintStream connectShell() {
        int asyncTimeoutSeconds = 10;
        //starts the shell
        PrintStream rtrn = null;
        //need an alt host that has the default connection method for local or remote shell, then we use provided host to connect to the container
        //how does this work if we want a custom shell (zsh?) on the alt host?
        //TODO how do we specify custom shell container sub-host?
        Host subHost = getHost().isLocal() ? new Host() : new Host(getHost().getUserName(),getHost().getHostName(),getHost().getPassword(),getHost().getPort());
        if(getHost().hasAlias()){
            subHost.setAlias(getHost().getAlias());
        }
        if(getHost().hasIdentity()){
            subHost.setIdentity(getHost().getIdentity());
        }
        if(getHost().hasPassphrase()) {
            subHost.setPassphrase(getHost().getPassphrase());
        }
        if(getHost().isLocal()){
            shell = new LocalShell(subHost,setupCommand,executor,getFilter(),trace);
        } else {
            shell = new SshShell(subHost,setupCommand,executor,getFilter(),trace);
        }
        shell.setName(getName()+"-sub-shell");
        boolean connected = shell.connect();
        if(!connected){
            logger.errorf("failed to connect %s shell for container to %s",getHost().isLocal() ? "local" : "remote", getHost().getSafeString());
            return null;
        }
        boolean connectSetContainerId=false;

        String startError = null;

        String subShellUname = shell.execSync("uname -n",asyncTimeoutSeconds).output();//ignoring timeouts for now

        if(getHost().hasContainerId()) { //an already running container
            containerId = getHost().getContainerId();
        } else if (Host.isContainerName(getHost().getDefinedContainer())){ //try and get containerId from name
            String containerName = getHost().getDefinedContainer();
            if(getHost().hasCheckContainerName()){
                Json json = new Json();
                json.set("host",getHost().toJson());
                json.set("image",getHost().getDefinedContainer());
                json.set("container",containerName);

                String populatedCheckName = populateList(getHost().getCheckContainerName(),json);
                if(populatedCheckName.contains(StringUtil.PATTERN_PREFIX)){
                    logPopulateError(populatedCheckName,HostDefinition.CHECK_CONTAINER_NAME);
                }else{
                    SyncResponse checkNameResponse = shell.execSync(populatedCheckName,10);
                    if(!checkNameResponse.timedOut() && !hasErrorMessage(checkNameResponse.output())){//should not timeout for exec
                        if(Host.isContainerId(checkNameResponse.output())){
                            containerId = checkNameResponse.output();
                        }
                    }
                }
            }else{
                //Do we assume it is a valid image? I'm not sure that is going to work...
            }
        }else if (Host.isContainerId(getHost().getDefinedContainer())){
            containerId = getHost().getDefinedContainer(); //in user we trust?
        }
        if(containerId == null || containerId.isBlank() || !Host.isContainerId(containerId)){
            if(getHost().hasStartContainer()){
                Json json = new Json();
                json.set("host",getHost().toJson());
                json.set("image",getHost().getDefinedContainer());
                json.set("container",getHost().getDefinedContainer());
                json.set("containerId",getHost().getDefinedContainer());
                String populatedStartContainer = populateList(getHost().getStartContainer(),json);
                if(populatedStartContainer.contains(StringUtil.PATTERN_PREFIX)){
                    logPopulateError(populatedStartContainer,HostDefinition.START_CONTAINER);
                    //TODO how to fail when container start fails
                }else{
                    SyncResponse response = shell.shSync(populatedStartContainer,null,asyncTimeoutSeconds);
                    containerId = response.output();
                    if(response.timedOut() && containerId.isBlank()){
                        containerId = shell.peekOutput();
                    }
                    if(containerId.contains("select an image")) {
                        //there was an error reported from container runtime
                        logger.errorf("error starting %s container %s is ambiguous", getHost().isLocal() ? "local" : "remote", containerId);
                        return null;
                    } else if(hasErrorMessage(containerId)){
                        logger.errorf("error starting %s container %s : %s", getHost().isLocal() ? "local" : "remote", getHost().getSafeString(), containerId);
                        //cannot start the image if we cannot select it
                        return null;
                    } else if(containerId.contains("\n") || containerId.isBlank()){
                        //assume the container started connected
                        //cannot shSync because connection is not ready and user may not have passed in PROMPT
                        rtrn = shell.commandStream;
                        shell.setSessionStreams(getSessionStreams());
                    } else {
                        //we started a new container (docker and podman)
                        if(containerId.matches("[0-9a-f]{64}")){
                            getHost().setContainerId(containerId);
                            getHost().setNeedStopContainer(true);
                            connectSetContainerId=true;
                        }else{
                            String ec = shell.shSync("echo $?");
                            if(!"0".equals(ec)){
                                //we won't try to connect once start errors
                                startError = containerId;
                            }
                            containerId = getHost().getDefinedContainer();
                        }
                        //check for errors
                    }
                }
            }else{
                //handled by containerId==null check later
            }
        }
        if(rtrn == null && getHost().hasConnectShell() && startError == null){
            Json json = new Json();
            json.set("host",getHost().toJson());
            json.set("image",getHost().getDefinedContainer());
            json.set("container",containerId);//idk which we should use
            json.set("containerId",containerId);//TODO decide if container or containerId
            String populatedConnectShell = populateList(getHost().getConnectShell(),json);
            if( populatedConnectShell.contains(StringUtil.PATTERN_PREFIX)){
                logPopulateError(populatedConnectShell,HostDefinition.CONNECT_SHELL);
                //TODO how do we fail this case
            }else{
                Semaphore connectingShellLock = new Semaphore(0);
                AtomicBoolean hasError = new AtomicBoolean(false);
                List<String> errorMessages = Arrays.asList("Error","-bash");
                getSessionStreams().addLineConsumer((line)->{
                    if(errorMessages.stream().anyMatch(e->line.contains(e) || line.matches(e))){
                        hasError.set(true);
                    }
                    if(!line.isBlank()) {//how are we getting a blank line? is there an error in FilteredStream newline filtering after stripping command?
                        //blank line was probably from bash bracket paste mode
                        connectingShellLock.release();
                    }
                });
                //TODO replace with method that also adds the command from other sessionStreams
                //why was the sessionStreams changed before sending connectShell?
                //this only worked for LocalShell
                shell.setSessionStreams(getSessionStreams());//moved before sh so filterStream would see the command
                //calling shSync on shell::LocalShell doesn't work after changing sessionStreams

                //testing using shSync
                //shell.sh(populatedConnectShell,false,(prompt,output)->{ },null);//use sh without the lock
                SyncResponse response = shell.shSync(populatedConnectShell,null,asyncTimeoutSeconds);
                String fsz = response.output();
                if(fsz.isBlank()){
                    //we probably timed out due to container prompt
                }else{
                    //we probably got an error
                }
                try {
                    //TODO custom timeout
                    boolean acquired = connectingShellLock.tryAcquire(asyncTimeoutSeconds, TimeUnit.MILLISECONDS);
                    if(acquired){
                        //if we acquired the lock there was probably an error message
                    }else{
                        //this means we didn't get a message with a newline, we likely got the prompt.
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if(hasError.get()){

                    if(connectSetContainerId){
                        // TODO there is a containerId that we started before but stopped, we should use it
                        if(getHost().hasContainerId() && getHost().hasStartConnectedContainer()){
                            Json ccJson = new Json();
                            ccJson.set("host",getHost().toJson());
                            ccJson.set("containerId",getHost().getContainerId());
                            String populatedStartConnected = populateList(getHost().getStartConnectedContainer(),ccJson);
                            if(populatedStartConnected.contains(StringUtil.PATTERN_PREFIX)){
                                logPopulateError(populatedStartConnected,HostDefinition.START_CONNECTED_CONTAINER);
                                //TODO how do we fail this case?
                            }else{
                                hasError.set(false);
                                SessionStreams currentShellSessionStreams = shell.getSessionStreams();
                                if(currentShellSessionStreams != getSessionStreams()) {
                                    shell.setSessionStreams(getSessionStreams());
                                }
                                SyncResponse startConnectedResponse = shell.shSync(populatedStartConnected,null,asyncTimeoutSeconds);
                                String output = startConnectedResponse.output();
                                if(startConnectedResponse.timedOut() && output.isBlank()){
                                    output = shell.peekOutput();
                                }
                                if(output.contains("Error") || output.contains("error") || output.contains("-bash")){
                                    //TODO alert at error tring to start connected?
                                    hasError.set(true);
                                }else{
                                    if(output.isBlank()){//could be connected or timed out
                                        if(getHost().hasCheckContainerStatus()) {
                                            String populatedCheckStatus = populateList(getHost().getCheckContainerStatus(), ccJson);
                                            if (populatedCheckStatus.contains(StringUtil.PATTERN_PREFIX)) {
                                                logPopulateError(populatedCheckStatus,HostDefinition.CHECK_CONTAINER_STATUS);
                                                //TODO alert error
                                            } else {
                                                SyncResponse checkStatusResponse = shell.execSync(populatedCheckStatus,10);
                                                if(checkStatusResponse.output().contains("running")){
                                                    rtrn = shell.connectShell();
                                                }else{
                                                    hasError.set(true);
                                                    rtrn = null;
                                                }
                                            }
                                        }else{
                                            //TODO what to do when we cannot confirm the container started and it's blank? check uname?
                                            SyncResponse unameResponse = shell.shSync("uname -n",null,10);
                                            if(!unameResponse.timedOut() && !unameResponse.output().contains(subShellUname)){
                                                rtrn = shell.connectShell();
                                            }else{
                                                hasError.set(true);
                                                rtrn = null;
                                            }
                                        }
                                    } else { //assume the output indicated it was ok
                                        rtrn = shell.commandStream;
                                    }
                                }

                            }
                        }

                        if(rtrn == null && hasError.get() && getHost().hasCreateConnectedContainer()){
                            Json ccJson = new Json();
                            ccJson.set("host",getHost().toJson());
                            ccJson.set("image",getHost().getDefinedContainer());
                            String populatedCreateConnected = populateList(getHost().getCreateConnectedContainer(),ccJson);
                            if(populatedCreateConnected.contains(StringUtil.PATTERN_PREFIX)){
                                logPopulateError(populatedCreateConnected,HostDefinition.CREATE_CONNECTED_CONTAINER);
                                //TODO how do we fail in this case?
                            }else{
                                hasError.set(false);
                                SessionStreams currentShellSessionStreams = shell.getSessionStreams();
                                if(currentShellSessionStreams != getSessionStreams()){
                                    shell.setSessionStreams(getSessionStreams());
                                }
                                SyncResponse createConnectedResponse = shell.shSync(populatedCreateConnected,null,asyncTimeoutSeconds);
                                String output = createConnectedResponse.output();

                                if(output!=null && output.isEmpty()){//output is empty when timeout triggers
                                    output = getSessionStreams().currentOutput();
                                    rtrn = shell.commandStream;
                                    //clearing containerId to be set by postConnect
                                    getHost().setContainerId(Host.NO_CONTAINER);
                                }
                            }
                        }
                        //this what from when we would start a new container, now we reconnect
                        //getHost().setContainerId(Host.NO_CONTAINER);
                    }else{
                        if(!getHost().hasStartConnectedContainer()) {
                            logger.errorf("failed to connect to container shell for %s\n%s", getHost(), shell.getSessionStreams().currentOutput());
                        }
                    }
                }else {
                    rtrn = shell.commandStream;
                }
            }
        }
        if(rtrn == null && getHost().hasStartConnectedContainer()){
            if(shell.getSessionStreams() != getSessionStreams()){
                //shell.sessionStreams = sessionStreams;
                shell.setSessionStreams(getSessionStreams());
            }

            Json json = new Json();
            json.set("host",getHost().toJson());
            json.set("image",getHost().getDefinedContainer());
            json.set("container",containerId);//idk which we should use
            json.set("containerId",containerId);//TODO decide if container or containerId
            String populatedCommand = populateList(getHost().getStartConnectedContainer(),json);
            SyncResponse startConnectedResponse = shell.shSync(populatedCommand,null,asyncTimeoutSeconds);
            String output = startConnectedResponse.output();
            if(output!=null && output.isEmpty()){//output is empty when timeout triggers
                output = getSessionStreams().currentOutput();
                rtrn = shell.commandStream;
            }
            assert output != null;
            if(output.contains("Error:") || output.contains("command not found") || output.contains("bad substitution")){
                //there was an error reported from container runtime
                logger.errorf("error starting %s container %s : %s",getHost().isLocal() ? "local" : "remote", getHost().getSafeString(), output);
                rtrn = null;
            }
        }
        return rtrn;
    }

    @Override
    public void postConnect(){
        String unameN = shSync("uname -n");
        if(!getHost().hasContainerId()){
            getHost().setContainerId(unameN);
            getHost().setNeedStopContainer(true);
        }else{
            if(!unameN.equals(getHost().getContainerId())){
                logger.error("container Id changed for "+getHost().getSafeString()+" "+unameN);
                //getHost().setContainerId(unameN);
            }
        }
    }

    /**
     * Will stop the container of the host indicates it was started by qDup
     */
    public void stopContainerIfStarted(){
        if(getHost().isContainer() && getHost().needStopContainer() && getHost().startedContainer()){
            if(getHost().hasStopContainer()){
                Host jumpHost = getHost().withoutContainer();
                AbstractShell closeShell = AbstractShell.getShell(jumpHost, getScheduledExector(), getFilter(), false);
                closeShell.setName(getName()+"-stop-container-sub-shell");
                Json json = new Json();
                json.set("host",getHost().toJson());
                json.set("image",getHost().getDefinedContainer());
                json.set("container",getHost().getDefinedContainer());
                json.set("containerId",getHost().getContainerId());
                String populatedCommand = ContainerShell.populateList(getHost().getStopContainer(),json);
                if(populatedCommand.contains(StringUtil.PATTERN_PREFIX)){
                    logger.error("failed to populate pattern to stop container\n"+populatedCommand+"\n"+getHost());
                }else{
                    String response = closeShell.shSync(populatedCommand);
                    //
                }
                if ( closeShell != null && closeShell.status != Status.Closing) {
                    closeShell.close(false);
                }
            }
        }
    }

    @Override
    public void exec(String command, Consumer<String> callback) {
        if(getHost().hasExec()){
            Json json = new Json();
            json.set("host",getHost().toJson());
            json.set("image",getHost().getDefinedContainer());
            json.set("container",containerId);//idk which we should use
            json.set("containerId",containerId);//TODO decide if container or containerId
            json.set("command",command);
            String populatedCommand = populateList(getHost().getExec(),json);
            if(populatedCommand.contains(StringUtil.PATTERN_PREFIX)){
                logPopulateError(populatedCommand,HostDefinition.EXEC);
                //how do we fail
            }else{
                String output = shell.execSync(populatedCommand);
                callback.accept(output);
            }
        }else{
            //is it okay for a host to not support exec??
        }
    }

    @Override
    public boolean isOpen() {
        if(shell == null || !shell.isOpen()){
            return false;
        }
//        String output = shell.execSync("podman ps --filter id="+containerId+" --format \"{{.ID}}\"");
        return true;
    }
    @Override
    public AbstractShell copy() {
        return new ContainerShell(
            getHost(),
            setupCommand,
            executor,
            getFilter(),
            trace
        );
    }

    @Override
    public void close() {
        if(shell!=null && shell.status != Status.Closing) {
            shell.close(false);
        }
    }
}
