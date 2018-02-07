package perf.ssh.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perf.util.HashedList;

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Created by wreicher
 * A list of host names and script names
 */
public class HostList {


    final static Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private HashedList<String> hosts;

    private List<String> setupScripts;
    private List<String> runScripts;
    private List<String> cleanupScripts;

    public HostList(){
        this(Collections.emptyList());
    }
    public HostList(List<String> hosts){
        this.hosts = new HashedList<>(hosts);

        this.setupScripts = new LinkedList<>();
        this.runScripts = new LinkedList<>();
        this.cleanupScripts = new LinkedList<>();

    }
    private HostList(HashedList<String> hosts){
        this.hosts = hosts;
    }
    public HostList add(String...hosts){
        for(String host : hosts){
            this.hosts.add(host);
        }
        return this;
    }
    public HostList addAll(HostList...otherLists){
        for(HostList hl : otherLists){
            hosts.addAll(hl.toList());
        }
        return this;
    }
    public HostList filter(Predicate<String> predicate){
        HashedList<String> newList = new HashedList<>();
        for(String h : hosts.toList()){
            if(predicate.test(h)){
                newList.add(h);
            }
        }
        return new HostList(newList);
    }
    public HostList common(HostList otherList){
        HashedList<String> uniqueList = new HashedList<>(this.hosts.toList());
        HashedList<String> newList = new HashedList<>(this.hosts.toList());
        uniqueList.removeAll(otherList.toList());
        newList.removeAll(uniqueList.toList());
        return new HostList(newList);
    }
    public HostList filter(HostList otherList){
        HashedList<String> newList = new HashedList<>(this.hosts.toList());
        newList.removeAll(otherList.toList());
        return new HostList(newList);
    }
    public HostList removeAll(HostList...otherLists){
        for(HostList hl : otherLists){
            hosts.removeAll(hl.toList());
        }
        return this;
    }
    public List<String> toList(){
        return hosts.toList();
    }

    public List<String> getRunScripts(){
        return Collections.unmodifiableList(runScripts);
    }
    public void addRunScript(String script){
        runScripts.add(script);
    }
    public List<String> getSetupScripts(){
        return Collections.unmodifiableList(setupScripts);
    }
    public void addSetupScript(String script){
        setupScripts.add(script);
    }
    public List<String> getCleanupScripts(){
        return Collections.unmodifiableList(cleanupScripts);
    }
    public void addCleanupScript(String script){
        cleanupScripts.add(script);
    }

    public boolean matches(String host){
        return isEmpty() || hosts.contains(host);
    }
    public int size(){return hosts.size();}
    public boolean isEmpty(){return size()==0;}

    @Override
    public String toString(){
        return hosts.toList().toString();
    }
}
