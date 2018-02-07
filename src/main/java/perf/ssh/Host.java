package perf.ssh;

/**
 * Created by wreicher
 * A POJO for the host connection information. Does not support saving passwords, set up ssh keys :)
 */
public class Host {

    public static final int DEFAULT_PORT = 22;

    private String hostName;
    private String userName;
    private int port;
    public Host(String userName,String hostName){
        this(userName,hostName,DEFAULT_PORT);
    }
    public Host(String userName,String hostName,int port){
        this.userName = userName;
        this.hostName = hostName;
        this.port = port;
    }
    public String getUserName(){return userName;}
    public String getHostName(){return hostName;}
    public int getPort(){return port;}

    @Override
    public String toString(){return userName+"@"+hostName+":"+port;}

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
