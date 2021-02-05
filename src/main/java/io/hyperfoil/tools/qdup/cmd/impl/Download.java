package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;

import java.io.File;

public class Download extends Cmd {
    private String path;
    private String destination;
    private Long maxSize;

    public Download(String path, String destination, Long maxSize ){
        this.path = path;
        this.destination = destination;
        if(maxSize < 0) {
            this.maxSize = null;
        } else {
            this.maxSize = maxSize;
        }
    }
    public Download(String path, String destination ){
        this(path,destination, null);
    }
    public Download(String path){
        this(path,"", null);
    }
    public Download(String path, Long maxSize){
        this(path,"", maxSize);
    }
    public String getPath(){return path;}
    public String getDestination(){return destination;}
    @Override
    public void run(String input, Context context) {

        String basePath = context.getRunOutputPath()+ File.separator+context.getSession().getHost().getHostName();
        String userName = context.getSession().getHost().getUserName();
        String hostName = context.getSession().getHost().getHostName();
        String remotePath = populateStateVariables(path,this,context);
        String destinationPath =  populateStateVariables(basePath + File.separator +destination,this,context);
        File destinationFile = new File(destinationPath);
        if(!destinationFile.exists()){
            destinationFile.mkdirs();
        }

        boolean canDownload = true;

        if(maxSize != null){
            Long remoteFileSize = context.getLocal().remoteFileSize(remotePath,context.getSession().getHost());
            if(remoteFileSize > maxSize){
                canDownload = false;
                logger.warn("File: {} is {} bytes, which is larger than max size: {}", remotePath, remoteFileSize, maxSize);
            }
        }
        if(canDownload) {
            context.getLocal().download(remotePath, destinationPath, context.getSession().getHost());
        }
        context.next(path);
    }

    @Override
    public Cmd copy() {
        return new Download(this.path,this.destination, this.maxSize);
    }
    @Override
    public String toString(){return "download: "+path+" "+destination;}

}
