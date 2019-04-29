package perf.qdup.config.yaml;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Construct;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.parser.ParserException;
import org.yaml.snakeyaml.scanner.ScannerException;
import perf.qdup.Host;
import perf.qdup.State;
import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Script;
import perf.qdup.cmd.impl.Abort;
import perf.qdup.cmd.impl.Countdown;
import perf.qdup.cmd.impl.CtrlC;
import perf.qdup.cmd.impl.Done;
import perf.qdup.cmd.impl.Download;
import perf.qdup.cmd.impl.Echo;
import perf.qdup.cmd.impl.ForEach;
import perf.qdup.cmd.impl.JsCmd;
import perf.qdup.cmd.impl.Log;
import perf.qdup.cmd.impl.QueueDownload;
import perf.qdup.cmd.impl.ReadState;
import perf.qdup.cmd.impl.Regex;
import perf.qdup.cmd.impl.RepeatUntilSignal;
import perf.qdup.cmd.impl.ScriptCmd;
import perf.qdup.cmd.impl.SetState;
import perf.qdup.cmd.impl.Sh;
import perf.qdup.cmd.impl.Signal;
import perf.qdup.cmd.impl.Sleep;
import perf.qdup.cmd.impl.Upload;
import perf.qdup.cmd.impl.WaitFor;
import perf.qdup.cmd.impl.XmlCmd;
import perf.qdup.config.CmdBuilder;
import perf.qdup.config.Role;
import perf.qdup.config.RunConfigBuilder;
import perf.qdup.config.waml.WamlException;
import perf.qdup.config.waml.WamlParser;
import perf.yaup.file.FileUtility;
import perf.yaup.json.Json;
import perf.yaup.yaml.DeferableConstruct;
import perf.yaup.yaml.MapRepresenter;
import perf.yaup.yaml.Mapping;
import perf.yaup.yaml.OverloadConstructor;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Parser {

    final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());

    public static Parser getInstance(){
        Parser rtrn = new Parser();
        RoleConstruct roleConstruct = new  RoleConstruct();

        rtrn.addMap(YamlFile.class,
            "yamlFile",
            new YamlFileConstruct(roleConstruct),
            YamlFileConstruct.MAPPING
        );
        rtrn.addMap(Role.class,
                "role",
                roleConstruct,
                //TODO role encoding :)
                (role)->new HashMap<>());
        rtrn.addValueType("roles","roles");
        rtrn.addMap(State.class,
                "states",
                new StateConstruct(),
                (state)-> Json.toObjectMap(state.toJson())
        );
        rtrn.addValueType("states","states");
        rtrn.addEncoding(Host.class,
                "host",
                new HostConstruct(),
                (host)->host.toString()
        );
        rtrn.addValuePattern("host",Host.HOST_PATTERN);
        rtrn.addCmd(
            Abort.class,
            "abort",
            (cmd)->cmd.getMessage(),
            (str)-> new Abort(str),
            null
        );
        rtrn.addCmd(
            Countdown.class,
            "countdown",
            (cmd)->(cmd.getName()+" "+cmd.getInitial()),

            (str)-> {
                List<String> split = CmdBuilder.split(str);
                if (split.size() == 2) {
                    return new Countdown(split.get(0), Integer.parseInt(split.get(1)));
                } else {
                    throw new YAMLException("cannot create countdown from " + str);
                }
            },
            (json)->new Countdown(json.getString("path"),(int)json.getLong("initial"))

        );

        rtrn.addCmd(
            CtrlC.class,
            "ctrlC",
            true,
            (cmd)->"",
            (str)->new CtrlC(),
            (json)->new CtrlC()
        );
        rtrn.addCmd(
            Done.class,
            "done",
            true,
            (cmd)->"",
            (str)->new Done(),
            (json)->new Done()
        );
        rtrn.addCmd(
            Download.class,
            "download",
            (cmd)->(cmd.getPath()+(cmd.getDestination()!=null && !cmd.getDestination().isEmpty() ? " "+cmd.getDestination() : "" )),
            (str)->{
                List<String> split = CmdBuilder.split(str);
                if(split.size()==2){
                    return new Download(split.get(0),split.get(1));
                }else{
                    throw new YAMLException("cannot create download from "+str);
                }
            },
            (json)->new Download(json.getString("path"),json.getString("destination",""))

        );
        rtrn.addCmd(
            Echo.class,
            "echo",
            true,
            (cmd)->"",
            (str)->new Echo(),
            null
        );
        //Exec
        //ExitCode
        rtrn.addCmd(
            ForEach.class,
            "for-each",
            (cmd)->((cmd.getName()+" "+cmd.getDeclaredInput()).trim()),
            (str)->{
                List<String> split = CmdBuilder.split(str);
                if(split.size()==1){
                    return new ForEach(split.get(0));
                }else if (split.size()==2){
                    return new ForEach(split.get(0),split.get(1));
                }else{
                    throw new YAMLException("cannot create for-each from "+str);
                }
            },
            (json)->new ForEach(json.getString("name"),json.getString("input",""))
        );
        //Invoke
        rtrn.addCmd(
            JsCmd.class,
            "js",
            (cmd)->cmd.getCode(),
            (str)->new JsCmd(str),
            null
        );
        rtrn.addCmd(
            Log.class,
            "log",
            (cmd)->cmd.getMessage(),
            (str)->new Log(str),
            null
        );
        rtrn.addCmd(
            QueueDownload.class,
            "queue-download",
            (cmd)->(cmd.getPath()+(cmd.getDestination()!=null && !cmd.getDestination().isEmpty() ? " "+cmd.getDestination() : "" )),
            (str)->{
                List<String> split = CmdBuilder.split(str);
                if(split.size()==1){
                    return new QueueDownload(split.get(0));
                }else if (split.size()==2){
                    return new QueueDownload(split.get(0),split.get(1));
                }else{
                    throw new YAMLException("cannot create queue-download from "+str);
                }
            },
            (json)->{
                return new QueueDownload(json.getString("path"),json.getString("destination",""));
            }
        );
        rtrn.addCmd(
            ReadState.class,
        "read-state",
            (cmd)->cmd.getKey(),
            (str)->new ReadState(str),
            null
        );
        rtrn.addCmd(
            Regex.class,
            "regex",
            (cmd)->cmd.getPattern(),
            (str)->new Regex(str),
            null
        );
        rtrn.addCmd(
            RepeatUntilSignal.class,
        "repeat-until",
            (cmd)->cmd.getName(),
            (str)->new RepeatUntilSignal(str),
            null
        );
        rtrn.addCmd(
            ScriptCmd.class,
            "script",
                (cmd)->cmd.getName(),
                (str)->new ScriptCmd(str),
                (json)->new ScriptCmd(json.getString("name"),json.getBoolean("async",false))
        );
        rtrn.addCmd(
            SetState.class,
            "set-state",
            (cmd)->cmd.getKey()+(cmd.getValue()!=null && !cmd.getValue().isEmpty() ? " "+cmd.getValue() : ""),
            (str)->new SetState(str),
            (json)->new SetState(json.getString("key"),json.getString("value",null))
        );
        rtrn.addCmd(
            Sh.class,
            "sh",
                (cmd)->{
                    if(cmd.isSilent() || !cmd.getPrompt().isEmpty()){
                        LinkedHashMap<Object,Object> map = new LinkedHashMap<>();
                        map.put("sh",cmd.getCommand());
                        if(cmd.isSilent()) {
                            map.put("silent", cmd.isSilent());
                        }
                        if(!cmd.getPrompt().isEmpty()){
                            map.put("prompt",cmd.getPrompt());
                        }
                        return map;
                    }else{
                        return cmd.getCommand();
                    }
                },
                (str)->{
                    if(str==null || str.isEmpty()){
                        throw new YAMLException("sh command cannot be empty");
                    }
                    return new Sh(str);
                },
                (json)->{
                    if(!json.has("command") || json.getString("command","").isEmpty()){
                        throw new YAMLException("sh requires a non-empty command ");
                    }
                    Sh sh = new Sh(json.getString("command"),json.getBoolean("silent",false));
                    if(json.has("prompt")){
                        json.getJson("prompt",new Json()).forEach((k,v)->{
                            sh.addPrompt(k.toString(),v.toString());
                        });
                    }
                    return sh;
                }
        );
        rtrn.addCmd(
            Signal.class,
            "signal",
                (cmd)->cmd.getName(),
                (str)->new Signal(str),
                null
        );

        rtrn.addCmd(
            Sleep.class,
            "sleep",
            (cmd)->cmd.getAmount(),
            (str)->new Sleep(str),
            null
        );
        rtrn.addCmd(
            Upload.class,
            "upload",
            (cmd)->(cmd.getPath()+" "+cmd.getDestination()),
            (str)->{
                List<String> split = CmdBuilder.split(str);
                if(split.size()==1){
                    return new Upload(split.get(0));
                }else if (split.size()==2){
                    return new Upload(split.get(0),split.get(1));
                }else{
                    throw new YAMLException("cannot create upload from "+str);
                }
            },
            (json)->new Upload(json.getString("path"),json.getString("destination",""))
        );
        rtrn.addCmd(
            WaitFor.class,
            "wait-for",
            (cmd)->cmd.getName(),
            (str)->new WaitFor(str),
            (json)->new WaitFor(json.getString("name"),json.getBoolean("silent",true))
        );
        rtrn.addCmd(
            XmlCmd.class,
            "xml",
            (cmd)->(cmd.getPath()+" "+cmd.getOperations()),
            (str)->new XmlCmd(str),
            (json)->{
                List<String> operations = new LinkedList<>();
                json.getJson("operations",new Json()).forEach(entry->{
                    operations.add(entry.toString());
                });
                return new XmlCmd(json.getString("path"),operations.toArray(new String[]{}));
            }
        );
        CmdMapping<Script> scriptCmdMapping = new CmdMapping<Script>("script",null){

            @Override
            public Map<Object, Object> getMap(Object o) {
                String name = ((Script)o).getName();
                Map<Object,Object> rtrn =  super.getMap(o);
                if(rtrn.containsKey("then")){
                    Object value = rtrn.get("then");
                    rtrn.remove("then");
                    rtrn.put(name,value);
                }
                return rtrn;
            }
        };
        rtrn.mapRepresenter.addMapping(Script.class,scriptCmdMapping);
        return rtrn;
    }

    private Yaml yaml;
    private OverloadConstructor constructor;
    private MapRepresenter mapRepresenter;
    private Map<String,Function<String, Cmd>> noArgs;

    private Parser(){
        constructor = new OverloadConstructor();
        constructor.setExactMatchOnly(false);
        mapRepresenter = new MapRepresenter();
        noArgs = new HashMap<>();
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setWidth(1024);
        dumperOptions.setIndent(2);
        yaml = new Yaml(constructor,mapRepresenter,dumperOptions);
        constructor.addConstruct(new Tag("cmd"), new DeferableConstruct() {
            @Override
            public Object construct(Node node) {
                if(node instanceof ScalarNode){
                    String value = ((ScalarNode)node).getValue();
                    if(noArgs.containsKey(value)){
                        return noArgs.get(value).apply("");
                    }
                }else{
                }
                return defer(node);
            }
        });
    }

    public String dump(Object o){
        return yaml.dump(o);
    }

    public <T extends Cmd> void addCmd(Class<T> clazz, CmdEncoder<T> encoder, TypeDescription description){
        mapRepresenter.addMapping(clazz,new CmdMapping<T>(description.getTag().getValue(),encoder));
        constructor.addTypeDescription(description);
    }
    public <T extends Cmd> void addCmd(Class<T> clazz, String tag, CmdEncoder<T> encoder, Function<String, Cmd> fromString,Function<Json,Cmd> fromJson,String...expectedKeys) {
        addCmd(clazz,tag,false,encoder,fromString,fromJson,expectedKeys);
    }
    public <T extends Cmd> void addCmd(Class<T> clazz, String tag, boolean noArg,CmdEncoder<T> encoder, Function<String, Cmd> fromString,Function<Json,Cmd> fromJson,String...expectedKeys){
        Tag nodeTag = new Tag(tag);
        Tag tagFullyQualified = new Tag(clazz);

        Construct construct = new CmdConstruct(tag,fromString,fromJson,expectedKeys);
        constructor.addConstruct(tagFullyQualified,construct);
        CmdMapping cmdMapping = new CmdMapping<T>(tag,encoder);
        if(noArg){
            this.noArgs.put(tag,fromString);
            mapRepresenter.addEncoding(clazz, (t)->{
                Map<Object,Object> map = cmdMapping.getMap(t);
                if(map!=null && map.size()==1){
                    Object key = map.entrySet().iterator().next().getKey();
                    Object value = map.entrySet().iterator().next().getValue();
                    if(value==null || value.toString().isEmpty()){
                        return tag;
                    }
                }
                return map;
            });
        }else {

        }
        mapRepresenter.addMapping(clazz, cmdMapping);
        constructor.addConstruct(nodeTag,construct);
        constructor.addTargetTag(nodeTag,tag);
        //constructor.addMapKeys(nodeTag,Sets.of(tag));
    }
    public void addValueType(String key,String valueTag){
        constructor.addValueTag(new Tag(valueTag),key);
    }
    public <T> void addMap(Class<T> clazz, String tag, DeferableConstruct construct, Mapping<T> mapping){
        Tag tagImpl = new Tag(tag);
        Tag tagFullyQualified = new Tag(clazz);
        mapRepresenter.addMapping(clazz,mapping);
        constructor.addConstruct(tagImpl,construct);
        constructor.addConstruct(tagFullyQualified,construct);
    }
    public <T> void addEncoding(Class<T> clazz, String tag, DeferableConstruct construct,Function<T,Object> encoder){
        Tag tagImpl = new Tag(tag);
        Tag tagFullyQualified = new Tag(clazz);
        mapRepresenter.addEncoding(clazz,encoder);
        constructor.addConstruct(tagImpl,construct);
        constructor.addConstruct(tagFullyQualified,construct);
    }
    public void addValuePattern(String tag,String pattern){
        constructor.addStringTag(new Tag(tag),pattern);
    }
    public void addMapping(String tag,Set<String> requiredKeys){
        constructor.addMapKeys(new Tag(tag),requiredKeys);
    }


    public YamlFile loadFile(String path){
        InputStream stream  = FileUtility.getInputStream(path);
        return loadFile(path,stream);
    }

    public YamlFile loadFile(String path, InputStream stream) {
        String content = new BufferedReader(new InputStreamReader(stream))
           .lines().collect(Collectors.joining("\n"));
        return loadFile(path,content);
    }
    public YamlFile loadFile(String path,String content){
        YamlFile loaded = null;
        try{
            loaded = yaml.loadAs(content,YamlFile.class);
        }catch(ParserException| ScannerException e){
            //e.printStackTrace();
            try {
                WamlParser wamlParser = new WamlParser();
                wamlParser.load(path, new ByteArrayInputStream(content.getBytes()));
                if(wamlParser.hasErrors()){
                    logger.error("Failed to load {} with waml parser \n{}",path,wamlParser.getErrors().stream().collect(Collectors.joining("\n")));
                }
                RunConfigBuilder runConfigBuilder = new RunConfigBuilder(CmdBuilder.getBuilder());
                runConfigBuilder.loadWaml(wamlParser);
                String newContent = yaml.dump(YamlFileConstruct.MAPPING.getMap(runConfigBuilder.toYamlFile()));
                try {
                    loaded = yaml.loadAs(newContent, YamlFile.class);
                }catch (Exception exception){
                    logger.error("Failed to load {} after waml transform\n{}\n{}",path,exception.getMessage(),newContent);
                    exception.printStackTrace();
                }
            }catch (WamlException wamlException){
                logger.error("Failed to load {} as waml\n{}",path,wamlException.getMessage());
                wamlException.printStackTrace();
            }
            if(loaded!=null){
                logger.warn("loaded {} as deprecated waml format, convert with -W jar argument",path);
            }
        }catch(RuntimeException e){
            logger.error("Failed to load {}\n{}",path,e.getMessage());
        }
        if(loaded!=null) {
            loaded.setPath(path);
        }
        return loaded;
    }

}
