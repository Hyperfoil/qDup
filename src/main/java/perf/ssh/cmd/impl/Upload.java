package perf.ssh.cmd.impl;

import perf.ssh.Local;
import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.CommandResult;
import perf.ssh.cmd.Context;

import java.io.File;

public class Upload extends Cmd {
    private String path;
    private String destination;
    public Upload(String path, String destination){
        this.path = path;
        this.destination = destination;
    }
    public Upload(String path){
        this(path,"");
    }
    public String getPath(){return path;}
    public String getDestination(){return destination;}
    @Override
    protected void run(String input, Context context, CommandResult result) {

        String basePath = context.getRunOutputPath()+ File.separator+context.getSession().getHostName();
        String userName = context.getSession().getUserName();
        String hostName = context.getSession().getHostName();
        String localPath = populateStateVariables(path,context.getState());
        String destinationPath =  populateStateVariables(basePath + File.separator +destination,context.getState());
        File destinationFile = new File(destinationPath);
        if(!destinationFile.exists()){
            destinationFile.mkdirs();
        }

        Local.get().upload(localPath,destinationPath,context.getSession().getHost());
        result.next(this,path);
    }

    @Override
    protected Cmd clone() {
        return new Upload(this.path,this.destination);
    }
    @Override
    public String toString(){return "upload "+path+" "+destination;}

}
