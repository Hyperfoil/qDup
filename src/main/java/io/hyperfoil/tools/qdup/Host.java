package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.HostDefinition;
import io.hyperfoil.tools.yaup.json.Json;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by wreicher
 * Contains host connection information.
 */
public class Host {

    public static final List<String> PODMAN_LOGIN = Arrays.asList("podman login -u ${{host.username}} -p ${{host.password}} ${{target}}").stream().filter(v->v!=null && !v.isBlank()).collect(Collectors.toUnmodifiableList());
    public static final List<String> DOCKER_LOGIN = Arrays.asList("docker login -u ${{host.username}} -p ${{host.password}} ${{target}}").stream().filter(v->v!=null && !v.isBlank()).collect(Collectors.toUnmodifiableList());
    public static final List<String> PODMAN_START_CONTAINER = Arrays.asList("podman run --detach ${{image}}").stream().filter(v->v!=null && !v.isBlank()).collect(Collectors.toUnmodifiableList());
    public static final List<String> DOCKER_START_CONTAINER = Arrays.asList("docker run --detach ${{image}}").stream().filter(v->v!=null && !v.isBlank()).collect(Collectors.toUnmodifiableList());
    public static final List<String> PODMAN_CREATE_CONNECTED_CONTAINER = Arrays.asList("podman run --interactive --tty ${{image}} /bin/bash").stream().filter(v->v!=null && !v.isBlank()).collect(Collectors.toUnmodifiableList());
    public static final List<String> DOCKER_CREATE_CONNECTED_CONTAINER = Arrays.asList("docker run --interactive --tty ${{image}} /bin/bash").stream().filter(v->v!=null && !v.isBlank()).collect(Collectors.toUnmodifiableList());
    public static final List<String> PODMAN_STOP_CONTAINER = Arrays.asList("podman stop ${{containerId}}").stream().filter(v->v!=null && !v.isBlank()).collect(Collectors.toUnmodifiableList());
    public static final List<String> DOCKER_STOP_CONTAINER = Arrays.asList("docker stop ${{containerId}}").stream().filter(v->v!=null && !v.isBlank()).collect(Collectors.toUnmodifiableList());
    public static final List<String> PODMAN_REMOVE_CONTAINER = Arrays.asList("podman rm ${{containerId}}").stream().filter(v->v!=null && !v.isBlank()).collect(Collectors.toUnmodifiableList());
    public static final List<String> DOCKER_REMOVE_CONTAINER = Arrays.asList("docker rm ${{containerId}}").stream().filter(v->v!=null && !v.isBlank()).collect(Collectors.toUnmodifiableList());
    public static final List<String> PODMAN_CONNECT_SHELL = Arrays.asList("podman exec --interactive --tty ${{container}} /bin/bash").stream().filter(v->v!=null && !v.isBlank()).collect(Collectors.toUnmodifiableList());
    public static final List<String> DOCKER_CONNECT_SHELL = Arrays.asList("docker exec --interactive --tty ${{container}} /bin/bash").stream().filter(v->v!=null && !v.isBlank()).collect(Collectors.toUnmodifiableList());
    public static final List<String> PODMAN_START_CONNECTED_CONTAINER = Arrays.asList("podman start --interactive --attach ${{containerId}}").stream().filter(v->v!=null && !v.isBlank()).collect(Collectors.toUnmodifiableList());
    public static final List<String> DOCKER_START_CONNECTED_CONTAINER = Arrays.asList("docker start --interactive --attach ${{containerId}}").stream().filter(v->v!=null && !v.isBlank()).collect(Collectors.toUnmodifiableList());
    public static final List<String> DOCKER_OLD_START_CONNECTED_CONTAINER = Arrays.asList("docker run --interactive --tty ${{image}} /bin/bash").stream().filter(v->v!=null && !v.isBlank()).collect(Collectors.toUnmodifiableList());
    public static final List<String> PODMAN_EXEC = Arrays.asList("podman exec ${{container}} ${{command}}").stream().filter(v->v!=null && !v.isBlank()).collect(Collectors.toUnmodifiableList());
    public static final List<String> DOCKER_EXEC = Arrays.asList("docker exec ${{container}} ${{command}}").stream().filter(v->v!=null && !v.isBlank()).collect(Collectors.toUnmodifiableList());
    public static final List<String> PODMAN_CHECK_CONTAINER_ID = Arrays.asList("podman ps --filter id=${{container}} --format=\"{{.ID}}\"");
    public static final List<String> DOCKER_CHECK_CONTAINER_ID = Arrays.asList("docker ps --filter id=${{container}} --format=\"{{.ID}}\"");
    public static final List<String> PODMAN_CHECK_CONTAINER_NAME = Arrays.asList("podman ps --filter name=${{container}} --format=\"{{.Names}}\"");
    public static final List<String> DOCKER_CHECK_CONTAINER_NAME = Arrays.asList("docker ps --filter name=${{container}} --format=\"{{.Names}}\"");
    public static final List<String> PODMAN_UPLOAD = Arrays.asList("podman","cp","${{source}}","${{container}}:${{destination}}");
    public static final List<String> DOCKER_UPLOAD = Arrays.asList("docker","cp","${{source}}","${{container}}:${{destination}}");
    public static final List<String> PODMAN_DOWNLOAD = Arrays.asList("podman","cp","${{container}}:${{source}}","${{destination}}");
    public static final List<String> DOCKER_DOWNLOAD = Arrays.asList("docker","cp","${{container}}:${{source}}","${{destination}}");
    public static final List<String> PODMAN_FILE_SIZE = Arrays.asList("podman exec ${{container}} du -bc ${{source}} | grep total | cut -d '\t' -f1");
    public static final List<String> DOCKER_FILE_SIZE = Arrays.asList("docker exec ${{container}} du -bc ${{source}} | grep total | cut -d '\t' -f1");

