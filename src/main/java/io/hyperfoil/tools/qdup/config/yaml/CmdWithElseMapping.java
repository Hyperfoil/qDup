package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.cmd.CmdWithElse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CmdWithElseMapping extends CmdMapping{

    public static final String ELSE = "else";

    public CmdWithElseMapping(String key, CmdEncoder encoder) {
        super(key, encoder);
    }

    @Override
    public Map<Object, Object> getMap(Object o){
        Map<Object, Object> rtrn = super.getMap(o);
        if(o instanceof CmdWithElse){
            CmdWithElse cmd = (CmdWithElse)o;
            if(cmd.hasElse()){
                List<Object> elses = new ArrayList<>();
                cmd.getElses().forEach(entry->elses.add(defer(entry)));
                rtrn.put(ELSE,elses);
            }
        }
        return rtrn;
    }
}
