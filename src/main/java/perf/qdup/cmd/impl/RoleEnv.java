package perf.qdup.cmd.impl;

import perf.qdup.Env;
import perf.qdup.Host;
import perf.qdup.SshSession;
import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;
import perf.qdup.config.Role;
import perf.qdup.stream.SuffixStream;

/**
 * Cmd injected at the start and end of setup to capture changes to env.
 * The changes are then applied before the start of all run-scripts in the role.
 */
public class RoleEnv extends Cmd {

    private final Role role;
    private final boolean isStart;

    public RoleEnv(Role role,boolean isStart){
        this.role = role;
        this.isStart = isStart;
    }

    @Override
    public String toString(){
        return isStart ?
            "env" : // todo output different toString for isStart?
            "env";
    }

    @Override
    public void run(String input, Context context) {
        int delay = context.getSession().getDelay();
        context.getSession().setDelay(SuffixStream.DEFAULT_DELAY);
        String env = context.getSession().shSync("env");
        context.getSession().setDelay(delay);
        Host host = context.getSession().getHost();
        if(isStart){
            if(!role.hasEnvironment(host)){
                role.addEnv(host,new Env());
            }
            role.getEnv(host).loadBefore(env);
        }else{
            if(role.hasEnvironment(host)){
                role.getEnv(host).loadAfter(env);
            }else{
                //TODO log error trying to set after without before
            }
        }
        context.next(input);
    }

    @Override
    public Cmd copy() {
        return new RoleEnv(role,isStart);
    }

    @Override
    public String getLogOutput(String output,Context context){
        if(isStart){
            return "start-env:";
        }else{
            return "stop-env:";
        }
    }
}
