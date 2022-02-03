package io.hyperfoil.tools.qdup;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class Global {

    private static final XLogger logger = XLoggerFactory.getXLogger(Global.class);

    private final Map<String, JsFunction> jsFunctions;


    public Global(List<JsFunction> jsFunctions) {
        this();
        this.addAllFunctions(jsFunctions);
    }

    public Global() {
        this.jsFunctions = new ConcurrentHashMap<>();
        this.jsFunctions.put("milliseconds", new JsFunction("function milliseconds(v){ return Packages.io.hyperfoil.tools.yaup.StringUtil.parseToMs(v)}"));
        this.jsFunctions.put("seconds", new JsFunction("function seconds(v){ return Packages.io.hyperfoil.tools.yaup.StringUtil.parseToMs(v)/1000}"));
        this.jsFunctions.put("range", new JsFunction("function range(start,stop,step=1){ return Array(Math.ceil(Math.abs(stop - start) / step)).fill(start).map((x, y) => x + Math.ceil(Math.abs(stop - start) / (stop - start)) * y * step);}"));
    }

    public void addAllFunctions(List<JsFunction> functionList) {
        functionList.forEach( jsFunction -> addFunction(jsFunction.getName(), jsFunction));
    }


    public void addFunction(String name, JsFunction function) {
        if (jsFunctions.putIfAbsent(name, function) != null) {
            logger.warn("Mutiple JS Function names detected: " + name);
        }
    }

    public List<String> getJsFunctionsList() {
        return this.jsFunctions.values().stream().map(jsFunction -> jsFunction.getFunction()).collect(Collectors.toList());
    }

    public Map<String, JsFunction> getJsFunctions() {
        return Collections.unmodifiableMap(this.jsFunctions);
    }



    public void merge(Global newGlobal){
        this.addAllFunctions(newGlobal.getJsFunctions());
    }

}
