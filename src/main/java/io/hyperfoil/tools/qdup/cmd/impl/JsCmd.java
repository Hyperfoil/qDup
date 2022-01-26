package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.CmdWithElse;
import io.hyperfoil.tools.qdup.cmd.PatternValuesMap;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.yaup.StringUtil;
import io.hyperfoil.tools.yaup.json.Json;
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
public class JsCmd extends CmdWithElse {
    final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

    private String codeString;
    private boolean ran = false;
    private Object rtrn = null;
    public JsCmd(String code){
        this.codeString = code;
    }

    public String getCode(){
        return codeString;
    }

    public boolean returnIsElse(Object rtrn){
        return rtrn == null ||
            (rtrn instanceof Boolean && !((Boolean)rtrn)) ||
            (rtrn instanceof String && ((String)rtrn).toUpperCase().trim().equals("FALSE")) ||
            (rtrn instanceof String && ((String)rtrn).isEmpty() && (codeString.contains("return ") || (codeString.contains("=>") && !codeString.contains("=>{"))));
    }

    @Override
    public void run(String input, Context context) {
            ran = true;
            try{
                PatternValuesMap map = new PatternValuesMap(this,context,new Ref(this));
                String populatedCodeString = Cmd.populateStateVariables(codeString,this,context);
                Object jsInput = input;
                if(Json.isJsonLike(input)){
                    jsInput = Json.fromString(input);
                }

                //Object rtrn = null;
                try{
                    Object result = StringUtil.jsEval(populatedCodeString,jsInput,map);
                    rtrn = result;
                }catch( RuntimeException ise){
                    //todo; raise ISE
                    abort(ise.getMessage()+"\n"+ise.getCause().getMessage());
                }
                if( rtrn==null ||
                    (rtrn instanceof Boolean && !((Boolean)rtrn)) ||
                    (rtrn instanceof String && ((String)rtrn).toUpperCase().trim().equals("FALSE"))
                ) {
                    if(hasElse()){
                        context.next(input);
                    } else {
                        context.skip(input);
                    }
                }else if (rtrn instanceof String && ((String)rtrn).isBlank()){
                    //if we think the function tried to return something
                    if(populatedCodeString.contains("return ") || (populatedCodeString.contains("=>") && !populatedCodeString.contains("=>{"))){
                        if(hasElse()){
                            context.next(input);
                        } else {
                            context.skip(input);
                        }
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
    public Cmd getNext(){
        if(ran && hasElse() && returnIsElse(rtrn)){
            return getElses().get(0);
        }else{
            return super.getNext();
        }
    }

    @Override
    public Cmd copy() {
        return new JsCmd(this.codeString);
    }

    @Override
    public String toString(){
        return "js: "+this.codeString;
    }
}
