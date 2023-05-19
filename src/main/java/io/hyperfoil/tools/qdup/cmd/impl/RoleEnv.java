package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.config.Role;
import io.hyperfoil.tools.qdup.Env;
import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.qdup.stream.SuffixStream;

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
        int delay = context.getShell().getDelay();
        context.getShell().setDelay(SuffixStream.DEFAULT_DELAY);
        String env = context.getShell().shSync("env");
        context.getShell().setDelay(delay);
        Host host = context.getShell().getHost();
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
            return "stop-env: "+(role.hasEnvironment(context.getHost()) ? role.getEnv(context.getHost()).getDiff().getCommand() : "");
        }
    }
}
