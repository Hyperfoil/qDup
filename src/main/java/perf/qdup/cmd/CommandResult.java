package perf.qdup.cmd;

/**
 * Created by wreicher
 * Interface for Cmds to send updates and signal the end of execution for the Cmd.
 */
public interface CommandResult {



    public void next(Cmd command,String output);
    public void skip(Cmd command,String output);
    public void update(Cmd command,String output);

}
