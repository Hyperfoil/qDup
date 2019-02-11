package perf.qdup.config.yaml;

import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.*;
import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.impl.Sleep;
import perf.yaup.json.Json;
import perf.yaup.yaml.DeferableConstruct;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static perf.qdup.config.yaml.CmdMapping.*;
import static perf.yaup.yaml.OverloadConstructor.json;

public class CmdConstruct extends DeferableConstruct {

    private String tag;
    private Function<String, Cmd> fromString;
    private Function<Json, Cmd> fromJson;
    private final List<String> expectedKeys = new ArrayList<>();

    public CmdConstruct(String tag,Function<String,Cmd> fromString,Function<Json,Cmd> fromJson,String...keys){
        this.tag = tag;
        this.fromString = fromString;
        this.fromJson = fromJson;
        expectedKeys.addAll(Arrays.asList(keys));
        this.validate();
    }

    protected void setTag(String tag){
        this.tag = tag;
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
        mappingNode.getValue().forEach(nodeTuple -> {
            populate(cmd,nodeTuple);
        });
    }
    public void populate(final Cmd cmd, NodeTuple nodeTuple){
        String key = ((ScalarNode)nodeTuple.getKeyNode()).getValue();
        Node valueNode = nodeTuple.getValueNode();
        switch (key){
            case WITH:
                if(valueNode instanceof MappingNode){
                    MappingNode withMap = (MappingNode)valueNode;
                    Json withJson = json(withMap);
                    withJson.forEach((k,v)->{
                        cmd.with(k.toString(),v.toString());
                    });
                }else{
                    throw new YAMLException(WITH+" requires a map "+valueNode.getStartMark());
                }
                break;
            case SILENT:
                if(valueNode instanceof ScalarNode){
                    String value = ((ScalarNode)valueNode).getValue();
                    if("true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value)){
                        cmd.setSilent(true);
                    }
                }else{
                    throw new YAMLException(SILENT+" requires a scalar"+valueNode.getStartMark());
                }
                break;
            case THEN:
                if(valueNode instanceof SequenceNode){
                    SequenceNode thenNodes = (SequenceNode)valueNode;
                    thenNodes.getValue().forEach(then->{
                        Object built = defer(then);
                        if(built != null && built instanceof Cmd){
                            cmd.then((Cmd)built);
                        }else{
                            throw new YAMLException("failed to construct Cmd"+then.getStartMark());
                        }
                    });
                }else{
                    throw new YAMLException(THEN+" requires a list of commands "+valueNode.getStartMark());
                }
                break;
            case ONSIGNAL:
                if(valueNode instanceof MappingNode){
                    MappingNode signalMapNode = (MappingNode)valueNode;
                    signalMapNode.getValue().forEach(signalTuple->{
                        if(signalTuple.getKeyNode() instanceof ScalarNode){
                            String signalName = ((ScalarNode)signalTuple.getKeyNode()).getValue();
                            if(signalTuple.getValueNode() instanceof SequenceNode){
                                SequenceNode signalSequence = (SequenceNode)signalTuple.getValueNode();
                                signalSequence.getValue().forEach(task->{
                                    Object built = defer(task);
                                    if(built !=null && built instanceof Cmd){
                                        //TODO add signal when merge onsignal branch (forgot and started working it already)
                                        //finalRtrn.onSignal(signalName,(Cmd)built);
                                    }else{
                                        throw new YAMLException("failed to construct Cmd"+task.getStartMark());
                                    }
                                });
                            }else{
                                throw new YAMLException(ONSIGNAL+" "+signalName+" value must be a sequence"+signalTuple.getValueNode().getStartMark());
                            }
                        }else{
                            throw new YAMLException(ONSIGNAL+" keys must be a scalar"+signalTuple.getKeyNode().getStartMark());
                        }
                    });
                }else{
                    throw new YAMLException(ONSIGNAL+" requires a map "+valueNode.getStartMark());
                }
                break;
            case TIMER:
                if(valueNode instanceof MappingNode){
                    MappingNode timerMapNode = (MappingNode)valueNode;
                    timerMapNode.getValue().forEach(timerTuple->{
                        String timeout = ((ScalarNode)timerTuple.getKeyNode()).getValue();
                        long timeoutMs = Sleep.parseToMs(timeout);
                        Node timerTasks = timerTuple.getValueNode();
                        if(timerTasks instanceof SequenceNode){

                            SequenceNode timerTaskList = (SequenceNode)timerTasks;
                            timerTaskList.getValue().forEach(task->{
                                Object built = defer(task);
                                if(built !=null && built instanceof Cmd){
                                    cmd.addTimer(timeoutMs,(Cmd)built);
                                }else{
                                    throw new YAMLException("failed to construct Cmd"+task.getStartMark());
                                }
                            });
                        }else{
                            throw new YAMLException(timeout+" timer requires a list of Cmds "+timerTasks.getStartMark());
                        }
                    });
                }else{
                    throw new YAMLException(TIMER+" requires a map "+valueNode.getStartMark());
                }
                break;
            default:
                if(getTag().equals(key) || expectedKeys.contains(key)){
                    //ignore
                }else{
                    throw new YAMLException("unsupported key "+key+" "+nodeTuple.getKeyNode().getStartMark());
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

            if(tagValue instanceof ScalarNode){
                if(supportString()){
                    String value = ((ScalarNode)tagValue).getValue();
                    rtrn = fromString.apply(value);
                }else{
                    throw new YAMLException(tag+" does not support scalar values "+tagValue.getStartMark());
                }
            }else{
                if(supportsJson()){
                    Json json = json(tagValue);
                    rtrn = fromJson.apply(json);
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
