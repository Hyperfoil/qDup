package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.yaup.json.Json;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Global {

    private static final XLogger logger = XLoggerFactory.getXLogger(Global.class);

    private final List<JsSnippet> jsSnippets;


    public Global(List<JsSnippet> jsSnippets) {
        this();
        this.addAllSnippets(jsSnippets);
    }

    public Global() {
        this.jsSnippets = new ArrayList<>();
    }

    public void addAllSnippets(List<JsSnippet> snippetList) {
        snippetList.forEach( jsSnippet -> addSnippet(jsSnippet));
    }


    public void addSnippet(JsSnippet snippet) {
        if ( ! jsSnippets.add(snippet) ) {
            logger.warn("JS Snippet not added");
        }
        snippet.getNames().forEach(name -> {
            if (getJsSnippetsList().contains(name)) {
                logger.warn("Mutiple JS Function names detected: " + name);
            }
        });
    }

    public List<String> getJsSnippetsList() {
        return this.jsSnippets.stream().flatMap(jsSnippet -> jsSnippet.getNames().stream()).collect(Collectors.toList());
    }

    public List<String> getJsSnippetsContents() {
        return this.jsSnippets.stream().map(jsSnippet -> jsSnippet.getFunction()).collect(Collectors.toList());
    }


    public List<JsSnippet> getJsSnippets() {
        return Collections.unmodifiableList(this.jsSnippets);
    }



    public void merge(Global newGlobal){
        this.addAllSnippets(newGlobal.getJsSnippets());
    }

    public Json toJson() {
        Json rtrn = new Json(false);

        String javascripSnippet = this.jsSnippets.stream().map(jsSnippet -> jsSnippet.getFunction()).collect(Collectors.joining("\n"));

        rtrn.add("javascript", javascripSnippet);
        return rtrn;
    }
}
