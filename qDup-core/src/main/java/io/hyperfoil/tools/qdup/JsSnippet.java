package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.yaup.json.Json;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class JsSnippet {
    private static final Pattern functionPattern = Pattern.compile("^function ([a-zA-Z_{1}][a-zA-Z0-9_]+)\\(");

    private List<String> fnNames ;
    private final String snippet ;

    public JsSnippet(String snippet) {
        this.fnNames = extractFunctionNames(snippet);
        this.snippet = snippet;
    }

    public Json toJson() {
        Json json = new Json();
        json.add(snippet);
        return json;
    }
    public List<String> getNames() {
        return fnNames;
    }

    public String getFunction() {
        return snippet;
    }

    private List<String> extractFunctionNames(String snippet) {

        List<String> fNames = new ArrayList<>();

        String[] lines = snippet.split("[\\{\\}]");

        Arrays.stream(lines).forEach(line -> {
            Matcher matcher = functionPattern.matcher(line.trim());
            if (matcher.lookingAt()) {
                fNames.add(matcher.group(1));
            }
        });

        return fNames;
    }

    public static List<JsSnippet> fromList(List<String> snippets){
        return snippets.stream().map(snippet -> new JsSnippet(snippet)).collect(Collectors.toList());
    }
}