    //worked on old fedora but not on fedora 39
    //public static final List<String> LOCAL_LINUX_CONNECT_SHELL = Arrays.asList("script", "-q", "/dev/null","/bin/bash");
    public static final List<String> LOCAL_LINUX_CONNECT_SHELL = Arrays.asList("script", "-q","-c","/bin/bash","/dev/null");
    public static final List<String> LOCAL_MACOS_CONNECT_SHELL = Arrays.asList("script", "-q", "/dev/null", "/bin/bash");
    //LOCAL_EXEC uses System.getRuntime().exec(...)
    public static final List<String> LOCAL_LINUX_UPLOAD = Arrays.asList("cp","-r","${{source}}","${{destination}}");
    public static final List<String> LOCAL_LINUX_DOWNLOAD = Arrays.asList("cp","-r","${{source}}","${{destination}}");
    public static final List<String> LOCAL_LINUX_FILE_SIZE = Arrays.asList("du","-cb","${{source}}","|","grep","total","|","cut","-d","\t","-f1");

    private static final String SSH_FILE_PASS = "${{= host.password ? `sshpass -p ${host.password}` : \"\"}}";
    private static final String SSH_OPTS =
        "/usr/bin/ssh " +
        "${{=knownHost ? '-o UserKnownHostsFile=\"${{knownHost}}\"' : '-o UserKnownHostsFile=/dev/null -o LogLevel=ERROR'}} " +
        "${{=identity ? '-i \"${{identity}}\" ' : \"\"}} " +
        "${{=host.port ? `-p ${host.port} ` : ''}} " +
        "-o StrictHostKeyChecking=no";
    public static final List<String> SSH_UPLOAD = Arrays.asList(
        SSH_FILE_PASS,
        "/usr/bin/rsync",
        "--archive",
        "--verbose",
        "--compress",
        "-e",
        SSH_OPTS,
        "--ignore-times",
        "${{source}}",
        "${{host.username}}@${{host.hostname}}:${{destination}}"
    );
    public static final List<String> SSH_DOWNLOAD = Arrays.asList(
        SSH_FILE_PASS,
        "/usr/bin/rsync",
        "--archive",
        "--verbose",
        "--compress",
        "-e",
        SSH_OPTS,
        "${{='${{source}}'.includes('/./') ? '--relative' : ''}}",
        "${{host.username}}@${{host.hostname}}:${{source}}",
        "${{destination}}"
    );
    public static final List<String> SSH_FILE_SIZE = Arrays.asList(
        SSH_FILE_PASS,
        "/usr/bin/rsync",
        "--archive",
        "--verbose",
        "--compress",
        "-e",
        SSH_OPTS,
        "${{='${{source}}'.includes('/./') ? '--relative' : ''}}",
        "${{host.username}}@${{host.hostname}}:${{source}}",
        "--stats",
        "--dry-run",
        "|",
        "grep",
        "total size is",
        "|",
        "cut",
        "-d",
        " ",
        "-f4"
    );
    public static final String HOST_PATTERN = "\\w+(?::.*?)@\\w[\\w\\-]*(?:\\.\\w[\\w\\-])*(?::\\d+)*.*";
    public static final String NO_PLATFORM = "";
    public static final String NO_PROMPT = null;
    public static final String NO_USER = "";
    public static final String NO_HOST = "";
    public static final String NO_PASSWORD = null;
    public static final String NO_CONTAINER = "";
    public static final String LOCAL = "LOCAL";
    public static final String CONTAINER_SEPARATOR = "//";

