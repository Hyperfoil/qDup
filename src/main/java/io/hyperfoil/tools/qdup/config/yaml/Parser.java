package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Script;
import io.hyperfoil.tools.qdup.cmd.impl.*;
import io.hyperfoil.tools.qdup.config.CmdBuilder;
import io.hyperfoil.tools.qdup.config.Role;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
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
import io.hyperfoil.tools.qdup.config.waml.WamlException;
import io.hyperfoil.tools.qdup.config.waml.WamlParser;
import io.hyperfoil.tools.yaup.file.FileUtility;
import io.hyperfoil.tools.yaup.json.Json;
import io.hyperfoil.tools.yaup.yaml.DeferableConstruct;
import io.hyperfoil.tools.yaup.yaml.MapRepresenter;
import io.hyperfoil.tools.yaup.yaml.Mapping;
import io.hyperfoil.tools.yaup.yaml.OverloadConstructor;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.util.*;
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
           AddPrompt.class,
           "add-prompt",
           (cmd)->cmd.getPrompt(),
           (str)->new AddPrompt(str),
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
           CtrlC.class,
           "ctrlZ",
           true,
           (cmd)->"",
           (str)->new CtrlZ(),
           (json)->new CtrlZ()
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
                if(split.size()<=1){
                    return new Download(str);
                }else if(split.size()==2){
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
        rtrn.addCmd(
            Exec.class,
            "exec",
            (cmd)->cmd.getCommand(),
            (str)->new Exec(str),
            (json)->new Exec(json.getString("command"),json.getBoolean("silent",false))
        );
        //ExitCode
        rtrn.addCmd(
            ForEach.class,
            "for-each",
            //have to quote declaredInput because CmdBuilder.split() strips out the quotes, remove once cmd builder is gone
            (cmd)->((cmd.getName()+" "+(cmd.getDeclaredInput().trim().isEmpty()?"":"'"+cmd.getDeclaredInput()).trim()+"'")),
            (str)->{

                List<String> split = CmdBuilder.split(str);
                if(split.size()<=1){
                    return new ForEach(split.get(0));
                }else if (split.size()==2){
                    return new ForEach(split.get(0),split.get(1));
                }else{
                   String name = split.get(0);
                   String remainder = str.substring(name.length());
                   if(Json.isJsonLike(remainder)){
                      return new ForEach(name,remainder);

                   } else {
                      throw new YAMLException("cannot create for-each from " + str + " splits " + split.size() + " " + Arrays.asList(split).stream().map(a -> "||" + a.toString() + "||").collect(Collectors.toList()));
                   }
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
        //TODO QueueDelete
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
           ReadSignal.class,
           "read-signal",
           (cmd)->cmd.getName(),
           (str)->new ReadSignal(str),
           null
        );
        rtrn.addCmd(
            ReadState.class,
        "read-state",
            (cmd)->cmd.getKey(),
            (str)->new ReadState(str),
            null
        );
        rtrn.addCmd( //this is here to help find errors in waml -> yaml conversion
           Cmd.NO_OP.class,
           "#NO_OP",
           (cmd)->"",
           (str)->new Cmd.NO_OP(),
           null
        );
        //TODO add Reboot to yaml support
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
           SetSignal.class,
           "set-signal",
           (cmd)->{
               if(cmd.isReset()){
                   LinkedHashMap<Object,Object> map = new LinkedHashMap<>();
                   LinkedHashMap<Object,Object> opts = new LinkedHashMap<>();
                   map.put("set-signal",opts);
                   opts.put("name",cmd.getName());
                   opts.put("initial",cmd.getInitial());
                   opts.put("reset",cmd.isReset());
                   return map;
               }else {
                  return cmd.getName() + " " + cmd.getInitial();
               }
           },
           (str)->{
               List<String> split = CmdBuilder.split(str);
               if(split.size()!=2){
                   throw new YAMLException("cannot create countdown from " + str);
               }else{
                   return new SetSignal(split.get(0),split.get(1));
               }
           },
           (json)->new SetSignal(json.getString("name"),json.getString("initial"))
        );
        rtrn.addCmd(
            SetState.class,
            "set-state",
            (cmd)->cmd.getKey()+(cmd.getValue()!=null && !cmd.getValue().isEmpty() ? " "+cmd.getValue() : ""),
            (str)->{
                List<String> split = CmdBuilder.split(str);
                if(split.size()<=1){
                    return new SetState(str);
                }else{
                    String name = split.get(0);
                    String remainder = str.substring(name.length()).trim();
                    return new SetState(name,remainder);
                }
            },
            (json)->new SetState(
               json.getString("key"),
               json.getString("value",null),
               json.getBoolean("silent",false)
            )
        );
        rtrn.addCmd(
            Sh.class,
            "sh",
                (cmd)->{
                    if(cmd.isSilent() || !cmd.getPrompt().isEmpty()){
                        LinkedHashMap<Object,Object> map = new LinkedHashMap<>();
                        LinkedHashMap<Object,Object> opts = new LinkedHashMap<>();
                        map.put("sh",opts);
                        opts.put("command",cmd.getCommand());
                        if(cmd.isSilent()) {
                            opts.put("silent", cmd.isSilent());
                        }
                        if(!cmd.getPrompt().isEmpty()){
                            opts.put("prompt",cmd.getPrompt());
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
                if(split.size()<=1){
                    return new Upload(str);
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
            (str)->{
                List<String> split = CmdBuilder.split(str);
                if(split.size()<=1){
                    return new WaitFor(str);
                }else{
                    String name = split.get(0);
                    String remainder = str.substring(name.length()).trim();
                    return new WaitFor(name,remainder);
                }
            },
            (json)->new WaitFor(json.getString("name"),json.getString("initial",null))
        );
        rtrn.addCmd(
            XmlCmd.class,
            "xml",
            (cmd)->{
                if(cmd.getOperations().isEmpty()){
                    return cmd.getPath();
                }else{
                    LinkedHashMap<Object,Object> map = new LinkedHashMap<>();
                    LinkedHashMap<Object,Object> opts = new LinkedHashMap<>();
                    map.put("xml",opts);
                    opts.put("path",cmd.getPath());
                    opts.put("operations",cmd.getOperations());
                    return map;
                }
            },
            (str)->{
                List<String> split = CmdBuilder.split(str);
                if(split.size()==1){
                    return new XmlCmd(str);
                }else {
                    String name = split.get(0);
                    String remainder = str.substring(name.length()).trim();
                    if(Json.isJsonLike(remainder)){
                        Json remainderJson = Json.fromJs(remainder);
                        if(remainderJson.isArray() && !remainderJson.isEmpty()){
                            split.clear();
                            remainderJson.forEach(v->split.add(v.toString()));
                        }else{
                            split.remove(0);
                        }
                    }else{
                        split.remove(0);
                    }
                    return new XmlCmd(name,split.toArray(new String[0]));
                }
            },
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
        }catch(YAMLException e){
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
            e.printStackTrace();
        }
        if(loaded!=null){
            loaded.setPath(path);
        }
        return loaded;
    }

}
