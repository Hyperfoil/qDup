package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;

import java.io.File;

public class QueueDownload extends Cmd {
    private String path;
    private String populatedPath;
    private String destination;
    private String populatedDestination;
    public QueueDownload(String path, String destination){
        this.path = path;
        this.destination = destination;
        if(this.destination==null){
            this.destination="";
        }
    }
    public QueueDownload(String path){
        this(path,"");
    }
    public String getPath(){return path;}
    public String getDestination(){return destination;}


    @Override
    public String toString(){return "queue-download: " + path + (destination.isEmpty()?"":(" -> "+destination));}

    @Override
    public void run(String input, Context context) {
        String basePath = context.getRunOutputPath()+ File.separator+context.getSession().getHost().getHostName();
        String resolvedPath = Cmd.populateStateVariables(getPath(),this,context.getState());
        String resolvedDestination = Cmd.populateStateVariables(basePath + File.separator + getDestination(),this,context.getState());


        if(resolvedPath.matches("[^\\$]*\\$(?!\\{\\{).*")){//if the source path has $name or ${name}
            resolvedPath = context.getSession().execSync("echo "+resolvedPath);
        }
        if(!resolvedPath.startsWith("/")){//relative path
            //TODO can download paths be relative? probably best if no
        }
        if(resolvedDestination.matches("[^\\$]*\\$(?!\\{\\{).*")){//if the destination path has $name or ${name}
            resolvedDestination = context.getSession().execSync("echo "+resolvedDestination);
        }

        populatedPath = resolvedPath;
        populatedDestination = resolvedDestination;
        context.addPendingDownload(resolvedPath,resolvedDestination);

        File destinationFile = new File(resolvedDestination);
        if(!destinationFile.exists()){
            destinationFile.mkdirs();
        }
        context.next(input);
    }

    @Override
    public Cmd copy() {
        return new QueueDownload(path,destination);
    }

    @Override
    public String getLogOutput(String output,Context context){
        if(populatedPath!=null){
            return "queue-download: "+populatedPath+" "+populatedDestination;
        }else{
            return "queue-download: "+path+" "+destination;
        }
    }
}
