package perf.ssh.cmd.impl;

import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.CommandResult;
import perf.ssh.cmd.Context;

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

        String localPath = populateStateVariables(path,this, context.getState());
        String destinationPath =  populateStateVariables(destination ,this, context.getState());

        //create remote directory
        context.getSession().sh( "mkdir -p " + destinationPath );
        
        context.getLocal().upload(
            localPath,
            destinationPath,
            context.getSession().getHost()
        );

        result.next(this,path);
    }

    @Override
    protected Cmd clone() {
        return new Upload(this.path,this.destination).with(this.with);
    }
    @Override
    public String toString(){return "upload "+path+" "+destination;}

}
