package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.yaup.json.Json;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Globals {

    private static final XLogger logger = XLoggerFactory.getXLogger(Globals.class);

    private final List<JsSnippet> jsSnippets;
    private Json settings;

    public Globals(List<JsSnippet> jsSnippets) {
        this();
        this.addAllSnippets(jsSnippets);
    }

    public Globals() {
        this.jsSnippets = new ArrayList<>();
        this.settings = new Json(false);
    }

    public void addAllSnippets(List<JsSnippet> snippetList) {
        snippetList.forEach( jsSnippet -> addSnippet(jsSnippet));
    }

    public void addSnippet(JsSnippet snippet) {
        snippet.getNames().forEach(name -> {
            if (getJsSnippetsList().contains(name)) {
                logger.warn("Mutiple JS Function names detected: " + name);
            }
        });
        Set<String> uniqNames = snippet.getNames().stream().collect(Collectors.toSet());
        if(uniqNames.size() < snippet.getNames().size()){
            uniqNames.forEach(name -> {
                if( snippet.getNames().stream().filter(snippetName -> snippetName.equals(name)).collect(Collectors.toList()).size() > 1){
                    logger.warn("Mutiple JS Function names detected: " + name);
                }
            });
        }

        if ( ! jsSnippets.add(snippet) ) {
            logger.warn("JS Snippet not added");
        }
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

    public void addSetting(String key,Object value){
        settings.set(key,value);
    }
    public Json getSettings(){return settings;}
    public boolean hasSetting(String key){
        return settings.has(key);
    }
    public <T> T getSetting(String key, T defaultValue){
        return settings.has(key) ? (T)settings.get(key) : defaultValue;
    }

    public void merge(Globals newGlobals){
        this.addAllSnippets(newGlobals.getJsSnippets());
        settings.merge(newGlobals.getSettings());
    }

    public Json toJson() {
        Json rtrn = new Json(false);
        if(!this.jsSnippets.isEmpty()){
            String javascripSnippet = this.jsSnippets.stream().map(jsSnippet -> jsSnippet.getFunction()).collect(Collectors.joining("\n"));
            rtrn.add("javascript", javascripSnippet);
        }
        if(!settings.isEmpty()) {
            rtrn.add("settings", Json.toObjectMap(settings));
        }
        return rtrn;
    }
}
