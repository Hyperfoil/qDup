package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.yaup.AsciiArt;

public class Abort extends Cmd {
    private String message;
    private String populatedMessage;
    private Boolean skipCleanup;

    public Abort(String message) {
        this(message, false);
    }
    public Abort(String message, Boolean skipCleanup){
        this.message = message;
        this.skipCleanup = skipCleanup;
    }

    @Override
    public void run(String input, Context context) {
        populatedMessage = Cmd.populateStateVariables(message,this,context);
        context.terminal(
            String.format("%sAbort! %s%s",
                context.isColorTerminal()?AsciiArt.ANSI_RED:"",
                populatedMessage,
                context.isColorTerminal()?AsciiArt.ANSI_RESET:""
            )
        );
        context.abort(this.skipCleanup);
        //context.next(input); calling this would execute any commands after abort, which is wrong :)
        //result.next(this,input);
    }

    public String getMessage(){return message;}

    public Boolean getSkipCleanup() {
        return skipCleanup;
    }

    @Override
    public Cmd copy() {
        return new Abort(this.message, this.skipCleanup);
    }

    @Override
    public String toString(){return "abort: "+this.message;}

    @Override
    public String getLogOutput(String output,Context context){

        String touse = populatedMessage!=null ? populatedMessage : message;
        return
            (context.isColorTerminal() ? AsciiArt.ANSI_RED : "")+
            "abort: "+touse+
            (context.isColorTerminal() ? AsciiArt.ANSI_RESET : "");
    }
}
