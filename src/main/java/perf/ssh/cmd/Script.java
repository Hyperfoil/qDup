package perf.ssh.cmd;

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
    protected void run(String input, CommandContext context, CommandResult result) {
        result.next(this, input);

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
        return new Script(this.name);
    }

    @Override
    public String toString() { return this.name; }
}
