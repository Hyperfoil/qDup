package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;

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
    public void run(String input, Context context) {

        String localPath = populateStateVariables(path,this, context.getState());
        String destinationPath =  populateStateVariables(destination ,this, context.getState());

        //create remote directory
        if(destinationPath.endsWith("/")) {
            context.getSession().sh("mkdir -p " + destinationPath);
        }
        
        context.getLocal().upload(
            localPath,
            destinationPath,
            context.getSession().getHost()
        );
        context.next(path);
    }

    @Override
    public Cmd copy() {
        return new Upload(this.path,this.destination);
    }
    @Override
    public String toString(){return "upload: "+path+" "+destination;}

}
