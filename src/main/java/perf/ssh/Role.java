package perf.ssh;

/**
 * Created by wreicher
 * A named HostList. Adds another convenience to associated hosts with scripts for a Run
 */
public class Role extends HostList{

    private String name;
    private RunConfig runConfig;

    protected Role(String name,RunConfig runConfig){
        super(runConfig);
        this.name = name;
        this.runConfig = runConfig;
    }

    public String getName(){return name;}

    @Override
    public String toString(){
        return name+" "+super.toString();
    }
}
