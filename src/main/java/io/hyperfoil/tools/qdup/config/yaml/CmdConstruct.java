package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.impl.Sleep;
import io.hyperfoil.tools.yaup.Sets;
import io.hyperfoil.tools.yaup.StringUtil;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.*;
import io.hyperfoil.tools.yaup.json.Json;
import io.hyperfoil.tools.yaup.yaml.DeferableConstruct;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static io.hyperfoil.tools.yaup.yaml.OverloadConstructor.json;

public class CmdConstruct extends DeferableConstruct {

    private String tag;
    private FromString fromString;
    private Function<Json, Cmd> fromJson;
    private Map<String, BiConsumer<Cmd,Node>> topLevelKeys;
    private final List<String> expectedKeys = new ArrayList<>();

    public CmdConstruct(String tag,FromString fromString,Function<Json,Cmd> fromJson,String...keys){
        this.tag = tag;
        this.fromString = fromString;
        this.fromJson = fromJson;
        this.topLevelKeys = new HashMap<>();
        expectedKeys.addAll(Arrays.asList(keys));
        this.validate();
    }

    protected void setTag(String tag){
        this.tag = tag;
    }


    public void addTopLevelkey(String key,BiConsumer<Cmd,Node> consumer){
       topLevelKeys.put(key,consumer);
    }

    public void validate(){
        if(fromString==null && fromJson == null){
            throw new RuntimeException("CmdConstruct requires a String or Json builder");
        }
    }

    public String getTag(){return tag;}
    public boolean supportString(){return fromString!=null;}
    public boolean supportsJson(){return fromJson!=null;}

