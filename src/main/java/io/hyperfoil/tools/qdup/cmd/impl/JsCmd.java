package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.PatternValuesMap;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.yaup.StringUtil;
import io.hyperfoil.tools.yaup.json.Json;
import io.hyperfoil.tools.yaup.json.graaljs.JsException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.IOException;
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
                PatternValuesMap map = new PatternValuesMap(this,context,new Ref(this));
                String populatedCodeString = Cmd.populateStateVariables(codeString,this,context);
                Object jsInput = input;
                if(Json.isJsonLike(input)){
                    jsInput = Json.fromString(input);
                }

                Object rtrn = null;
                try{
                    Object result = StringUtil.jsEval(populatedCodeString,jsInput,map);
                    rtrn = result;
                }catch( RuntimeException ise){
                    //todo; raise ISE
                    abort(ise.getMessage()+"\n"+ise.getCause().getMessage());
                }
                if (rtrn instanceof JsException){
                    JsException jsException = (JsException)rtrn;
                    abort(jsException.getMessage());
                }
                if( rtrn==null ||
                    (rtrn instanceof Boolean && !((Boolean)rtrn)) ||
                    (rtrn instanceof String && ((String)rtrn).toUpperCase().equals("FALSE"))
                ) {
                    context.skip(input);
                }else if (rtrn instanceof String && ((String)rtrn).isBlank()){
                    //if we think the function tried to return something
                    if(populatedCodeString.contains("return ") || (populatedCodeString.contains("=>") && !populatedCodeString.contains("=>{"))){
                        context.skip(input);
                    }else{
                        context.next(input);
                    }
                }else if ( rtrn instanceof Boolean){
                    //TODO potentially log that the js function should have an explicit return message
                    context.next(input);
                }else {
                    context.next(rtrn.toString());
                }
            }catch (Exception e){
                e.printStackTrace();
                //TODO log the failure
                context.skip(input);
            }
    }

    @Override
    public Cmd copy() {
        return new JsCmd(this.codeString);
    }
}