    public static Host parse(String fullyQualified) {
        Host rtrn = null;
        String container = NO_CONTAINER;
        String platform = "podman";
        if(fullyQualified == null || fullyQualified.isBlank()){
            return new Host(NO_USER,NO_HOST,NO_PASSWORD,22,NO_PROMPT,true,platform,NO_CONTAINER);
        }


        if (fullyQualified.contains(CONTAINER_SEPARATOR)){
            container = fullyQualified.substring(fullyQualified.indexOf(CONTAINER_SEPARATOR)+CONTAINER_SEPARATOR.length());
            fullyQualified = fullyQualified.substring(0,fullyQualified.indexOf(CONTAINER_SEPARATOR));
        }
        if (LOCAL.equals(fullyQualified)){
            return new Host(NO_USER,NO_HOST,NO_PASSWORD,22,NO_PROMPT,true,platform,container);
        }
        if (fullyQualified.contains("@")) {//remote host
            String password = null;
            String username = fullyQualified.substring(0, fullyQualified.lastIndexOf("@"));
            if(username.contains(":")){
                String tmpUsername = username.substring(0,username.indexOf(":"));
                password = username.substring(username.indexOf(":")+1);
                username = tmpUsername;
            }
            String hostname = fullyQualified.substring(fullyQualified.indexOf("@") + 1);
            int port = DEFAULT_PORT;
            if (hostname.contains(":")) {
                port = Integer.parseInt(hostname.substring(hostname.indexOf(":") + 1));
                hostname = hostname.substring(0, hostname.indexOf(":"));
            }
            rtrn = new Host(username, hostname, password, port, null, false, platform, container);
        }else if (fullyQualified.contains("/")){//fully qualified is the container
            if(container!=NO_CONTAINER){
                //this shouldn't happen, we have  a problem
            }
            return new Host(NO_USER,NO_HOST,NO_PASSWORD,22,NO_PROMPT,true,platform,fullyQualified);
        }
        return rtrn;
    }
    public static boolean isResolvableHostName(String hostname){
        boolean rtrn = false;
        try {
            InetAddress address = InetAddress.getByName(hostname);
            rtrn = true;
        } catch (UnknownHostException e) {

        }
        return rtrn;
    }
    public static final int DEFAULT_PORT = 22;

    private String hostName;
    private String password;
    private String userName;
    private int port;
    private boolean isShell = true;
    private String prompt = null; //
    private boolean isLocal = false;
    private String identity = RunConfigBuilder.DEFAULT_IDENTITY;
    private String passphrase = RunConfigBuilder.DEFAULT_PASSPHRASE;

    //settings for containers
    private String platform;//currently only podman, eventually docker, oc, kubernetes
    private String container;//the target for the container can be an image or an already running container ID or container name
    private String containerId;
    //assume we connect to a running container
    private boolean needStopContainer = false;
    //things that normally just use the default
    private List<String> getFileSize;
    private List<String> upload;
    private List<String> download;

