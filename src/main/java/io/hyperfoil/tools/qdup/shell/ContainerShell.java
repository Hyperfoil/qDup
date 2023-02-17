package io.hyperfoil.tools.qdup.shell;

import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.yaup.AsciiArt;
import io.hyperfoil.tools.yaup.PopulatePatternException;
import io.hyperfoil.tools.yaup.StringUtil;
import io.hyperfoil.tools.yaup.json.Json;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ContainerShell extends AbstractShell{

/*
    podman run --detach <image>
    podman exec --interactive --tty <name> /bin/bash
    podman-attach
 */

    private AbstractShell shell;
    private String containerId = null;

    public ContainerShell(Host host, String setupCommand, ScheduledThreadPoolExecutor executor, boolean trace) {
        super(host, setupCommand, executor, trace);
    }

    //TODO should aso accept the state or some state representation
    @Override
    PrintStream connectShell() {
        //starts the shell
        PrintStream rtrn = null;
        //need an alt host that has the default connection method for local or remote shell, then we use provided host to connect to the container
        //how doe this work if we want a custom shell on the alt host?
        //TODO how do we specify custom shell container sub-host?
        Host subHost = getHost().isLocal() ? new Host() : new Host(getHost().getUserName(),getHost().getHostName(),getHost().getPassword(),getHost().getPort());
        if(getHost().isLocal()){
            shell = new LocalShell(subHost,setupCommand,executor,trace);
        } else {
            shell = new SshShell(subHost,setupCommand,executor,trace);
        }
        boolean connected = shell.connect();
        if(!connected){
            logger.error("failed to connect {} shell for container to {}",getHost().isLocal() ? "local" : "remote", getHost().getSafeString());
            return null;
        }
        if(getHost().hasContainerId()){
            containerId = getHost().getContainerId();
        }else{
            if(getHost().hasStartContainer()){
                Json json = new Json();
                json.set("host",getHost().toJson());
                json.set("image",getHost().getDefinedContainer());
                List<String> populated = Cmd.populateList(json,getHost().getStartContainer());
                if(Cmd.hasPatternReference(populated,StringUtil.PATTERN_PREFIX)){
                    //how do we fail
                }else{
                    String populatedCommand = populated.stream().filter(v->v!=null && !v.isBlank()).collect(Collectors.joining(" "));
                    containerId = shell.shSync(populatedCommand);
                    getHost().setContainerId(containerId);
                }
            }else{
                //handled by containerId==null check later
            }
        }
        if(containerId==null || containerId.isBlank()){
            logger.error("failed to start container {}",getHost());
        }
        //this works for local podman but not podman on a remote connection
        //we need to hijack the ssh
        if(getHost().hasConnectShell()){
            Json json = new Json();
            json.set("host",getHost().toJson());
            json.set("image",getHost().getDefinedContainer());
            json.set("container",containerId);//idk which we should use
            json.set("containerId",containerId);//TODO decide if container or containerId
            List<String> populated = Cmd.populateList(json,getHost().getConnectShell());
            if(Cmd.hasPatternReference(populated,StringUtil.PATTERN_PREFIX)){
                //how do we fail
            }else{
                String populatedCommand = populated.stream().filter(v->v!=null && !v.isBlank()).collect(Collectors.joining(" "));
                Semaphore connectingShellLock = new Semaphore(0);
                AtomicBoolean hasError = new AtomicBoolean(false);
                String errorMessage = "Error";
                sessionStreams.addLineConsumer((line)->{
                    if( line.contains(errorMessage) || line.matches(errorMessage)){
                        hasError.set(true);
                    }
                    if(!line.isBlank()) {//how are we getting a blank line? is there an error in FilteredStream newline filtering after stripping command?
                        connectingShellLock.release();
                    }
                });
                //TODO replace with method that also adds the command from other sessionStreams
                shell.sessionStreams = sessionStreams;//moved before sh so filterStream would see the command
                shell.sh(populatedCommand,false,(prompt,output)->{ },null);//use sh without the lock
                long timeout = 2_000;
                try {
                    //TODO custom timeout
                    boolean acquired = connectingShellLock.tryAcquire(timeout, TimeUnit.MILLISECONDS);
                    if(!acquired){//this means we didn't get a message with a newline, we likely got the prompt.
                    }else{
                        //if we acquired the lock there was probably an error message
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if(hasError.get()){
                    logger.error("failed to connect container shell for {}",getHost());
                }else {
                    rtrn = shell.commandStream;
                }
            }
        }else{
            //Honestly an invalid host should not get to this point so we should not need to check it
        }
        return rtrn;
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
