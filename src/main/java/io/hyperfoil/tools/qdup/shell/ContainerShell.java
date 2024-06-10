package io.hyperfoil.tools.qdup.shell;

import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.SecretFilter;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.stream.SessionStreams;
import io.hyperfoil.tools.yaup.StringUtil;
import io.hyperfoil.tools.yaup.json.Json;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ContainerShell extends AbstractShell{
//TODO test if the image has to get pulled too

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
        List<String> populated = Cmd.populateList(variables,toPopulate);
        String populatedCommand = populated.stream().filter(v->v!=null && !v.isBlank()).collect(Collectors.joining(" "));
        return populatedCommand;
    }

    @Override
    void updateSessionStream(SessionStreams sessionStreams){

    }

    //TODO should also accept the state or some state representation
    @Override
    PrintStream connectShell() {
        int connectTimeoutSeconds = 10;
        //starts the shell
        PrintStream rtrn = null;
        //need an alt host that has the default connection method for local or remote shell, then we use provided host to connect to the container
        //how does this work if we want a custom shell (zsh?) on the alt host?
        //TODO how do we specify custom shell container sub-host?
        Host subHost = getHost().isLocal() ? new Host() : new Host(getHost().getUserName(),getHost().getHostName(),getHost().getPassword(),getHost().getPort());
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
        boolean connected = shell.connect();
        if(!connected){
            logger.error("failed to connect {} shell for container to {}",getHost().isLocal() ? "local" : "remote", getHost().getSafeString());
            return null;
        }
        boolean connectSetContainerId=false;
        String startError = null;
        
        if(getHost().hasContainerId()){ //an already running container
            containerId = getHost().getContainerId();
        }else{
            if(getHost().hasStartContainer()){
                Json json = new Json();
                json.set("host",getHost().toJson());
                json.set("image",getHost().getDefinedContainer());
                json.set("container",getHost().getDefinedContainer());
                json.set("containerId",getHost().getDefinedContainer());
                String populatedCommand = populateList(getHost().getStartContainer(),json);
                if(populatedCommand.contains(StringUtil.PATTERN_PREFIX)){
                    //TODO how to fail when container start fails
                }else{
                    containerId = shell.shSync(populatedCommand,null,connectTimeoutSeconds);
                    if(containerId.contains("\n") || containerId.isBlank()){
                        //assume the container started connected
                        //cannot shSync because connection is not ready...
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
                //how do we fail
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

                String fsz = shell.shSync(populatedConnectShell,null,connectTimeoutSeconds);
                if(fsz.isBlank()){
                    //we probably timed out due to container prompt
                }else{
                    //we probably got an error
                }
                try {
                    //TODO custom timeout
                    boolean acquired = connectingShellLock.tryAcquire(connectTimeoutSeconds, TimeUnit.MILLISECONDS);
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
                        if(getHost().hasCreateConnectedContainer()){
                            Json ccJson = new Json();
                            ccJson.set("host",getHost().toJson());
                            ccJson.set("image",getHost().getDefinedContainer());                            
                            String populatedCreateConnected = populateList(getHost().getCreateConnectedContainer(),ccJson);
                            if(populatedCreateConnected.contains(StringUtil.PATTERN_PREFIX)){
                                //TODO how do we fail in this case?
                            }else{
                                hasError.set(false);
                                SessionStreams currentShellSessionStreams = shell.getSessionStreams();
                                if(currentShellSessionStreams != getSessionStreams()){
                                    shell.setSessionStreams(getSessionStreams());
                                }
                                String output = shell.shSync(populatedCreateConnected,null,connectTimeoutSeconds);
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
                            logger.error("failed to connect to container shell for {}\n{}", getHost(), shell.getSessionStreams().currentOutput());
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
            String output = shell.shSync(populatedCommand,null,connectTimeoutSeconds);
            if(output!=null && output.isEmpty()){//output is empty when timeout triggers
                output = getSessionStreams().currentOutput();
                rtrn = shell.commandStream;
            }
        }
        return rtrn;
    }

    @Override
    public void postConnect(){
        if(!getHost().hasContainerId()){
            String unameN = shSync("uname -n");
            getHost().setContainerId(unameN);
            getHost().setNeedStopContainer(true);
        }else{
            String unameN = shSync("uname -n");
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
                AbstractShell shell = AbstractShell.getShell(jumpHost, getScheduledExector(), getFilter(), false);
                Json json = new Json();
                json.set("host",getHost().toJson());
                json.set("image",getHost().getDefinedContainer());
                json.set("container",getHost().getDefinedContainer());
                json.set("containerId",getHost().getContainerId());
                String populatedCommand = ContainerShell.populateList(getHost().getStopContainer(),json);
                if(populatedCommand.contains(StringUtil.PATTERN_PREFIX)){
                    logger.error("failed to populate pattern to stop container\n"+populatedCommand+"\n"+getHost());
                }else{
                    String response = shell.shSync(populatedCommand);
                    //
                }
                if ( shell != null && shell.status != Status.Closing) {
                    shell.close(true);
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
            List<String> populated = Cmd.populateList(json,getHost().getExec());
            if(Cmd.hasPatternReference(populated,StringUtil.PATTERN_PREFIX)){
                //how do we fail
            }else{
                String populatedCommand = populated.stream().filter(v->v!=null && !v.isBlank()).collect(Collectors.joining(" "));
                String output = shell.execSync("podman exec "+containerId+" "+command);
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
        if(shell!=null && shell.isOpen()) {
            shell.close();
        }
    }
}
