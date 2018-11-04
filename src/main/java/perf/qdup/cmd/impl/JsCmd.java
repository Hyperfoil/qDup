package perf.qdup.cmd.impl;

import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import perf.qdup.State;
import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.lang.invoke.MethodHandles;
import java.util.function.BiFunction;

/**
 * Use nashorn to support BiFunctions in javascript:
 * function(input,state){
 *     return true | false;
 * }
 * Returning true means invoke the next command, returning false means skip the next command. No return value assumes a return of true
 */
public class JsCmd extends Cmd {
    final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());


    private ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
    public static Object eval(String input){
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");

        Object rtrn = null;
        try {
            rtrn = engine.eval(input);
        } catch (ScriptException e) {
            logger.warn(JsCmd.class.getSimpleName()+": "+input+" threw "+e.getMessage(),e);
        }
        return rtrn;
    }

    private String codeString;
    private BiFunction<String,State,Object> function;

    public JsCmd(String code){
        this.codeString = code;
        //trap for ES6 when not running on an ES6 compatible jvm?
        //NOPE, too many cases to support
        try {
            engine.eval("var console = { log: print };");
            this.function = (BiFunction<String,State,Object>) engine.eval(
                    String.format("new java.util.function.BiFunction(%s)", codeString));
        } catch (ScriptException e) {
            e.printStackTrace();
        }

    }

    public String getCode(){
        return codeString;
    }

    @Override
    public void run(String input, Context context) {
        if(function!=null){
            try{
                Object rtrn = this.function.apply(input,context.getState());
                if( rtrn==null ||

                    (rtrn instanceof Boolean && !((Boolean)rtrn)) ||
                    (rtrn instanceof String && ((String)rtrn).toUpperCase().equals("FALSE"))
                ){
                    context.skip(input);
                }else if (ScriptObjectMirror.isUndefined(rtrn) || rtrn instanceof Boolean){
                    //TODO potentially log that the js function should have an explicit return value
                    context.next(input);
                }else {
                    context.next(rtrn.toString());
                }
            }catch (Exception e){
                //TODO log the failure
                context.skip(input);
            }
        }else{
            //TODO log that the function could not be run becuase it failed to create
            context.skip(input);
        }
    }

    @Override
    public Cmd copy() {
        return new JsCmd(this.codeString);
    }
}
