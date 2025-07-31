package io.hyperfoil.tools.qdup.shell;

import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.SecretFilter;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.config.yaml.HostDefinition;
import io.hyperfoil.tools.qdup.stream.SessionStreams;
import io.hyperfoil.tools.yaup.PopulatePatternException;
import io.hyperfoil.tools.yaup.StringUtil;
import io.hyperfoil.tools.yaup.json.Json;

import java.io.PrintStream;
import java.util.*;
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

    private static final String GET_HOST_OR_CONTAINER_ID = "cat /proc/1/sched | head -n 1";

    private AbstractShell shell;
    private String containerId = null;
    private String subShellIdentifier = null;

    public ContainerShell(String name,Host host, String setupCommand, ScheduledThreadPoolExecutor executor, SecretFilter filter, boolean trace) {
        super(name,host, setupCommand, executor, filter, trace);
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
        if(input == null){
            return true;//invalid input is an error? :)
        }
        return Stream.of("Error","error","-bash","command not found","select an image","no such object:","bad substitution")
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

    private String getCidFile(){
        return "/tmp/qdup."+getHost().getShortHostName()+"."+hashCode();
    }
    private void removeCidFile(){
        shellExecSync("rm "+getCidFile(),"remove-cidfile",10);
    }
    private SyncResponse shellShSync(String command,String description, int timeout){
        SyncResponse rtrn = null;
        if(command.contains(StringUtil.PATTERN_PREFIX)){
            logPopulateError(command,description);
            rtrn = new SyncResponse("Error: failed to populate pattern",true);
        }else {
            rtrn = shell.shSync(command, new HashMap<>(), timeout);
            logger.debugf("%s - %s%n  %s%n  timedOut: %s%n  response:%n%s",getName(),HostDefinition.CREATE_CONTAINER,command,rtrn.timedOut(),rtrn.output());
        }
        return rtrn;
    }
    private SyncResponse shellExecSync(String command,String description, int timeout){
        SyncResponse rtrn = null;
        if(command.contains(StringUtil.PATTERN_PREFIX)){
            logPopulateError(command,description);
            rtrn = new SyncResponse("Error: failed to populate pattern",true);
        }else {
            rtrn = shell.execSync(command, timeout);
        }
        return rtrn;
    }

    private boolean isContainerRunning(String containerId){
        SyncResponse response = shellExecSync(
                populateList(getHost().getCheckContainerStatus(), Json.fromMap(Map.of(
                    "containerId",containerId
                ))),
                HostDefinition.CHECK_CONTAINER_STATUS,
                10
        );
        return !response.timedOut() && response.output().contains("running");
    }

    private static record CommandResponse(String name,String command,SyncResponse response){}

    //TODO should also accept the state or some state representation
    @Override
    PrintStream connectShell() {

        List<CommandResponse> responses = new ArrayList<>();

        PrintStream rtrn = null;
        int asyncTimeoutSeconds = 10;
        Host subHost = getHost().withoutContainer();
        if(getHost().hasAlias()){
            subHost.setAlias(getHost().getAlias());
        }
        if(getHost().hasIdentity()){
            subHost.setIdentity(getHost().getIdentity());
        }
        if(getHost().hasPassphrase()){
            subHost.setPassphrase(getHost().getPassphrase());
        }
        if(getHost().isLocal()){
            shell = new LocalShell(getName()+"-sub-shell",subHost,setupCommand,executor,getFilter(),trace);
        } else {
            shell = new SshShell(getName()+"-sub-shell",subHost,setupCommand,executor,getFilter(),trace);
        }
        boolean connected = shell.connect();
        if(!connected){
            logger.errorf("failed to connect %s shell for container to %s",getHost().isLocal() ? "local" : "remote", getHost().getSafeString());
            return null;
        }
        boolean connectSetContainerId=false;
        SyncResponse shellIdentifierResponse = shell.execSync(GET_HOST_OR_CONTAINER_ID,asyncTimeoutSeconds);
        responses.add(new CommandResponse("subshell-identifier",GET_HOST_OR_CONTAINER_ID,shellIdentifierResponse));
        subShellIdentifier = shellIdentifierResponse.output().trim();//ignoring timeouts for now
        logger.debugf("%s subShell identifier = %s",getName(),subShellIdentifier);
        try {
            getHost().getContainerLock().acquire();
        } catch (InterruptedException e) {
            logger.error("interrupted trying to coordinate container creation for "+getName()+" on "+getHost().getAlias());
            Thread.currentThread().interrupt();
        }
        if(getHost().hasContainerId()){
            containerId = getHost().getContainerId();
        }else if (Host.isContainerId(getHost().getDefinedContainer())){
            containerId = getHost().getDefinedContainer();
        }else if (Host.isContainerName(getHost().getDefinedContainer()) && getHost().hasCheckContainerName()){
            String populated = populateList(getHost().getCheckContainerName(),Json.fromMap(Map.of(
                    "host",getHost().toJson(),
                    "image",getHost().getDefinedContainer(),
                    "container",getHost().getDefinedContainer()
            )));
            SyncResponse response = shellExecSync(
                    populated,
                    HostDefinition.CHECK_CONTAINER_NAME,
                    asyncTimeoutSeconds
            );
            responses.add(new CommandResponse(HostDefinition.CHECK_CONTAINER_NAME,populated,response));
            if(!response.timedOut() && !hasErrorMessage(response.output()) && Host.isContainerId(response.output().trim())){
                containerId = response.output().trim();
            }
        }
        //if we need to create a containerId
        if(containerId == null || containerId.isBlank() || !Host.isContainerId(containerId)){
            if(getHost().hasStartContainer()){
                String populatedCommand = populateList(getHost().getStartContainer(),Json.fromMap(Map.of(
                        "host",getHost().toJson(),
                        "image",getHost().getDefinedContainer(),
                        "container",getHost().getDefinedContainer(),
                        "containerId",getHost().getDefinedContainer(),
                        "cidfile",getCidFile()
                )));
                if(populatedCommand.contains("cidfile")){
                    removeCidFile();
                }
                SyncResponse response = shellShSync(
                        populatedCommand,
                        HostDefinition.CREATE_CONTAINER,
                        10
                );
                responses.add(new CommandResponse(HostDefinition.CREATE_CONTAINER,populatedCommand,response));
                if(hasErrorMessage(response.output())){
                    if(response.output().contains("select an image")) {
                        logger.errorf("error starting %s container %s is ambiguous", getHost().isLocal() ? "local" : "remote", containerId);
                    } else {
                        logger.errorf("error starting %s container %s : %s", getHost().isLocal() ? "local" : "remote", getHost().getSafeString(), containerId);
                    }
                } else if(response.timedOut() || populatedCommand.contains("PS1")){//likely happens when we connected to a terminal
                    if(populatedCommand.contains("cidfile")){//if we can check a cid file
                        String cid = shellExecSync(
                                "cat "+getCidFile(),
                                "read-cidfile",
                                10
                        ).output();
                        if(Host.isContainerId(cid)) {
                            containerId = cid;
                        }
                    }
                    if( rtrn == null ){//did not set rtrn from the cidfile or didn't have one
                        SyncResponse shellIdResponse = shellShSync(GET_HOST_OR_CONTAINER_ID,"get-host-or-container-id",10);
                        if(!subShellIdentifier.equals(shellIdResponse.output())){
                            rtrn = shell.commandStream;
                            shell.setSessionStreams(getSessionStreams());
                            //containerId will need to be set by the postConnect
                        }
                    }
                }
                if( rtrn == null && Host.isContainerId(response.output().trim())) {//command returned something or above failed to set the return
                    containerId = response.output().trim();
                }
            }
        }
        //try and restart a stopped container
        if( rtrn == null && Host.isContainerId(containerId) && !isContainerRunning(containerId) && getHost().hasRestartConnectedContainer()){//if the container isn't running
            String populatedCommand = populateList(
                getHost().getRestartConnectedContainer(),Json.fromMap(Map.of(
                "host",getHost().toJson(),
                "containerId",containerId
            )));
            //not sure this is necessary
//            if(!shell.getSessionStreams().equals(getSessionStreams())){
//                shell.setSessionStreams(getSessionStreams());
//            }
            SyncResponse response = shellShSync(populatedCommand,HostDefinition.RESTART_CONNECTED_CONTAINER,asyncTimeoutSeconds);
            responses.add(new CommandResponse(HostDefinition.RESTART_CONNECTED_CONTAINER,populatedCommand,response));
            if(hasErrorMessage(response.output())){
                logger.debugf(getName()+" error trying to restart container "+containerId+"\n"+response.output());
            }else{
                SyncResponse shellIdResponse = shellShSync(GET_HOST_OR_CONTAINER_ID,"get-host-or-container-id",10);
                if(!subShellIdentifier.equals(shellIdResponse.output())){
                    rtrn = shell.commandStream;
                    shell.setSessionStreams(getSessionStreams());
                    //containerId will need to be set by the postConnect
                }
                //else the containerId should now be started (should we check?)
            }
        }
        //try and connect to a running containerId
        if( rtrn == null && Host.isContainerId(containerId) && isContainerRunning(containerId) && getHost().hasConnectShell()){//if we do not yet feel connected to a container but we started one
            String populatedCommand = populateList(
                getHost().getConnectShell(),Json.fromMap(Map.of(
                "host",getHost().toJson(),
                "image",getHost().getDefinedContainer(),
                "container",containerId,
                "containerId",containerId
            )));
//            if(!shell.getSessionStreams().equals(getSessionStreams())){
//                shell.setSessionStreams(getSessionStreams());
//            }
            SyncResponse response = shellShSync(populatedCommand,HostDefinition.CONNECT_SHELL,asyncTimeoutSeconds);
            responses.add(new CommandResponse(HostDefinition.CONNECT_SHELL,populatedCommand,response));
            if(hasErrorMessage(response.output())){
                logger.debugf(getName()+" failed to connect to container "+containerId);
            }else{//check if the shellId changed
                SyncResponse shellIdResponse = shellShSync(GET_HOST_OR_CONTAINER_ID,"get-host-or-container-id",10);
                if(!subShellIdentifier.equals(shellIdResponse.output())){
                    rtrn = shell.commandStream;
                    shell.setSessionStreams(getSessionStreams());
                    //containerId will need to be set by the postConnect
                }else{//assume we failed to use containerId
                    //TODO do we remove the container if we created it from prevoius step?
                    containerId = null; //we cannot use the containerId, time to try creating a new one
                }
            }
        }
        //at this point we could not restart or connect to containerId, assume the value should be neglected
        //do we clear containerId and the host's containerId?
        if(rtrn == null && getHost().hasCreateConnectedContainer()){
            String populatedCommand = populateList(
                getHost().getCreateConnectedContainer(),Json.fromMap(Map.of(
                "host",getHost().toJson(),
                "image",getHost().getDefinedContainer(),
                "cidfile",getCidFile()
            )));
            if(populatedCommand.contains("cidfile")){
                removeCidFile();
            }
            SyncResponse response = shellShSync(populatedCommand,HostDefinition.CREATE_CONNECTED_CONTAINER,asyncTimeoutSeconds);
            responses.add(new CommandResponse(HostDefinition.CREATE_CONNECTED_CONTAINER,populatedCommand,response));
            if(hasErrorMessage(response.output())){
                logger.debugf(getName()+" failed to create a connected container "+getHost().getDefinedContainer()+"\n"+response.output());
            }else {
                //begin copy and paste from getStartContainer
                if(populatedCommand.contains("cidfile")){//if we can check a cid file
                    String cid = shellExecSync(
                            "cat "+getCidFile(),
                            "read-cidfile",
                            10
                    ).output();
                    if(Host.isContainerId(cid)) {

                        containerId = cid;
                    }
                }
                if( rtrn == null ){//did not set rtrn from the cidfile or didn't have one
                    SyncResponse shellIdResponse = shellShSync(GET_HOST_OR_CONTAINER_ID,"get-host-or-container-id",10);
                    if(!subShellIdentifier.equals(shellIdResponse.output())){
                        rtrn = shell.commandStream;
                        shell.setSessionStreams(getSessionStreams());
                        //containerId will need to be set by the postConnect
                    }
                }
                //end copy and paste from getStartContainer
            }
        }
        if(rtrn == null){
            String message = responses.stream().map(cr->cr.name+"\ncommand: "+cr.command+"\ntimeout: "+cr.response.timedOut()+"\noutput:\n"+cr.response.output()).collect(Collectors.joining("\n"));
            logger.errorf("%s failed to connect %s. Attempted:%n%s",getName(),getHost().getSafeString(),message);
        }
        return rtrn;
    }

    @Override
    public boolean postConnect(){
        boolean ok = true;
        if(!getHost().hasContainerId()) {
            if(containerId!=null && Host.isContainerId(containerId) && isContainerRunning(containerId)){
                getHost().setContainerId(containerId);
                getHost().setNeedStopContainer(true);
            }else {
                String command = "cat " + getCidFile();
                String unameN = shell.execSync(command);
                if (Host.isContainerId(unameN) && isContainerRunning(unameN)) {
                    getHost().setContainerId(unameN);
                    getHost().setNeedStopContainer(true);
                    containerId = unameN;
                } else {
                    SyncResponse hostnameResponse = shellShSync("cat /etc/hostname", "cat-hostname", 10);
                    if (Host.isContainerId(hostnameResponse.output().trim())) {
                        getHost().setContainerId(hostnameResponse.output().trim());
                        containerId = hostnameResponse.output().trim();
                    }
                }
            }
        }
        String unameN = shell.shSync(GET_HOST_OR_CONTAINER_ID);
        //it should always be 0...
        if(getHost().getContainerLock().availablePermits() == 0){
            getHost().getContainerLock().release();
        }else{
            //This should not happen
        }
        if (unameN.equals(subShellIdentifier)) {
            logger.error("failed to connect "+getHost().getShortHostName()+" to "+getHost().getDefinedContainer());
            ok = false;
        }else{
            //this logic is out-dated because we use cidfile and need to support hostname being overridden
/*            if(!unameN.equals(getHost().getContainerId())){
                logger.error("container Id changed for "+getHost().getSafeString()+" currently "+getHost().getContainerId()+" new value "+unameN);
                //TODO do we abort due to the wrong host connection? We are going to return a connection to the base Shell :(
                ok = false;
                //getHost().setContainerId(unameN);
            }*/
        }
        if(!ok){
            shell.close(false);
            this.setSessionStreams(null);

        }
        return ok;
    }

    /**
     * Will stop the container of the host indicates it was started by qDup
     */
    public void stopContainerIfStarted(){
        if(getHost().isContainer() && getHost().needStopContainer() && getHost().startedContainer()){
            if(getHost().hasStopContainer()){
                Host jumpHost = getHost().withoutContainer();
                AbstractShell closeShell = AbstractShell.getShell(jumpHost.getShortHostName()+"-stop-container",jumpHost, getScheduledExector(), getFilter(), false);
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
        if(shell!=null && shell.status != Status.Closing) {
            shell.close(false);
        }
    }
}
