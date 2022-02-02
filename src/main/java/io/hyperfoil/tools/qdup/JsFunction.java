package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.yaup.json.Json;

import java.util.ArrayList;
import java.util.List;

public class JsFunction {
    private final String function ;

    public JsFunction(String function) {
        this.function = function;
    }


    public Json toJson() {
        Json json = new Json();
        json.add(function);
        return json;
    }

    public String getFunction() {
        return function;
    }

}
