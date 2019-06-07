package io.hyperfoil.tools.qdup.cmd;

/**
 * Created by wreicher
 * The starting Cmd for a sequence of Cmd that will run against a remote SshSession
 */
public class Script extends Cmd {
    private String name;

    public Script(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public void run(String input, Context context) {
        context.next(input);
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
    public Cmd copy() {
        return new Script(this.name);
    }

    @Override
    public String toString() { return this.name; }
}
