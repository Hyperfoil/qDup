package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.qdup.shell.AbstractShell;

public class RemovePrompt extends Cmd {


    private String prompt;
    private String populatedPrompt = null;


    public RemovePrompt(String prompt){
        this.prompt = prompt;
    }

    @Override
    public void run(String input, Context context) {
        populatedPrompt = Cmd.populateStateVariables(prompt,this,context);
        if(AbstractShell.PROMPT.equals(populatedPrompt) || (context.getHost().hasPrompt() && context.getHost().getPrompt().equals(populatedPrompt))){
            //cannot remove the prompt defined for the host or by qdup
            
        }else{
            context.getShell().removePrompt(populatedPrompt);
        }


        context.next(input);
    }
    public String getPrompt(){return prompt;}

    @Override
    public Cmd copy() {
        return new RemovePrompt(prompt);
    }
}
