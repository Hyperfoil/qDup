package io.hyperfoil.tools.qdup.config.rule;

import io.hyperfoil.tools.qdup.Stage;
import io.hyperfoil.tools.qdup.cmd.Cmd;

class RSSCRef {
    private final String role;
    private final Stage stage;
    private final String script;
    private final Cmd command;

    RSSCRef(String role, Stage stage, String script, Cmd command) {
        this.role = role;
        this.stage = stage;
        this.script = script;
        this.command = command;
    }

    public boolean isSameScript(RSSCRef ref){
        return
            getStage().equals(ref.getStage()) &&
            getRole().equals(ref.getRole()) &&
            getScript().equals(ref.getScript());
    }
    public boolean isBeforeOrSequentiallyWith(RSSCRef ref){
        return stage.isBefore(ref.getStage()) ||
                (stage.isSequential() && stage == ref.getStage() && getRole().equals(ref.getRole()));
    }


    public String getRole() {
        return role;
    }

    public Stage getStage() {
        return stage;
    }

    public String getScript() {
        return script;
    }

    public Cmd getCommand() {
        return command;
    }


    @Override
    public String toString(){
        return getRole()+":"+getStage().getName()+":"+getScript()+":"+getCommand();
    }
}
