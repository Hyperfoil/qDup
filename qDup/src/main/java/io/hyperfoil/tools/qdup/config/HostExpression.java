package io.hyperfoil.tools.qdup.config;

import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.yaup.HashedList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HostExpression {

    public static final String EXPRESSION_PATTERN = "= \\s*\\w+(?:\\s*[\\-+]\\s*\\w+)*";

    private final String expression;
    public HostExpression(String expression){
        this.expression = expression;
    }
    public String getExpression(){return expression;}

    public List<Host> getHosts(RunConfig runConfig){
        Set<Host> rtrn = new HashSet<>();

        if(getExpression().matches(EXPRESSION_PATTERN)){
            String toMatch = getExpression().substring(2);
            Pattern p = Pattern.compile("(?<opt>[\\-+]*)\\s*(?<role>\\w+)");
            Matcher m = p.matcher(toMatch);
            while(m.find()){
                String opt = m.group("opt");
                String role = m.group("role");
                switch (opt){
                    case "-":
                        if(runConfig.getRoleNames().contains(role)){
                            rtrn.removeAll(runConfig.getRole(role).getHosts(runConfig));
                        }else{
                            if(RunConfigBuilder.ALL_ROLE.equals(role)){
                                rtrn.removeAll(runConfig.getAllHostsInRoles());
                            }
                            //TODO do we warn the role is missing?
                        }
                        break;
                    case "+":
                    default:
                        if(runConfig.getRoleNames().contains(role)){
                            rtrn.addAll(runConfig.getRole(role).getHosts(runConfig));
                        }else{
                            if(RunConfigBuilder.ALL_ROLE.equals(role)){
                                rtrn.addAll(runConfig.getAllHostsInRoles());
                            }
                            //TODO do we warn the role is missing?
                        }
                        break;
                }
            }
        }
        return new ArrayList<>(rtrn);
    }
}
