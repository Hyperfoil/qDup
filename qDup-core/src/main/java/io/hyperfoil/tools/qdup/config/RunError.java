package io.hyperfoil.tools.qdup.config;

import io.hyperfoil.tools.qdup.Stage;

public class RunError {
    private final String role;
    private final Stage stage;
    private final String script;
    private final String command;
    private final String message;

    public RunError(String role, Stage stage, String script, String command, String message) {
        this.role = role;
        this.stage = stage;
        this.script = script;
        this.command = command;
        this.message = message;
    }

    @Override
    public String toString(){
        return "Error: "+message+"\n  role: "+role+" stage: "+stage.getName()+" script: "+script+"\n  command: "+command;
    }

    public String getRole() {
        return role;
    }

    public Stage getStage(){return stage;}

    public String getScript() {
        return script;
    }

    public String getCommand() {
        return command;
    }

    public String getMessage() {
        return message;
    }
}
