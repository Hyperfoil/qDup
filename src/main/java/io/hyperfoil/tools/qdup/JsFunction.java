package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.yaup.json.Json;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class JsFunction {
    private static final Pattern functionPattern = Pattern.compile("^function ([a-zA-Z_{1}][a-zA-Z0-9_]+)\\(");

    private final String name ;
    private final String function ;

    public JsFunction(String function) {
        this.name = extractFunctionName(function);
        this.function = function;
    }

    public Json toJson() {
        Json json = new Json();
        json.add(function);
        return json;
    }
    public String getName() {
        return name;
    }

    public String getFunction() {
        return function;
    }

    private String extractFunctionName(String function) {
        Matcher matcher = functionPattern.matcher(function);
        if (matcher.lookingAt()) {
            return matcher.group(1);
        }
        return null;
    }

    public static List<JsFunction> fromList(List<String> functions){
        return functions.stream().map(function -> new JsFunction(function)).collect(Collectors.toList());
    }
}
