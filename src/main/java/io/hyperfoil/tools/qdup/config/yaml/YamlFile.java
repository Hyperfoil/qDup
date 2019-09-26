package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.Script;
import io.hyperfoil.tools.qdup.config.Role;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class YamlFile {

    private String path;
    private String name;
    private Map<String, Script> scripts;
    private Map<String, Host> hosts;
    private State state;
    private Map<String, Role> roles;

    public YamlFile(){
        name = "";
        scripts = new LinkedHashMap<>();
        hosts = new LinkedHashMap<>();
        state = new State(State.RUN_PREFIX);
        roles = new LinkedHashMap<>();
    }

    public void addHost(String name,Host host){
        hosts.put(name,host);
    }
    public void addScript(String name,Script script){
        scripts.put(name,script);
    }
    public void addRole(String name,Role role){
        roles.putIfAbsent(name,role);
    }
    public void setName(String name){
        this.name = name;
    }
    public void setPath(String path){
        this.path = path;
        scripts.values().forEach(script->{
            script.with(RunConfigBuilder.SCRIPT_DIR,path);
        });
    }

    public String getName(){return name;}
    public String getPath(){return path;}
    public Map<String,Host> getHosts(){
        return Collections.unmodifiableMap(hosts);
    }
    public Map<String,Script> getScripts(){
        return Collections.unmodifiableMap(scripts);
    }
    public Map<String,Role> getRoles(){
        return Collections.unmodifiableMap(roles);
    }
    public State getState(){
        return state;
    }

}
