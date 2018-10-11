package perf.qdup.cmd;

import perf.qdup.cmd.impl.Sh;
import perf.yaup.HashedLists;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;


//doesn't work for prompts because those are not sent to accept without a newline
public class ShErrorObserver implements CommandDispatcher.CommandObserver {

    public static ShErrorObserver getObserver(Context context) {
        ShErrorObserver rtrn = new ShErrorObserver(context);

        rtrn.addGlobalError("-bash:: Permission denied");
        rtrn.addGlobalError("command not found...");
        rtrn.addCommandError("unzip","? [y]es, [n]o, [A]ll, [N]one, [r]ename:");
        rtrn.addCommandError("ant","BUILD FAILURE");
        rtrn.addCommandError("ant","Build failed");
        rtrn.addCommandError("grep", "grep: Invalid regular expression");
        rtrn.addCommandError("cd", "No such file or directory");
        rtrn.addCommandError("tail", "tail: no files remaining");
        rtrn.addCommandError("lsof","lsof: illegal process ID:");
        rtrn.addCommandError("lsof","lsof: no process ID specified");

        return rtrn;
    }

    Context context;
    List<String> globalErrors;
    HashedLists<String, String> commandErrors;

    public ShErrorObserver(Context context) {
        this.context = context;

        this.globalErrors = new LinkedList<>();
        this.commandErrors = new HashedLists<>();
    }

    public void addGlobalError(String error) {
        globalErrors.add(error);
    }

    public void addCommandError(String command, String error) {
        commandErrors.put(command, error);
    }
    public static List<String> getCommandNames(String command){
        List<String> commandNames = Arrays.asList(command.split("\\|")).stream().map(s->{
            return s.contains(" ") ? s.substring(0,s.indexOf(" ")) : s;
        }).collect(Collectors.toList());
        return commandNames;
    }
    public void checkErrors(Cmd command,String output){
        if (command instanceof Sh) {
            Sh shCommand = (Sh) command;
            String fullCommandString = shCommand.getCommand();
            List<String> commandNames = getCommandNames(fullCommandString);
            List<String> errors = new LinkedList<>();
            errors.addAll(globalErrors);
            commandNames.forEach(commandName->errors.addAll(commandErrors.get(commandName)));
            errors.forEach(error -> {
                if (output.contains(error)) {
                    context.getRunLogger().error("{}\n{}", command, error);
                    context.abort();
                }
            });
        }
    }
    @Override
    public void onStop(Cmd command) {
        if (command instanceof Sh) {
            Sh shCommand = (Sh) command;
            String output = shCommand.getOutput();
            checkErrors(command,output);
        }
    }

    @Override
    public void onUpdate(Cmd command, String output) {
        checkErrors(command,output);

    }
}