    private List<String> checkContainerId;
    private List<String> checkContainerName;
    private List<String> createConnectedContainer;
    private List<String> startContainer;
    private List<String> startConnectedContainer;
    private List<String> stopContainer;
    private List<String> connectShell;
    private List<String> removeContainer;
    private List<String> platformLogin;
    private List<String> exec;

    /**
     * creates a new local host reference
     */
    public Host(){
        this(NO_USER,LOCAL,DEFAULT_PORT);
    }
    public Host(String userName,String hostName){

        this(userName,hostName,DEFAULT_PORT);
    }
    public Host(String userName,String hostName,int port){
        this(userName,hostName,null,port,NO_PROMPT,LOCAL.equals(hostName),null,null);
    }
    public Host(String userName,String hostName,String password,int port){this(userName, hostName,password,port,NO_PROMPT,false,"","");}
    public Host(String userName,String hostName,String password,int port,String prompt,boolean isLocal,String platform,String container){
        this.userName = userName;
        this.hostName = hostName;
        this.password = password;
        this.port = port;
        this.isShell = prompt == NO_PROMPT || prompt.isBlank();
        this.prompt = prompt;
        this.isLocal = isLocal;
        if(isLocal){
            this.hostName=LOCAL;
        }
        this.container = container;
        this.containerId = null;
        this.platform = platform;
        //set from default then
        this.identity = RunConfigBuilder.DEFAULT_IDENTITY;
        this.passphrase = RunConfigBuilder.DEFAULT_PASSPHRASE;
        if(isContainer()){
            switch (platform.toLowerCase()){
                case "podman":
                    this.getFileSize=PODMAN_FILE_SIZE;
                    this.upload=PODMAN_UPLOAD;
                    this.download=PODMAN_DOWNLOAD;
                    this.checkContainerId=PODMAN_CHECK_CONTAINER_ID;
                    this.checkContainerName=PODMAN_CHECK_CONTAINER_NAME;
                    this.createConnectedContainer=PODMAN_CREATE_CONNECTED_CONTAINER;
                    this.startContainer=PODMAN_START_CONTAINER;
                    this.startConnectedContainer=PODMAN_START_CONNECTED_CONTAINER;
                    this.stopContainer=PODMAN_STOP_CONTAINER;
                    this.connectShell=PODMAN_CONNECT_SHELL;
                    this.removeContainer=PODMAN_REMOVE_CONTAINER;
                    this.exec=PODMAN_EXEC;
                    break;
                case "docker":
                    this.getFileSize=DOCKER_FILE_SIZE;
                    this.upload=DOCKER_UPLOAD;
                    this.download=DOCKER_DOWNLOAD;
                    this.checkContainerId=DOCKER_CHECK_CONTAINER_ID;
                    this.checkContainerName=DOCKER_CHECK_CONTAINER_NAME;
                    this.createConnectedContainer=DOCKER_CREATE_CONNECTED_CONTAINER;
                    this.startContainer=DOCKER_START_CONTAINER;
                    this.startConnectedContainer=DOCKER_START_CONNECTED_CONTAINER;
                    this.stopContainer=DOCKER_STOP_CONTAINER;
                    this.connectShell=DOCKER_CONNECT_SHELL;
                    this.removeContainer=DOCKER_REMOVE_CONTAINER;
                    this.exec=DOCKER_EXEC;
                    break;
                default:
                    this.getFileSize= Collections.EMPTY_LIST;
                    this.upload=Collections.EMPTY_LIST;
                    this.download=Collections.EMPTY_LIST;
                    this.checkContainerId=Collections.EMPTY_LIST;
                    this.checkContainerName=Collections.EMPTY_LIST;
                    this.startContainer=Collections.EMPTY_LIST;
                    this.startConnectedContainer=Collections.EMPTY_LIST;
                    this.stopContainer=Collections.EMPTY_LIST;
                    this.connectShell=Collections.EMPTY_LIST;
                    this.removeContainer=Collections.EMPTY_LIST;
                    this.exec=Collections.EMPTY_LIST;
                    //support custom but assume the container commands are set
            }
        }else if (isLocal){
            this.getFileSize= LOCAL_LINUX_FILE_SIZE;
            this.upload= LOCAL_LINUX_UPLOAD;
            this.download= LOCAL_LINUX_DOWNLOAD;
            this.checkContainerId=Collections.EMPTY_LIST;
            this.checkContainerName=Collections.EMPTY_LIST;
            this.createConnectedContainer=Collections.EMPTY_LIST;
            this.startContainer=Collections.EMPTY_LIST;
            this.startConnectedContainer=Collections.EMPTY_LIST;
            this.stopContainer=Collections.EMPTY_LIST;
            if (System.getProperty("os.name").toUpperCase().contains("MAC")) {
                this.connectShell = LOCAL_MACOS_CONNECT_SHELL;
            } else {
                this.connectShell = LOCAL_LINUX_CONNECT_SHELL;
            }
            this.removeContainer=Collections.EMPTY_LIST;
            this.exec=Collections.EMPTY_LIST;//uses system.getRuntime().exec()
        }else{//ssh, remember when that was the only use case?
            this.getFileSize= SSH_FILE_SIZE;
            this.upload= SSH_UPLOAD;
            this.download= SSH_DOWNLOAD;
            this.checkContainerId=Collections.EMPTY_LIST;
            this.checkContainerName=Collections.EMPTY_LIST;
            this.createConnectedContainer=Collections.EMPTY_LIST;
            this.startContainer=Collections.EMPTY_LIST;
            this.stopContainer=Collections.EMPTY_LIST;
            this.connectShell=Collections.EMPTY_LIST;//uses ssh and accepts the default shell
            this.removeContainer=Collections.EMPTY_LIST;
            this.exec=Collections.EMPTY_LIST;//uses the ssh exec channel
        }
    }
    private boolean addError(List<String> errors, String message){
        if(errors!=null && message!=null ){
            errors.add(message);
        }
        return false;
    }
    public Host withoutContainer(){
        Json json = toJson();
        json.remove(HostDefinition.CONTAINER);
        json.remove(HostDefinition.CONTAINER_ID);
        HostDefinition definition = new HostDefinition(json);
        return definition.toHost(null);
    }
    public boolean isValid(){
        return isValid(null);
    }
    private boolean isImageId(String container){
        return container!=null && !container.isBlank() && container.contains("/");
    }
    public boolean isValid(List<String> errors){
        boolean rtrn = true;
        if(!isLocal()){
            if(!hasUsername()){
                rtrn = addError(errors,"missing a username for host "+this);
            }
            if(!hasHostName()){
                rtrn = addError(errors, "missing a hostname for host "+this);
            }
            if(getPort()<0 || getPort()>65535){
                rtrn = addError(errors, "invalid port number "+this);
            }
        }
        if(isContainer()){
            if(!hasCheckContainerId() && !hasCheckContainerName()){
                rtrn = addError(errors,"containerized host needs either checkContainerId or checkContainerName");
            }
            if(isImageId(getDefinedContainer()) && !hasStartContainer()){
                rtrn = addError(errors,"containerized host of image "+getDefinedContainer()+" needs a startContainer command");
            }
        }
        if((isContainer() || isLocal()) &&  !hasConnectShell()){
            rtrn = addError(errors,"host cannot connect without a connectShell command "+this);
        }


        return rtrn;
    }

