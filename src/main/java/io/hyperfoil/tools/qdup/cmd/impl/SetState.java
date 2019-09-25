package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.yaup.StringUtil;

public class SetState extends Cmd {

    String key;
    String value;
    String populatedKey;
    String populatedValue;

    public SetState(String key) {
        this(key, null);
    }

    public SetState(String key, String value) {
        this(key,value,false);
    }
    public SetState(String key, String value,boolean silent) {
        super(silent);
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }


    @Override
    public String toString() {
        return "set-state: " + this.key + (this.value == null ? "" : " " + this.value);
    }

    @Override
    public void run(String input, Context context) {
        populatedValue = this.value == null ? input.trim() : Cmd.populateStateVariables(this.value, this, context.getState());
        if(StringUtil.isQuoted(populatedValue) && (StringUtil.removeQuotes(populatedValue)).trim().isEmpty()){
            populatedValue="";
        }
        populatedKey = Cmd.populateStateVariables(this.key, this, context.getState());
        context.getState().set(populatedKey, populatedValue);
        context.next(input);
    }

    @Override
    public Cmd copy() {
        return new SetState(key, value);
    }

    @Override
    public String getLogOutput(String output, Context context) {
        String useKey = populatedKey != null ? populatedKey : key;
        String useValue = populatedValue != null ? populatedValue : value;

        if(isSilent()){
            return "set-state: "+key+" "+value;
        }
        return "set-state: " + useKey + " " + (useValue.trim().isEmpty() ? "\"\"" : useValue);
    }
}