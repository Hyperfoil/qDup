package perf.ssh.cmd.impl;

import perf.ssh.cmd.*;

public class CodeCmd extends Cmd {
    private Code code;
    public CodeCmd(Code code){
        this.code = code;
    }
    public Code getCode(){return code;}
    @Override
    protected void run(String input, CommandContext context, CommandResult result) {
        Result codeResult = code.run(input,context.getState());
        switch(codeResult.getType()){
            case skip:
                result.skip(this,codeResult.getResult());
                break;
            default:
                result.next(this,codeResult.getResult());
        }
    }
    @Override
    protected Cmd clone() {
        return new CodeCmd(code);
    }
    @Override
    public String toString(){return "code "+code.toString();}
}