    private boolean hasProcessArgs(List<String> args){
        return args!=null && !args.isEmpty() && args.stream().anyMatch(v->{
            return v!=null && !v.isBlank();
        });
    }
    public boolean hasFileSize(){
        return hasProcessArgs(getFileSize);
    }
    public List<String> getGetFileSize() {
        return getFileSize;
    }
    public void setGetFileSize(List<String> getFileSize) {
        this.getFileSize = getFileSize;
    }
    public boolean hasUpload(){
        return hasProcessArgs(upload);
    }
    public List<String> getUpload() {
        return upload;
    }
    public void setUpload(List<String> upload) {
        this.upload = upload;
    }
    public boolean hasDownload(){
        return hasProcessArgs(download);
    }
    public List<String> getDownload() {
        return download;
    }
    public void setDownload(List<String> download) {
        this.download = download;
    }
    public boolean hasCheckContainerId(){return hasProcessArgs(checkContainerId);}
    public List<String> getCheckContainerId() {
        return checkContainerId;
    }
    public void setCheckContainerId(List<String> checkContainerId) {
        this.checkContainerId = checkContainerId;
    }
    public boolean hasCheckContainerName(){return hasProcessArgs(checkContainerName);}
    public List<String> getCheckContainerName() {
        return checkContainerName;
    }
    public void setCheckContainerName(List<String> checkContainerName) {
        this.checkContainerName = checkContainerName;
    }
    public boolean hasStartContainer(){return hasProcessArgs(startContainer);}
    public List<String> getStartContainer() {
        return startContainer;
    }
    public void setStartContainer(List<String> startContainer) {
        this.startContainer = startContainer;
    }
    public boolean hasCreateConnectedContainer(){
        return hasProcessArgs(createConnectedContainer);
    }
    public List<String> getCreateConnectedContainer(){
        return createConnectedContainer;
    }
    public void setCreateConnectedContainer(List<String> createConnectedContainer){
        this.createConnectedContainer = createConnectedContainer;
    }
    public boolean hasStartConnectedContainer(){return hasProcessArgs(startConnectedContainer);}
    public List<String> getStartConnectedContainer(){return startConnectedContainer;}
    public void setStartConnectedContainer(List<String> startConnectedContainer){
        this.startConnectedContainer = startConnectedContainer;
    }
    public boolean hasStopContainer(){return hasProcessArgs(stopContainer);}
    public List<String> getStopContainer() {
        return stopContainer;
    }
    public void setStopContainer(List<String> stopContainer) {
        this.stopContainer = stopContainer;
    }
    public boolean hasConnectShell(){return hasProcessArgs(connectShell);}
    public List<String> getConnectShell() {
        return connectShell;
    }
    public void setConnectShell(List<String> connectShell) {
        this.connectShell = connectShell;
    }
    public boolean hasRemoveContainer(){return hasProcessArgs(removeContainer);}
    public List<String> getRemoveContainer() {
        return removeContainer;
    }
    public void setRemoveContainer(List<String> removeContainer) {
        this.removeContainer = removeContainer;
    }
    public boolean hasPlatformLogin(){return hasProcessArgs(platformLogin);}
    public List<String> getPlatformLogin() {
        return platformLogin;
    }
    public void setPlatformLogin(List<String> platformLogin) {
        this.platformLogin = platformLogin;
    }
    public boolean hasExec(){return hasProcessArgs(exec);}
    public List<String> getExec() {
        return exec;
    }
    public void setExec(List<String> exec) {
        this.exec = exec;
    }
    public boolean hasPlatform(){return !NO_PLATFORM.equals(platform);}
    public String getPlatform(){return platform;}
    public void setPlatform(String platform){
        this.platform = platform;
    }
    public boolean hasContainerId(){
        return containerId!=null && !containerId.isBlank();
    }
    public String getContainerId(){return containerId;}
    public void setContainerId(String containerId){
        this.containerId = containerId;
    }
    public void setIdentity(String identity){
        this.identity = identity;
    }
    public String getIdentity(){return identity;}
    
