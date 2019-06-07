package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Code;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.qdup.cmd.Result;

import java.lang.reflect.InvocationTargetException;

public class CodeCmd extends Cmd {

    private Code code;
    private String className;
    public CodeCmd(Code code){
        this.code = code;
    }
    public CodeCmd(String className){
        this.className = className;
    }
    public Code getCode(){return code;}
    public String getClassName(){return className;}
    @Override
    public void run(String input, Context context) {
        Result codeResult = Result.skip(input);
        if(className!=null){
            try {
                Object instance = Class.forName(className).getConstructors()[0].newInstance();
                if(instance instanceof Code){
                    //TODO need a State subclass that uses the with before invoking state
                    codeResult = ((Code)instance).run(input, new State.CmdState( context.getState(), this ) );
                }
            } catch (InstantiationException|IllegalAccessException|ClassNotFoundException| InvocationTargetException e) {
                logger.error("Failed to load "+className+": {}",e.getMessage(),e);
            }
        }else if (code != null) {
            codeResult = code.run(input, new State.CmdState( context.getState(), this ) );

        }
        switch (codeResult.getType()) {
            case skip:
                context.skip(codeResult.getResult());
                break;
            default:
                context.next(codeResult.getResult());
        }
    }
    @Override
    public Cmd copy() {
        return new CodeCmd(code);
    }
    @Override
    public String toString(){return "code: "+( className==null? code.toString() : className );}
}
