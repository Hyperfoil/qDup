package perf.ssh.cmd.impl;

import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.Context;
import perf.ssh.cmd.CommandResult;

import java.io.File;

public class QueueDownload extends Cmd {
    private String path;
    private String destination;
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
    public String toString(){return "queueDownload " + path + (destination.isEmpty()?"":(" -> "+destination));}

    @Override
    protected void run(String input, Context context, CommandResult result) {
        String basePath = context.getRunOutputPath()+ File.separator+context.getSession().getHostName();
        String resolvedPath = Cmd.populateStateVariables(getPath(),context.getState());
        String resolvedDestination = Cmd.populateStateVariables(basePath + File.separator + getDestination(),context.getState());

        context.addPendingDownload(resolvedPath,resolvedDestination);

        File destinationFile = new File(resolvedDestination);
        if(!destinationFile.exists()){
            destinationFile.mkdirs();
        }
        result.next(this,input);

    }

    @Override
    protected Cmd clone() {
        return new QueueDownload(path,destination);
    }
}
