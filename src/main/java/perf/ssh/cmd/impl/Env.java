package perf.ssh.cmd.impl;

import perf.ssh.State;
import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.CommandResult;
import perf.ssh.cmd.Context;
import perf.ssh.cmd.Result;
import perf.yaup.Sets;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class Env extends Sh {

    private static final Set<String> FILTER = Sets.of("OLDPWD","PWD");

    final private Map<String,String> environment;

    public Env(){
        super("env",true);
        environment = new LinkedHashMap<>();
    }

    @Override
    protected void run(String input, Context context, CommandResult result) {
        watch(Cmd.code((envLine,state)->{
            if(envLine.contains("=")){
                String key = envLine.substring(0,envLine.indexOf("="));
            }
            return Result.next(envLine);
        }));
        super.run(input,context,result);
    }

    public void onLine(String line){
        int index=-1;
        if( (index=line.indexOf("="))>0){
            String key = line.substring(0,index);
            String value = line.substring(index+1);
            environment.put(key,value);
        }
    }

    public Map<String,String> getEnvironment(){
        return Collections.unmodifiableMap(environment);
    }

    @Override
    protected Cmd clone() {
        return null;
    }

    @Override
    public String toString(){return "env:";}
}