    public void setPassphrase(String passphrase){
        this.passphrase = passphrase;
    }
    public String getPassphrase(){return passphrase;}
    public boolean hasPassphrase(){return RunConfigBuilder.DEFAULT_PASSPHRASE != passphrase;}
    public boolean hasIdentity(){return !RunConfigBuilder.DEFAULT_IDENTITY.equals(identity);}
    public boolean isContainer(){return container!=null && !container.trim().isEmpty();}
    public boolean needStopContainer(){return needStopContainer;}
    public void setNeedStopContainer(boolean needStopContainer){
        this.needStopContainer = needStopContainer;
    }
    public String getDefinedContainer(){return container;}

    /**
     * Returns true if the host has a containerId that is different than what was defined as the container
     * This is used to determine if the container should be stopped after the run.
     * @return
     */
    public boolean startedContainer(){
        return isContainer() && hasContainerId() && !getDefinedContainer().equals(getContainerId());
    }

    public boolean isLocal(){return isLocal;}
    public boolean hasPrompt(){return prompt!=null && !prompt.isEmpty();}
    public String getPrompt(){return prompt;}
    public boolean isShell(){return isShell;}
    public boolean hasPassword(){return password!=null && !password.isEmpty();}
    public String getPassword(){return password;}
    public boolean hasUsername(){return userName!=null && !userName.isBlank();}
    public String getUserName(){return userName;}
    public boolean hasHostName(){return hostName!=null && !LOCAL.equals(hostName) && !hostName.isBlank();}
    public String getHostName(){return hostName;}
    public String getShortHostName(){
        if(hostName!=null && hostName.indexOf(".")>-1){
            return hostName.substring(0,hostName.indexOf("."));
        }else{
            return hostName;
        }
    }
    public int getPort(){return port;}

