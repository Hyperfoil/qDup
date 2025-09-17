package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.CmdWithElse;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.yaup.AsciiArt;
import io.hyperfoil.tools.yaup.json.Json;

public class JsonCmd extends CmdWithElse {

    private String path;

    public JsonCmd(String path){
        this.path = path;
    }

    public String getPath(){return path;}

    @Override
    public void run(String input, Context context) {
        if(!Json.isJsonLike(input)){
            context.skip(input);
        }else{
            Json inputJson = Json.fromString(input);
            if(inputJson == null || inputJson.isEmpty()){
                context.skip(input);
            }else{
                String populatedPath = Cmd.populateStateVariables(path,this,context);
                if(Cmd.hasStateReference(populatedPath,this)){
                    context.error("failed to populate json path: "+populatedPath);
                    context.abort(false);
                }
                Object found = Json.find(inputJson,populatedPath);
                if(found == null){
                    context.skip(input);
                }else{
                    context.next(found.toString());
                }
            }
        }
    }

    @Override
    public Cmd copy() {
        return new JsonCmd(path);
    }

    @Override
    public String toString() {
        return "json: "+ getPath();
    }
}
