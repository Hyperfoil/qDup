package perf.qdup.cmd.impl;

import perf.qdup.Env;
import perf.qdup.Host;
import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;
import perf.qdup.config.Role;

public class RoleEnv extends Cmd {

    private final Role role;
    private final boolean isStart;

    public RoleEnv(Role role,boolean isStart){
        this.role = role;
        this.isStart = isStart;
    }

    @Override
    public void run(String input, Context context) {
        String env = context.getSession().shSync("env");
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
}
