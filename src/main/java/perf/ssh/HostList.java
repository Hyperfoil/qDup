package perf.ssh;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perf.ssh.cmd.Script;
import perf.util.HashedList;

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Created by wreicher
 * A list of hosts that simplify adding scripts to multiple hosts.
 * The HostList has an associated Run and will update the run whenever a script is associated with the list.
 */
public class HostList {

    final static Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private HashedList<Host> hosts;
    private RunConfig runConfig;
    public HostList(RunConfig runConfig){
        this(Collections.emptyList(),runConfig);
    }
    public HostList(List<Host> hosts,RunConfig runConfig){
        this.hosts = new HashedList<>(hosts);
        this.runConfig = runConfig;
    }
    private HostList(HashedList<Host> hosts,RunConfig runConfig){
        this.hosts = hosts;
        this.runConfig = runConfig;
    }
    public HostList add(Host...hosts){
        for(Host host : hosts){
            this.hosts.add(host);
            this.runConfig.addHost(host);
        }
        return this;
    }
    public HostList addAll(HostList...otherLists){
        for(HostList hl : otherLists){
            hosts.addAll(hl.toList());
            runConfig.addAllHosts(hl.toList());
        }
        return this;
    }
    public HostList filter(Predicate<Host> predicate){
        HashedList<Host> newList = new HashedList<>();
        for(Host h : hosts.toList()){
            if(predicate.test(h)){
                newList.add(h);
            }
        }
        return new HostList(newList,runConfig);
    }
    public HostList reverseFilter(HostList otherList){
        HashedList<Host> uniqueList = new HashedList<>(this.hosts.toList());
        HashedList<Host> newList = new HashedList<>(this.hosts.toList());
        uniqueList.removeAll(otherList.toList());
        newList.removeAll(uniqueList.toList());
        return new HostList(newList,runConfig);
    }
    public HostList filter(HostList otherList){

        HashedList<Host> newList = new HashedList<>(this.hosts.toList());
        newList.removeAll(otherList.toList());
        return new HostList(newList,this.runConfig);
    }
    public HostList removeAll(HostList...otherLists){
        for(HostList hl : otherLists){
            hosts.removeAll(hl.toList());
        }
        return this;
    }
    public List<Host> toList(){
        return hosts.toList();
    }

    public void addRunScript(Script script){
        for(Host host : hosts.toList()){
            runConfig.addRunScript(host,script);
        }
    }
    public void addSetupScript(Script script){
        for(Host host : hosts.toList()){
            runConfig.addSetupScript(host,script);
        }
    }
    public void addCleanupScript(Script script){
        for(Host host : hosts.toList()){
            runConfig.addCleanupScript(host,script);
        }
    }
    public boolean hasHost(Host host){
        return hosts.contains(host);
    }
    public int size(){return hosts.size();}

    @Override
    public String toString(){
        return hosts.toList().toString();
    }
}
