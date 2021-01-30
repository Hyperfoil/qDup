package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.yaup.AsciiArt;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Created by wreicher
 * A POJO for the host connection information. Does not support saving passwords, set up qdup keys :)
 */
public class Host {

    public static final String HOST_PATTERN = "\\w+(?::.*?)@\\w[\\w\\-]*(?:\\.\\w[\\w\\-])*(?::\\d+)*.*";

    public static Host parse(String fullyQualified) {
        Host rtrn = null;
        if (fullyQualified.contains("@")) {
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
            rtrn = new Host(username, hostname, password, port);
        }
        return rtrn;
    }
    public static final int DEFAULT_PORT = 22;

    private String hostName;
    private String password;
    private String userName;
    private int port;
    public Host(String userName,String hostName){
        this(userName,hostName,DEFAULT_PORT);
    }
    public Host(String userName,String hostName,int port){
        this(userName,hostName,null,port);
    }
    public Host(String userName,String hostName,String password,int port){
        this.userName = userName;
        this.hostName = hostName;
        this.password = password;
        this.port = port;
    }
    public boolean hasPassword(){return password!=null && !password.isEmpty();}
    public String getPassword(){return password;}
    public String getUserName(){return userName;}
    public String getHostName(){return hostName;}
    public String getShortHostName(){
        if(hostName!=null && hostName.indexOf(".")>-1){
            return hostName.substring(0,hostName.indexOf("."));
        }else{
            return hostName;
        }
    }
    public int getPort(){return port;}

    @Override
    public String toString(){
        return userName+(hasPassword()?":"+password:"")+"@"+hostName+":"+port;
    }

    public String getSafeString(){
        return userName+(hasPassword()?":********":"")+"@"+hostName+":"+port;
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
