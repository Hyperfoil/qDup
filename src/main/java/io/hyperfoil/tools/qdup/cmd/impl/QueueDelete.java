package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;

public class QueueDelete extends Cmd {

    private String path;
    private String populatedPath;

    public QueueDelete(String path){
        this.path = path;
    }

    @Override
    public void run(String input, Context context) {
        String resolvedPath = Cmd.populateStateVariables(getPath(),this,context);
        if(resolvedPath.matches("[^\\$]*\\$(?!\\{\\{).*")){//if the source path has $name or ${name}
            resolvedPath = context.getSession().shSync("echo "+resolvedPath);
        }
        if(!resolvedPath.startsWith("/")){//relative path
            //TODO can delete paths be relative? probably best if no
        }
        populatedPath = resolvedPath;
        context.addPendingDelete(populatedPath);
    }

    private String getPath() {
        return path;
    }

    @Override
    public Cmd copy() {
        return new QueueDelete(path);
    }


    @Override
    public String toString(){return "queue-delete: "+path;}

    @Override
    public String getLogOutput(String output,Context context){
        if(populatedPath!=null){
            return "queue-delete: "+populatedPath;
        }else{
            return "queue-delete: "+path;
        }

    }

}