    public void populate(final Cmd cmd,MappingNode mappingNode) {
        for(NodeTuple nodeTuple : mappingNode.getValue()){
            populate(cmd, nodeTuple, mappingNode);
        }
    }
    public List<Cmd> sequenceToCmds(SequenceNode thenNodes){
       List<Cmd> rtrn = new LinkedList<>();
       thenNodes.getValue().forEach(then->{
          Object built = then instanceof ScalarNode ? deferAs(then,new Tag("cmd")) : defer(then);
          if(built != null && built instanceof Cmd){
             rtrn.add((Cmd)built);
          }else{
             throw new YAMLException("failed to construct Cmd"+then.getStartMark());
          }
       });
       return rtrn;
    }
    private void populate(final Cmd cmd, NodeTuple nodeTuple, MappingNode parentNode){
        String key = ((ScalarNode)nodeTuple.getKeyNode()).getValue();
        Node valueNode = nodeTuple.getValueNode();
        switch (key){
            case CmdMapping.WITH:
                if(valueNode instanceof MappingNode){
                    MappingNode withMap = (MappingNode)valueNode;
                    Json withJson = json(withMap);
                    withJson.forEach((k,v)->{
                        cmd.with(k.toString(),v);
                    });
                }else{
                    throw new YAMLException(CmdMapping.WITH+" requires a map "+nodeTuple.getKeyNode().getStartMark());
                }
                break;
            case CmdMapping.SILENT:
                if(valueNode instanceof ScalarNode){
                    String value = ((ScalarNode)valueNode).getValue();
                    if("true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value)){
                        cmd.setSilent(true);
                    }
                }else{
                    throw new YAMLException(CmdMapping.SILENT+" requires a scalar "+valueNode.getStartMark());
                }
                break;
            case CmdMapping.WATCH:
                if(valueNode instanceof SequenceNode){
                    SequenceNode thenNodes = (SequenceNode)valueNode;
                   sequenceToCmds(thenNodes).forEach(cmd::watch);
                }else{
                    throw new YAMLException(CmdMapping.WATCH+" requires a list of commands "+valueNode.getStartMark());
                }
                break;
            case CmdMapping.THEN:
                if(valueNode instanceof SequenceNode){
                    SequenceNode thenNodes = (SequenceNode)valueNode;
                    cmd.ensureNewSet();
                    sequenceToCmds(thenNodes).forEach(cmd::then);
                }else{
                    throw new YAMLException(CmdMapping.THEN+" requires a list of commands "+valueNode.getStartMark());
                }
                break;
            case CmdMapping.ON_SIGNAL:
                if(valueNode instanceof MappingNode){
                    MappingNode signalMapNode = (MappingNode)valueNode;
                    signalMapNode.getValue().forEach(signalTuple->{
                        if(signalTuple.getKeyNode() instanceof ScalarNode){
                            String signalName = ((ScalarNode)signalTuple.getKeyNode()).getValue();
                            if(signalTuple.getValueNode() instanceof SequenceNode){
                                SequenceNode signalSequence = (SequenceNode)signalTuple.getValueNode();
                                sequenceToCmds(signalSequence).forEach(built->cmd.onSignal(signalName,built));
                            }else{
                                throw new YAMLException(CmdMapping.ON_SIGNAL +" "+signalName+" value must be a sequence"+signalTuple.getValueNode().getStartMark());
                            }
                        }else{
                            throw new YAMLException(CmdMapping.ON_SIGNAL +" keys must be a scalar"+signalTuple.getKeyNode().getStartMark());
                        }
                    });
                }else{
                    throw new YAMLException(CmdMapping.ON_SIGNAL +" requires a map "+valueNode.getStartMark());
                }
                break;
            case CmdMapping.TIMER:
                if(valueNode instanceof MappingNode){
                    MappingNode timerMapNode = (MappingNode)valueNode;
                    timerMapNode.getValue().forEach(timerTuple->{
                        String timeout = ((ScalarNode)timerTuple.getKeyNode()).getValue();
                        long timeoutMs = Sleep.parseToMs(timeout);
                        Node timerTasks = timerTuple.getValueNode();
                        if(timerTasks instanceof SequenceNode){
                            SequenceNode timerTaskList = (SequenceNode)timerTasks;
                            sequenceToCmds(timerTaskList).forEach(built->cmd.addTimer(timeoutMs,built));
                        }else{
                            throw new YAMLException(timeout+" timer requires a list of Cmds "+timerTasks.getStartMark());
                        }
                    });
                }else{
                    throw new YAMLException(CmdMapping.TIMER+" requires a map "+valueNode.getStartMark());
                }
                break;
            case CmdMapping.PREFIX:
                if(valueNode instanceof ScalarNode) {
                    String value = ((ScalarNode) valueNode).getValue();
                    cmd.setPatternPrefix(value);
                }else{
                    throw new YAMLException(CmdMapping.PREFIX+" requires a scalar "+valueNode.getStartMark());
                }
                break;
            case CmdMapping.SUFFIX:
                if(valueNode instanceof ScalarNode) {
                    String value = ((ScalarNode) valueNode).getValue();
                    cmd.setPatternSuffix(value);
                }else{
                    throw new YAMLException(CmdMapping.SUFFIX+" requires a scalar "+valueNode.getStartMark());
                }
                break;
            case CmdMapping.SEPARATOR:
                if(valueNode instanceof ScalarNode) {
                    String value = ((ScalarNode) valueNode).getValue();
                    cmd.setPatternSeparator(value);
                }else{
                    throw new YAMLException(CmdMapping.SEPARATOR+" requires a scalar "+valueNode.getStartMark());
                }
                break;
            case CmdMapping.JS_PREFIX:
                if(valueNode instanceof ScalarNode) {
                    String value = ((ScalarNode) valueNode).getValue();
                    cmd.setPatternJavascriptPrefix(value);
                }else{
                    throw new YAMLException(CmdMapping.JS_PREFIX+" requires a scalar "+valueNode.getStartMark());
                }
                break;
            case CmdMapping.IDLE_TIMER:
                if(valueNode instanceof ScalarNode) {
                    String value = ((ScalarNode) valueNode).getValue();
                    if(value.toLowerCase().equals("false")){
                        cmd.disableIdleTimer();
                    }else {
                        long millis = StringUtil.parseToMs(value);
                        if(millis <= 0){
                            throw new YAMLException(CmdMapping.IDLE_TIMER+" can be false or a valid duration "+valueNode.getStartMark());
                        }else{
                            cmd.setIdleTimer(millis);
                        }
                    }
                }else{
                    throw new YAMLException(CmdMapping.IDLE_TIMER+" requires a scalar "+valueNode.getStartMark());
                }
                break;
            case CmdMapping.STATE_SCAN:
                if(valueNode instanceof ScalarNode) {
                    String value = ((ScalarNode) valueNode).getValue();
                    if(value.toLowerCase().equals("false")){
                        cmd.setStateScan(false);
                    } else {
                      //state scan is on by default
                    }
                }else{
                    throw new YAMLException(CmdMapping.IDLE_TIMER+" requires a scalar "+valueNode.getStartMark());
                }
                break;
            default:
                if(getTag().equals(key) || expectedKeys.contains(key)){
                    //ignore
                }else{
                   if(topLevelKeys.containsKey(key)){
                      topLevelKeys.get(key).accept(cmd,valueNode);
                   } else {
                      throw new YAMLException("unsupported key " + key + " " + nodeTuple.getKeyNode().getStartMark());
                   }
                }
        }
    }
    @Override
    public Object construct(Node node) {
        if(node instanceof MappingNode){
            Cmd rtrn = null;
            MappingNode mappingNode = (MappingNode)node;
            Node tagValue = mappingNode.getValue().stream()
                    .filter(keyFilter(tag))
                    .findFirst()
                    .orElseThrow(()->new YAMLException("Missing required key="+tag+" "+node.getStartMark()))
                .getValueNode();

            //identify the pattern prefix and suffix for this command
            Map<String,String> stateIndicators = new HashMap<>();
            stateIndicators.put(CmdMapping.PREFIX,StringUtil.PATTERN_PREFIX);
            stateIndicators.put(CmdMapping.SUFFIX,StringUtil.PATTERN_SUFFIX);
            mappingNode.getValue().forEach(nodeTuple -> {
                if(nodeTuple.getKeyNode() instanceof ScalarNode && nodeTuple.getValueNode() instanceof ScalarNode){
                    String keyNode = ((ScalarNode)nodeTuple.getKeyNode()).getValue();
                    String valueNode = ((ScalarNode)nodeTuple.getValueNode()).getValue();
                    if(CmdMapping.PREFIX.equals(keyNode)){
                        stateIndicators.put(CmdMapping.PREFIX,valueNode);
                    }else if (CmdMapping.SUFFIX.equals(keyNode)){
                        stateIndicators.put(CmdMapping.SUFFIX,valueNode);
                    }
                }
            });

            if(tagValue instanceof ScalarNode){
                if(supportString()){
                    String value = ((ScalarNode)tagValue).getValue();
                    try {
                        rtrn = fromString.apply(value, stateIndicators.get(CmdMapping.PREFIX), stateIndicators.get(CmdMapping.SUFFIX));
                    }catch(YAMLException e){
                        throw new YAMLException(e.getMessage()+node.getStartMark());
                    }
                }else{
                    throw new YAMLException(tag+" does not support scalar values "+node.getStartMark());
                }
            }else{
                if(supportsJson()){
                    Json json = json(tagValue);
                    Set<Object> jsonKeys = Sets.of(json.keys().toArray());
                    jsonKeys.removeAll(expectedKeys);
                    if(!jsonKeys.isEmpty()){
                        throw new YAMLException("unexpected key(s) "+jsonKeys+"for "+tag+node.getStartMark());
                    }
                    try {
                        rtrn = fromJson.apply(json);

                    }catch(YAMLException e){
                        throw new YAMLException(e.getMessage()+node.getStartMark());
                    }
                }else{
                    throw new YAMLException(tag+" does not support list|map values "+tagValue.getStartMark());
                }
            }
            if(rtrn==null){
                throw new YAMLException(tag+" failed to create Cmd from "+tagValue.getStartMark());
            }
            populate(rtrn,mappingNode);
            return rtrn;
        }
        throw new YAMLException("Cmd "+tag+" requires a mapping node but saw "+node.getClass().getSimpleName()+" "+node.getStartMark());
    }
}
