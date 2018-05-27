package perf.qdup.cmd;

import java.util.ArrayList;
import java.util.List;

public class SpyCommandResult implements CommandResult {

    List<String> updates;
    String next;
    String skip;

    public SpyCommandResult(){
        updates = new ArrayList<>();
        next = null;
        skip = null;
    }


    @Override
    public void next(Cmd command, String output) {
        next = output;
    }

    @Override
    public void skip(Cmd command, String output) {
        skip = output;
    }

    @Override
    public void update(Cmd command, String output) {
        updates.add(output);
    }

    public String getNext(){return next;}
    public boolean hasNext(){return next!=null;}
    public String getSkip(){return skip;}
    public boolean hasSkip(){return skip!=null;}
}
