package perf.qdup;

/**
 * Created by wreicher
 * A POJO for the host connection information. Does not support saving passwords, set up qdup keys :)
 */
public class Host {


    public static Host parse(String fullyQualified) {
        Host rtrn = null;
        if (fullyQualified.contains("@")) {
            String username = fullyQualified.substring(0, fullyQualified.indexOf("@"));
            String hostname = fullyQualified.substring(fullyQualified.indexOf("@") + 1);
            int port = DEFAULT_PORT;
            if (hostname.contains(":")) {
                port = Integer.parseInt(hostname.substring(hostname.indexOf(":") + 1));
                hostname = hostname.substring(0, hostname.indexOf(":"));
            }
            rtrn = new Host(username, hostname, port);
        }
        return rtrn;
    }
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
    public String getShortHostName(){
        if(hostName!=null && hostName.indexOf(".")>-1){
            return hostName.substring(0,hostName.indexOf("."));
        }else{
            return hostName;
        }
    }
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
