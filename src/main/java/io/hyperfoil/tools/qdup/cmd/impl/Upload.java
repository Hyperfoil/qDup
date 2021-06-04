package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;

public class Upload extends Cmd {
    private String path;
    private String destination;
    String populatedPath;
    String populatedDestination;
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

        populatedPath = populateStateVariables(path,this, context);
        populatedDestination =  populateStateVariables(destination ,this, context);

        //create remote directory
        if(populatedDestination.endsWith("/")) {
            context.getSession().sh("mkdir -p " + populatedDestination);
        }
        
        boolean worked = context.getLocal().upload(
            populatedPath,
            populatedDestination,
            context.getSession().getHost()
        );
        if(!worked){
            context.error("failed to upload "+populatedPath+" to "+populatedDestination);
            context.abort(false);
        }
        context.next(path);
    }

    @Override
    public Cmd copy() {
        return new Upload(this.path,this.destination);
    }
    @Override
    public String toString(){return "upload: "+path+" "+destination;}
    @Override
    public String getLogOutput(String output,Context context){
        String usePath = populatedPath != null ? populatedPath : path;
        String useDestination = populatedDestination != null ? populatedDestination : destination;
        return "upload: "+usePath+" "+useDestination;
    }

}
