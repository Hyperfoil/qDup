package perf.ssh;

/**
 * Created by wreicher
 * A named HostList. Adds another convenience to associated hosts with scripts for a Run
 */
public class Role extends HostList{

    private String name;
    private Run run;

    protected Role(String name,Run run){
        super(run);
        this.name = name;
        this.run = run;
    }

    public String getName(){return name;}
}
