package perf.ssh.cmd.impl;

import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.Context;
import perf.ssh.cmd.CommandResult;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

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
    protected void run(String input, Context context, CommandResult result) {
        String basePath = context.getRunOutputPath()+ File.separator+context.getSession().getHostName();
        String resolvedPath = Cmd.populateStateVariables(getPath(),this,context.getState());
        String resolvedDestination = Cmd.populateStateVariables(basePath + File.separator + getDestination(),this,context.getState());

        if(resolvedPath.contains(ENV_PREFIX)){
            //we need to fetch the variables from the remote machine
            Matcher m = ENV_PATTERN.matcher(resolvedPath);
            Map<String,String> replacements = new HashMap<>();
            while(m.find()){
                String name = m.group("name");
                context.getSession().sh("echo ${"+name+"}");
                String output = context.getSession().getOutput().trim();
                replacements.put(name,output);
            }
            for(String key : replacements.keySet()){
                resolvedPath= resolvedPath.replaceAll("\\$\\{"+key+"}",replacements.get(key));
            }
        }
        if(resolvedDestination.contains(ENV_PREFIX)){
            //we need to fetch the variables from the remote machine
            Matcher m = ENV_PATTERN.matcher(resolvedDestination);
            Map<String,String> replacements = new HashMap<>();
            while(m.find()){
                String name = m.group("name");
                context.getSession().sh("echo ${"+name+"}");
                String output = context.getSession().getOutput().trim();
                replacements.put(name,output);
            }
            for(String key : replacements.keySet()){
                resolvedDestination= resolvedDestination.replaceAll("\\$\\{"+key+"}",replacements.get(key));
            }
        }


        context.addPendingDownload(resolvedPath,resolvedDestination);

        File destinationFile = new File(resolvedDestination);
        if(!destinationFile.exists()){
            destinationFile.mkdirs();
        }
        result.next(this,input);

    }

    @Override
    protected Cmd clone() {
        return new QueueDownload(path,destination).with(this.with);
    }
}
