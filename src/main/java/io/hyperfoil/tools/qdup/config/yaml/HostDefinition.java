package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.yaup.json.Json;

import java.util.*;
import java.util.stream.Collectors;

public class HostDefinition {

    public static final String USERNAME = "username";
    public static final String HOSTNAME = "hostname";
    public static final String PASSWORD = "password";
    public static final String PORT = "port";
    public static final String PROMPT = "prompt";
    public static final String LOCAL = "local";
    public static final String PLATFORM = "platform";
    public static final String CONTAINER = "container";
    public static final String CHECK_CONTAINER_ID = "check-container-id";
    public static final String CHECK_CONTAINER_NAME = "check-container-name";
    public static final String START_CONTAINER = "start-container";
    public static final String STOP_CONTAINER = "stop-container";
    public static final String CONNECT_SHELL = "connect-shell";
    public static final String PLATFORM_LOGIN = "platform-login";
    public static final String EXEC = "exec";

    public static final List<String> KEYS = Arrays.asList(
            USERNAME,HOSTNAME,PASSWORD,PORT,PROMPT,LOCAL,PLATFORM,CONTAINER,CHECK_CONTAINER_ID,CHECK_CONTAINER_NAME,
            START_CONTAINER,STOP_CONTAINER,CONNECT_SHELL,PLATFORM_LOGIN,EXEC
    );

    public static List<String> unknownKeys(Collection<String> keys){
        List<String> rtrn = new ArrayList<>(keys);
        rtrn.removeAll(KEYS);
        return rtrn;
    }

    private String oneLine;
    private Json mapping;


    public HostDefinition(){
        this(null,new Json(false));
    }
    public HostDefinition(String oneLine){
        this(oneLine,new Json(false));
    }
    public HostDefinition(Json json){
        this(null,json);
    }
    private HostDefinition(String oneLine,Json mapping){
        this.oneLine = oneLine;
        this.mapping = mapping;
        if(mapping == null){
            //TODO fail if mapping is null?
        }
    }



    public boolean isOneLine(){
        return oneLine!=null;
    }
    public void set(String key,Object value){
        mapping.set(key,value);
    }

    public Host toHost(State state){
        Host rtrn = null;
        if(isOneLine()){
            String populated = Cmd.populateStateVariables(oneLine,null,state,null,null);
            rtrn = Host.parse(populated);
        }else{
            rtrn = new Host(
                    mapping.getString(USERNAME, Host.NO_USER),
                    mapping.getString(HOSTNAME, Host.NO_HOST),
                    mapping.getString(PASSWORD, Host.NO_PASSWORD),
                    (int)mapping.getLong(PORT, Host.DEFAULT_PORT),
                    mapping.getString(PROMPT,Host.NO_PROMPT),
                    mapping.getBoolean(LOCAL,false),
                    mapping.getString(PLATFORM,""),
                    mapping.getString(CONTAINER,"")
            );
            if(mapping.has(CHECK_CONTAINER_ID)){
                rtrn.setCheckContainerId(toList(mapping.get(CHECK_CONTAINER_ID)));
            }
            if(mapping.has(CHECK_CONTAINER_NAME)){
                rtrn.setCheckContainerName(toList(mapping.get(CHECK_CONTAINER_NAME)));
            }
            if(mapping.has(START_CONTAINER)){
                rtrn.setStartContainer(toList(mapping.get(START_CONTAINER)));
            }
            if(mapping.has(STOP_CONTAINER)){
                rtrn.setStopContainer(toList(mapping.get(STOP_CONTAINER)));
            }
            if(mapping.has(CONNECT_SHELL)){
                rtrn.setConnectShell(toList(mapping.get(CONNECT_SHELL)));
            }
            if(mapping.has(PLATFORM_LOGIN)){
                rtrn.setPlatformLogin(toList(mapping.get(PLATFORM_LOGIN)));
            }
            if(mapping.has(EXEC)){
                rtrn.setExec(toList(mapping.get(EXEC)));
            }
        }
        return rtrn;
    }
    private List<String> toList(Object object){
        if(object == null){
            return Arrays.asList(null);
        }else if(object instanceof Json){
            Json json = (Json)object;
            return Arrays.asList(json.values().stream().map(Object::toString).collect(Collectors.joining()));
        }else{
            return Arrays.asList(object.toString());
        }
    }

    public Object toYaml(){
        if(isOneLine()){
            return oneLine;
        }else{
            return mapping.clone();
        }
    }
}
