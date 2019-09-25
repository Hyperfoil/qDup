package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;

/**
 * NOTE: I don't think this should be a command, it breaks the idea that qdup is just shell scripts
 * and moves closer to having ansible like encapsulation of shell functionality
 *
 * Check the previous exit code (echo $?) and invoke the child commands if the exit code matches the expected code.
 * The default expected is 0.
 * This works by saving the exit code of the previous shell command
 *
 */
public class ExitCode extends Cmd {

    private static final String EXIT_CODE_KEY = "qdupExitCode";
    private static final String DEFAULT_EXIT_CODE = "-1";

    private String expected;

    public ExitCode(String expected){
        if(expected.isEmpty()){
            expected="0";
        }
        this.expected = expected;
    }
    public String getExpected(){return expected;}

    @Override
    public void run(String input, Context context) {
         if(getPrevious()==null){
             //cannot get exit code if there wasn't a previous command
             context.skip(input);
         }else {//assuming there was a previous Sh at some point
             if(!getPrevious().getWith().has(EXIT_CODE_KEY)){
                 if(context!=null && context.getSession()!=null){
                     String response = context.getSession().shSync("echo $?");
                     getPrevious().with(EXIT_CODE_KEY,response);
                 }else{
                     //no valid session to get the previous exit code, use the default
                     getPrevious().with(EXIT_CODE_KEY,DEFAULT_EXIT_CODE);
                 }
             }
             String exitCode = getPrevious().getWith().get(EXIT_CODE_KEY).toString();
             with(EXIT_CODE_KEY,exitCode);

             if(expected.equals(exitCode)){
                 context.next(input);
             }else{
                 context.skip(input);
             }
         }
    }

    @Override
    public Cmd copy() {
        return new ExitCode(this.expected);
    }
    @Override
    public String toString(){
        return "exit-code: "+this.expected;
    }
}
