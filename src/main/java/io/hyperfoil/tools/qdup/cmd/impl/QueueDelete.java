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
