package perf.ssh.config;

/**
 * Created by wreicher
 * A named HostList. Adds another convenience to associated hosts with scripts for a Run
 */
public class Role extends HostList {

    private String name;
    protected Role(String name){
        super();
        this.name = name;
    }

    public String getName(){return name;}

    @Override
    public String toString(){
        return name+" "+super.toString();
    }
}
