package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;

import java.io.File;
import java.nio.file.Paths;

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

    /**
     * returns true of the the input contains ${ or $ without ${{ or $( or `
     * @param input
     * @return
     */
    public static boolean hasBashEnv(String input){
        return input.matches("[^\\$]*\\$(?!\\{\\{).*") || input.contains("$(") || input.contains("`");
    }

    @Override
    public void run(String input, Context context) {
        String basePath = context.getRunOutputPath()+ File.separator+context.getSession().getHost().getHostName();
        String resolvedPath = Cmd.populateStateVariables(getPath(),this,context);
        String resolvedDestination = Cmd.populateStateVariables(basePath + File.separator + getDestination(),this,context);


        if(hasBashEnv(resolvedPath)){//if the source path has $name or ${name}
            resolvedPath = context.getSession().shSync("echo "+resolvedPath);
        }
        if(resolvedPath.startsWith("~/")){
            String homeDir = context.getSession().shSync("echo ~/");
            resolvedPath = homeDir+resolvedPath.substring("~/".length());
        }else if(!resolvedPath.startsWith("/")){//relative path
            //TODO can download paths be relative? probably best if no
            String pwd = context.getSession().shSync("pwd");
            if(resolvedPath.startsWith("./")){
                resolvedPath = resolvedPath.substring("./".length());
            }
            resolvedPath = Paths.get(pwd,resolvedPath).toString();
        }
        if(hasBashEnv(resolvedDestination)){//if the destination path has $name or ${name}
            resolvedDestination = context.getSession().shSync("echo "+resolvedDestination);
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
