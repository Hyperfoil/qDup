package perf.ssh.cmd;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by wreicher
 * The starting Cmd for a sequence of Cmd that will run against a remote SshSession
 */
public class Script extends Cmd {
    private String name;

    private Map<String,String> with;

    public Script(String name) {

        this.name = name;
        this.with = new HashMap<>();
    }

    public String getName() {
        return name;
    }

    @Override
    protected void run(String input, CommandContext context, CommandResult result) {
        for(String key : with.keySet()){
            context.getState().set(key,with.get(key));
        }
        result.next(this, input);

    }

    private Script with(Map<String,String> withs){
        this.with.putAll(withs);
        return this;
    }
    public Script with(String key,String value){
        with.put(key,value);
        return this;
    }

    public Script then(Cmd command) {
        super.then(command);
        return this;
    }

    public Script watch(Cmd command) {
        super.watch(command);
        return this;
    }

    @Override
    protected Cmd clone() {
        return new Script(this.name).with(with);
    }

    @Override
    public String toString() { return this.name; }
}
