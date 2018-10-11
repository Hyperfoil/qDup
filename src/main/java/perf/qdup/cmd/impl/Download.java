package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;
import perf.qdup.cmd.CommandResult;

import java.io.File;

public class Download extends Cmd {
    private String path;
    private String destination;
    public Download(String path, String destination){
        this.path = path;
        this.destination = destination;
    }
    public Download(String path){
        this(path,"");
    }
    public String getPath(){return path;}
    public String getDestination(){return destination;}
    @Override
    public void run(String input, Context context, CommandResult result) {

        String basePath = context.getRunOutputPath()+ File.separator+context.getSession().getHost().getHostName();
        String userName = context.getSession().getHost().getUserName();
        String hostName = context.getSession().getHost().getHostName();
        String remotePath = populateStateVariables(path,this,context.getState());
        String destinationPath =  populateStateVariables(basePath + File.separator +destination,this,context.getState());
        File destinationFile = new File(destinationPath);
        if(!destinationFile.exists()){
            destinationFile.mkdirs();
        }

        context.getLocal().download(remotePath,destinationPath,context.getSession().getHost());
        result.next(this,path);
    }

    @Override
    public Cmd copy() {
        return new Download(this.path,this.destination);
    }
    @Override
    public String toString(){return "download: "+path+" "+destination;}

}
