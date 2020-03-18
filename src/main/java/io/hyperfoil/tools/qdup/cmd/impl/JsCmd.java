package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.yaup.StringUtil;
import org.graalvm.polyglot.Value;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.lang.invoke.MethodHandles;

/**
 * Use graaljs to support BiFunctions in javascript:
 * function(input,state){
 *     return true | false;
 * }
 * Returning true means invoke the next command,
 * Returning false means skip the next command.
 * No return message assumes a return of true
 */
public class JsCmd extends Cmd {
    final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

    private String codeString;

    public JsCmd(String code){
        this.codeString = code;
    }

    public String getCode(){
        return codeString;
    }

    @Override
    public void run(String input, Context context) {
            try{
                Object rtrn = StringUtil.jsEval(codeString,input,context.getState());
                if( rtrn==null ||
                    (rtrn instanceof Boolean && !((Boolean)rtrn)) ||
                    (rtrn instanceof String && ((String)rtrn).toUpperCase().equals("FALSE"))
                ){
                    context.skip(input);
                }else if (rtrn == null || (rtrn instanceof String && ((String)rtrn).isBlank()) || rtrn instanceof Boolean){
                    //TODO potentially log that the js function should have an explicit return message
                    context.next(input);
                }else {
                    context.next(rtrn.toString());
                }
            }catch (Exception e){
                //TODO log the failure
                context.skip(input);
            }
    }

    @Override
    public Cmd copy() {
        return new JsCmd(this.codeString);
    }
}
