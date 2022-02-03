package io.hyperfoil.tools.qdup;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Global {

    private static final XLogger logger = XLoggerFactory.getXLogger(Global.class);

    private static final Pattern functionPattern = Pattern.compile("^function ([a-zA-Z_{1}][a-zA-Z0-9_]+)\\(");
    private final Map<String, String> jsFunctions;


    public Global(List<String> jsFunctions){
        this();
        this.addAllFunctions(jsFunctions);
    }

    public Global() {
        this.jsFunctions = new ConcurrentHashMap<>();
        this.jsFunctions.put("milliseconds", "function milliseconds(v){ return Packages.io.hyperfoil.tools.yaup.StringUtil.parseToMs(v)}");
        this.jsFunctions.put("seconds", "function seconds(v){ return Packages.io.hyperfoil.tools.yaup.StringUtil.parseToMs(v)/1000}");
        this.jsFunctions.put("range", "function range(start,stop,step=1){ return Array(Math.ceil(Math.abs(stop - start) / step)).fill(start).map((x, y) => x + Math.ceil(Math.abs(stop - start) / (stop - start)) * y * step);}");
    }

    public void addAllFunctions(List<String> functionList) {

        Map<String, String> functionMap = new HashMap<>();
        functionList.forEach( function -> functionMap.put(extractFunctionName(function), function));
        this.addAllFunctions(functionMap);

    }

    private String extractFunctionName(String function){
        Matcher matcher = functionPattern.matcher(function);
        if (matcher.lookingAt()){
            return matcher.group(1);
        }
        return null;
    }

    public void addAllFunctions(Map<String, String> functionMap) {
        functionMap.forEach((key, value) -> addFunction(key, value));
    }

    public void addFunction(String name, String function) {
        if (jsFunctions.putIfAbsent(name, function) != null) {
            //TODO:: determine how to correctly handle name collisions, atm a WARN is logged
            logger.warn("Mutiple JS Function names detected: " + name);
        }
    }

    public Map<String, String> getJsFunctionsMap(){
        return this.jsFunctions;
    }

    public List<String> getJsFunctionsList(){
        return List.copyOf(this.jsFunctions.values());
    }




}
