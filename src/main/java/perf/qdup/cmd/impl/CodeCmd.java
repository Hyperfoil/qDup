package perf.qdup.cmd.impl;

import perf.qdup.State;
import perf.qdup.cmd.*;

public class CodeCmd extends Cmd {

    private class WithState extends State {
        public WithState(State parent) {
            super(parent, "");//empty previs to make parent read only
            set(getWith());
        }
    }

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
    public void run(String input, Context context, CommandResult result) {
        Result codeResult = Result.skip(input);
        if(className!=null){
            try {
                Object instance = Class.forName(className).newInstance();
                if(instance instanceof Code){
                    //TODO need a State subclass that uses the with before invoking state
                    codeResult = ((Code)instance).run(input, new WithState( context.getState() ) );
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
