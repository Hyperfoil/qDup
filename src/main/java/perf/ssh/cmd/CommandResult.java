package perf.ssh.cmd;

/**
 * Created by wreicher
 * Interface for Cmds to send updates and signal the end of execution for the Cmd.
 */
//TODO change the interface to no longer need Cmd?
//Cmd is always this from a Cmd so change to class with a mutable currentCmd?
public interface CommandResult {

    public void next(Cmd command,String output);
    public void skip(Cmd command,String output);
    public void update(Cmd command,String output);

}