    private List<String> populateList(State state,List<String> list){
        return list.stream().map(v->Cmd.populateStateVariables(v,null,state,null,null,null,true)).collect(Collectors.toUnmodifiableList());
    }

    private String nullOrPopulate(String input,State state){
        return input == null ? null : Cmd.populateStateVariables(input,null,state,null,null);
    }
    public void populate(State state){
        hostName = nullOrPopulate(hostName,state);
        password = nullOrPopulate(password,state);
        userName = nullOrPopulate(userName,state);
        prompt = nullOrPopulate(prompt,state);
        identity = nullOrPopulate(identity,state);
        passphrase = nullOrPopulate(passphrase,state);
        platform = nullOrPopulate(platform,state);
        container = nullOrPopulate(container,state);
        getFileSize = populateList(state,getFileSize);
        upload = populateList(state,upload);
        download = populateList(state,download);
        checkContainerId = populateList(state,checkContainerId);
        checkContainerName = populateList(state,checkContainerName);
        startContainer = populateList(state,startContainer);
        stopContainer = populateList(state,stopContainer);
        connectShell = populateList(state,connectShell);
        removeContainer = populateList(state,removeContainer);
        exec = populateList(state,exec);
    }

    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder();
        if(isLocal()){
            sb.append(Host.LOCAL);
        }else{
            sb.append(userName + (hasPassword() ? ":"+getPassword() : "") + "@" + hostName + ":" + port);
        }
        if(isContainer()){
            if(sb.length()>0){
                sb.append(CONTAINER_SEPARATOR);
            }
            sb.append(getDefinedContainer());
        }
        return sb.toString();
    }
    public Json toJson(){
        return toJson(false);
    }
    public Json toJson(boolean safe){
        Json rtrn = new Json(false);
        if(isLocal){
            rtrn.set("local",true);
        }else{
            rtrn.set("local",false);
            rtrn.set("username",getUserName());
            rtrn.set("hostname",getHostName());
            if(!safe && hasPassword()){
                rtrn.set("password",getPassword());
            }
            rtrn.set("port",getPort());
        }
        if(hasPrompt()){
            rtrn.set("prompt",getPrompt());
        }
        if(hasIdentity()){
            rtrn.set("identity",getIdentity());
        }
        if(!isShell()){
            rtrn.set("shShell",false);
        }
        if(isContainer()){
            rtrn.set("platform",platform);
            if(hasContainerId()){
                rtrn.set("containerId",containerId);
            }
            if(isContainer()){
                rtrn.set("container",container);
            }
            //TODO add any custom container commands
        }
        return rtrn;
    }

    public String getSafeString(){
        StringBuilder sb = new StringBuilder();
        if(isLocal()){
            sb.append(Host.LOCAL);
        }else{
            sb.append(userName + (hasPassword() ? ":********" : "") + "@" + hostName + ":" + port);
        }
        if(isContainer()){
            if(sb.length()>0){
                sb.append(CONTAINER_SEPARATOR);
            }
            sb.append(getDefinedContainer());
        }
        return sb.toString();
    }

    @Override
    public int hashCode(){return toString().hashCode();}
    @Override
    public boolean equals(Object object){
        if(object instanceof Host && object!=null){
            return toString().equals(object.toString());
        }
        return false;
    }
}
