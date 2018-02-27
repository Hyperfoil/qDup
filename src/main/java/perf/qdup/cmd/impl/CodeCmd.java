package perf.qdup.cmd.impl;

import perf.qdup.cmd.*;

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
    protected void run(String input, Context context, CommandResult result) {
        Result codeResult = Result.skip(input);
        if(className!=null){
            try {
                Object instance = Class.forName(className).newInstance();
                if(instance instanceof Code){
                    codeResult = ((Code)instance).run(input, context.getState());
                }

            } catch (InstantiationException|IllegalAccessException|ClassNotFoundException e) {
                logger.error("Failed to load "+className+": {}",e.getMessage(),e);
            }
        }else if (code != null) {
            codeResult = code.run(input, context.getState());

        }
        switch (codeResult.getType()) {
            case skip:
                result.skip(this, codeResult.getResult());
                break;
            default:
                result.next(this, codeResult.getResult());
        }
    }
    @Override
    protected Cmd clone() {
        return new CodeCmd(code).with(this.with);
    }
    @Override
    public String toString(){return "code: "+( className==null? code.toString() : className );}
}
