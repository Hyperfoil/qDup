package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;

public class SetState extends Cmd {

    String key;
    String value;
    String populatedKey;
    String populatedValue;

    public SetState(String key) {
        this(key, null);
    }

    public SetState(String key, String value) {
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

        return "set-state: " + useKey + " " + useValue;
    }
}