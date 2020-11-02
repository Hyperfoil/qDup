package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.yaup.AsciiArt;
import io.hyperfoil.tools.yaup.StringUtil;
import io.hyperfoil.tools.yaup.json.Json;

public class SetState extends Cmd {

    private static final String STATE_LOCK = "STATE_LOCK";

    String key;
    String value;
    String populatedKey;
    String populatedValue;

    public SetState(String key) {
        this(key, null);
    }

    public SetState(String key, String value) {
        this(key,value,StringUtil.PATTERN_DEFAULT_SEPARATOR,false);
    }
    public SetState(String key, String value,String separator, boolean silent) {
        super(silent);
        this.key = key;
        this.value = value;
        setPatternSeparator(separator);
        //this.separator = separator;
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
        synchronized (STATE_LOCK){
            try {
                populatedValue = this.value == null ? input.trim() : Cmd.populateStateVariables(this.value, this, context);
                if (StringUtil.isQuoted(populatedValue) && (StringUtil.removeQuotes(populatedValue)).trim().isEmpty()) {
                    populatedValue = "";
                }
                populatedKey = Cmd.populateStateVariables(this.key, this, context);
                if(populatedValue.contains(getPatternPrefix()) || populatedKey.contains(getPatternPrefix())){
                    //TODO populatedValue should already resolve patterns, any pattern prefix means it failed to resolve?

                }
                //why was the following pattern check here?
                if (Json.isJsonLike(populatedValue) /*&& populatedValue.contains(StringUtil.PATTERN_PREFIX) && populatedValue.contains(StringUtil.PATTERN_SUFFIX)*/) {
                    Json fromPopulatedValue = Json.fromString(populatedValue);
                    if (!fromPopulatedValue.isEmpty()) {
                        context.getState().set(populatedKey, fromPopulatedValue);
                    } else {
                        context.getState().set(populatedKey, populatedValue);
                    }
                } else {
                    context.getState().set(populatedKey, populatedValue);
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        context.next(input);
    }

    @Override
    public Cmd copy() {
        return new SetState(key, value, getPatternSeparator(), silent);
    }

    @Override
    public String getLogOutput(String output, Context context) {
        String useKey = populatedKey != null ? populatedKey : key;
        String useValue = populatedValue != null ? populatedValue : value;

        if(isSilent()){
            return "set-state: " + useKey;
        }
        return "set-state: " + useKey + " " + (useValue.trim().isEmpty() ? "\"\"" : useValue);
    }
}