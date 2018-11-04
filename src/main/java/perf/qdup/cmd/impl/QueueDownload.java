package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;

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
    public String toString(){return "queue-download: " + path + (destination.isEmpty()?"":(" -> "+destination));}

    @Override
    public void run(String input, Context context) {
        String basePath = context.getRunOutputPath()+ File.separator+context.getSession().getHost().getHostName();
        String resolvedPath = Cmd.populateStateVariables(getPath(),this,context.getState());
        String resolvedDestination = Cmd.populateStateVariables(basePath + File.separator + getDestination(),this,context.getState());

        if(resolvedPath.matches("[^\\$]*\\$(?!\\{\\{).*")){
            resolvedPath = context.getSession().shSync("echo "+resolvedPath);
        }
        if(resolvedDestination.matches("[^\\$]*\\$(?!\\{\\{).*")){
            resolvedDestination = context.getSession().shSync("echo "+resolvedDestination);
        }

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
}
