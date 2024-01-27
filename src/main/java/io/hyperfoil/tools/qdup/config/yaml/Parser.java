package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.parse.ParseCommand;
import io.hyperfoil.tools.qdup.Globals;
import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.JsSnippet;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Script;
import io.hyperfoil.tools.qdup.cmd.impl.*;
import io.hyperfoil.tools.qdup.config.Role;
import io.hyperfoil.tools.qdup.config.converter.FileSizeConverter;
import io.hyperfoil.tools.yaup.StringUtil;
import io.hyperfoil.tools.yaup.json.JsonValidator;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Construct;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;
import io.hyperfoil.tools.yaup.file.FileUtility;
import io.hyperfoil.tools.yaup.json.Json;
import io.hyperfoil.tools.yaup.yaml.DeferableConstruct;
import io.hyperfoil.tools.yaup.yaml.MapRepresenter;
import io.hyperfoil.tools.yaup.yaml.Mapping;
import io.hyperfoil.tools.yaup.yaml.OverloadConstructor;
import org.yaml.snakeyaml.resolver.Resolver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Parser {

    final static XLogger logger = XLoggerFactory.getXLogger(Parser.class.getName());
//    final static Logger logger = LoggerFactory.getLogger(Parser.class.getName());


    private JsonValidator validator;
    public static Parser getInstance() {
        Parser rtrn = new Parser();
        RoleConstruct roleConstruct = new RoleConstruct();

        rtrn.addMap(YamlFile.class,
                "yamlFile",
                new YamlFileConstruct(roleConstruct),
                YamlFileConstruct.MAPPING
        );
        rtrn.addMap(Role.class,
                "role",
                roleConstruct,
                //TODO role encoding :)
                (role) -> new HashMap<>());
        rtrn.addValueType("roles", "roles");
        rtrn.addMap(State.class,
                "states",
                new StateConstruct(),
                (state) -> Json.toObjectMap(state.toJson())
        );
        rtrn.addValueType("states", "states");
        rtrn.addMap(Globals.class,
                "globals",
                new GlobalsConstruct(),
                (global) -> Json.toObjectMap(global.toJson())
        );
        rtrn.addValueType("globals", "globals");
        rtrn.addMap(JsSnippet.class,
                "javascript",
                new JsSnippetConstruct(),
                (function) -> Json.toObjectMap(function.toJson())
        );
        rtrn.addValueType("javascript", "javascript");
        rtrn.addEncoding(Host.class,
                "host",
                new HostDefinitionConstruct(),
                (host) -> host.toString()
        );
        rtrn.addValuePattern("host", Host.HOST_PATTERN);

        rtrn.addCmd(
                Abort.class,
                "abort",
                (cmd) -> cmd.getMessage(),
                (str,prefix,suffix) -> new Abort(str),
                (json)->new Abort(json.getString("message"),json.getBoolean("skip-cleanup",false)),
                "message","skip-cleanup"
        );
        rtrn.addCmd(
                AddPrompt.class,
                "add-prompt",
                (cmd) -> cmd.isShell() ? Map.of("prompt",cmd.getPrompt(),HostDefinition.IS_SHELL,cmd.isShell()) :  cmd.getPrompt(),
                (str,prefix,suffix) -> new AddPrompt(str),
                (json)->new AddPrompt(json.getString("prompt"),json.getBoolean(HostDefinition.IS_SHELL,false)),
                "prompt",HostDefinition.IS_SHELL
        );
        rtrn.addCmd(
                Countdown.class,
                "countdown",
                (cmd) -> (cmd.getName() + " " + cmd.getInitial()),

                (str,prefix,suffix) -> {
                    List<String> split = Parser.split(str,prefix,suffix);
                    if (split.size() == 2) {
                        return new Countdown(split.get(0), Integer.parseInt(split.get(1)));
                    } else {
                        throw new YAMLException("cannot create countdown from " + str);
                    }
                },
                (json) -> new Countdown(json.getString("name"), (int) json.getLong("initial")),
                "path","initial"

        );

        rtrn.addCmd(
                CtrlC.class,
                "ctrlC",
                true,
                (cmd) -> "",
                (str,prefix,suffix) -> new CtrlC(),
                (json) -> new CtrlC()
        );
        rtrn.addCmd(
                CtrlSlash.class,
                "ctrl/",
                true,
                (cmd) -> "",
                (str,prefix,suffix) -> new CtrlSlash(),
                (json) -> new CtrlSlash()
        );
        rtrn.addCmd(
                CtrlBackSlash.class,
                "ctrl\\",
                true,
                (cmd) -> "",
                (str,prefix,suffix) -> new CtrlBackSlash(),
                (json) -> new CtrlBackSlash()
        );
        rtrn.addCmd(
                CtrlU.class,
                "ctrlU",
                true,
                (cmd) -> "",
                (str,prefix,suffix) -> new CtrlU(),
                (json) -> new CtrlU()
        );
        rtrn.addCmd(
                CtrlZ.class,
                "ctrlZ",
                true,
                (cmd) -> "",
                (str,prefix,suffix) -> new CtrlZ(),
                (json) -> new CtrlZ()
        );
        rtrn.addCmd(
                Done.class,
                "done",
                true,
                (cmd) -> "",
                (str,prefix,suffix) -> new Done(),
                (json) -> new Done()
        );
        rtrn.addCmd(
                Download.class,
                "download",
                (cmd) -> (cmd.getPath() + (cmd.getDestination() != null && !cmd.getDestination().isEmpty() ? " " + cmd.getDestination() : "")),
                (str,prefix,suffix) -> {
                    List<String> split = Parser.split(str,prefix,suffix);
                    if (split.size() <= 1) {
                        return new Download(str);
                    } else if (split.size() == 2) {
                        return new Download(split.get(0), split.get(1));
                    } else if (split.size() == 3) {
                        return new Download(split.get(0), split.get(1), FileSizeConverter.toBytes(split.get(2)));
                    } else {
                        throw new YAMLException("cannot create download from " + str);
                    }
                },
                (json) -> new Download(json.getString("path"), json.getString("destination", ""), FileSizeConverter.toBytes(json.getString("max-size", null))),
                "path","destination","max-size"

        );
        rtrn.addCmd(
                Echo.class,
                "echo",
                true,
                (cmd) -> "",
                (str,prefix,suffix) -> new Echo(),
                null
        );
        //Exec
        rtrn.addCmd(
                Exec.class,
                "exec",
                (cmd) -> cmd.getCommand(),
                (str,prefix,suffix) -> new Exec(str),
                (json) -> {
                    return new Exec(json.getString("command"), json.getBoolean("async", false), json.getBoolean("silent", false));
                },
                "command","async","silent"
        );
        //ExitCode
        rtrn.addCmd(
                ForEach.class,
                "for-each",
                //have to quote declaredInput because Parser.split() strips out the quotes, remove once cmd builder is gone
                (cmd) -> ((cmd.getName() + " " + (cmd.getDeclaredInput().trim().isEmpty() ? "" : "'" + cmd.getDeclaredInput()).trim() + "'")),
                (str,prefix,suffix) -> {
                    List<String> split = Parser.split(str,prefix,suffix);
                    if (str.isBlank() || split.size() < 1){
                        throw new YAMLException("cannot create for-each without arguments");
                    }else if (split.size() == 1) {
                        return new ForEach(split.get(0));
                    } else if (split.size() == 2) {
                        return new ForEach(split.get(0), split.get(1));
                    } else {
                        String name = split.get(0);
                        String remainder = str.substring(name.length());
                        if (Json.isJsonLike(remainder)) {
                            return new ForEach(name, remainder);

                        } else {
                            throw new YAMLException("cannot create for-each from " + str + " splits " + split.size() + " " + Arrays.asList(split).stream().map(a -> "||" + a.toString() + "||").collect(Collectors.toList()));
                        }
                    }
                },
                (json) -> {
                    return new ForEach(json.getString("name"), json.getString("input", ""));
                },
                "name","input"
        );
        //Invoke
        rtrn.addCmd(
                JsCmd.class,
                "js",
                new CmdWithElseMapping(
                        "js",
                        new CmdEncoder() {
                            @Override
                            public Object encode(Cmd cmd) {
                                if(cmd instanceof JsCmd){
                                    JsCmd r = (JsCmd)cmd;
                                    return r.getCode();
//                                    if(r.isMiss()){
//                                        Map<Object,Object> regexMap = new HashMap<>();
//                                        regexMap.put("miss",r.isMiss());
//                                        regexMap.put("pattern",r.getPattern());
//                                        regexMap.put("autoConvert",r.isAutoConvert());
//                                        return regexMap;
//                                    }else if (!r.isAutoConvert()) {
//                                        Map<Object,Object> regexMap = new HashMap<>();
//                                        regexMap.put("pattern",r.getPattern());
//                                        regexMap.put("autoConvert",r.isAutoConvert());
//                                        return regexMap;
//                                    }else{
//                                        return r.getCode();
//                                    }
                                }else{
                                    return "";
                                }
                            }
                        }
                ),
                new CmdWithElseConstruct(
                        "js",
                        (str,prefix,suffix) -> new JsCmd(str),
                        (json) -> { return new JsCmd(json.getString("code",""));}
                )
        );
        rtrn.addCmd(
                Log.class,
                "log",
                (cmd) -> cmd.getMessage(),
                (str,prefix,suffix) -> new Log(str),
                null
        );
        //TODO QueueDelete
        rtrn.addCmd(
                QueueDownload.class,
                "queue-download",
                (cmd) -> (cmd.getPath() + (cmd.getDestination() != null && !cmd.getDestination().isEmpty() ? " " + cmd.getDestination() : "")),
                (str,prefix,suffix) -> {
                    List<String> split = Parser.split(str,prefix,suffix);
                    if (split.size() == 1) {
                        return new QueueDownload(split.get(0));
                    } else if (split.size() == 2) {
                        return new QueueDownload(split.get(0), split.get(1));
                    } else if (split.size() == 3) {
                        return new QueueDownload(split.get(0), split.get(1), FileSizeConverter.toBytes(split.get(2)));
                    } else {
                        throw new YAMLException("cannot create queue-download from " + str);
                    }
                },
                (json) -> {
                    return new QueueDownload(json.getString("path"), json.getString("destination", ""), FileSizeConverter.toBytes(json.getString("max-size", null)));
                },
                "path","destination","max-size"
        );
        rtrn.addCmd(
                JsonCmd.class,
                "json",
                new CmdWithElseMapping(
                        "json",
                        new CmdEncoder() {
                            @Override
                            public Object encode(Cmd cmd) {
                                if (cmd instanceof JsonCmd) {
                                    JsonCmd r = (JsonCmd) cmd;
                                    if (r.hasElse()) {
                                        Map<Object, Object> map = new HashMap<>();
                                        map.put("json", r.getPath());
                                        return map;
                                    } else {
                                        return r.getPath();
                                    }
                                } else {
                                    return null;
                                }
                            }
                        }
                ),
                new CmdWithElseConstruct(
                        "json",
                        (str,prefix,suffix) -> new JsonCmd(str),
                        (json) -> {
                            return new JsonCmd(json.getString("path"));
                        }
                )
        );
        rtrn.addCmd(
                ReadSignal.class,
                "read-signal",
                new CmdWithElseMapping(
                        "read-signal",
                        new CmdEncoder() {
                            @Override
                            public Object encode(Cmd cmd) {
                                if (cmd instanceof ReadSignal) {
                                    ReadSignal r = (ReadSignal) cmd;
                                    if (r.hasElse()) {
                                        Map<Object, Object> map = new HashMap<>();
                                        map.put("read-signal", r.getName());
                                        return map;
                                    } else {
                                        return r.getName();
                                    }
                                } else {
                                    return null;
                                }
                            }
                        }
                ),
                new CmdWithElseConstruct(
                        "read-signal",
                        (str,prefix,suffix) -> new ReadSignal(str),
                        (json) -> {
                            return new ReadSignal(json.getString("name"));
                        }
                )
        );
        rtrn.addCmd(
                ReadState.class,
                "read-state",
                new CmdWithElseMapping(
                        "read-state",
                        new CmdEncoder() {
                            @Override
                            public Object encode(Cmd cmd) {
                                if (cmd instanceof ReadState) {
                                    ReadState r = (ReadState) cmd;
                                    if (r.hasElse()) {
                                        Map<Object, Object> map = new HashMap<>();
                                        map.put("read-state", r.getKey());
                                        return map;
                                    } else {
                                        return r.getKey();
                                    }
                                } else {
                                    return null;
                                }
                            }
                        }
                ),
                new CmdWithElseConstruct(
                        "read-state",
                        (str,prefix,suffix) -> new ReadState(str),
                        (json) -> new ReadState(json.getString("name"))
                )
        );
        rtrn.addCmd( //this is here to help find errors in waml -> yaml conversion
                Cmd.NO_OP.class,
                "#NO_OP",
                (cmd) -> "",
                (str,prefix,suffix) -> new Cmd.NO_OP(),
                null
        );
        //TODO add Reboot to yaml support
        rtrn.addCmd(
                ParseCmd.class,
                "parse",
                (cmd) -> cmd.getConfig(),
                (str,prefix,suffix) -> {
                    return new ParseCmd(str);
                },
                (json) -> {
                    return new ParseCmd(json.toString(0));
                }
        );
        rtrn.addCmd(
                Regex.class,
                "regex",
                new CmdWithElseMapping(
                        "regex",
                        new CmdEncoder() {
                            @Override
                            public Object encode(Cmd cmd) {
                                if(cmd instanceof Regex){
                                    Regex r = (Regex)cmd;
                                    if(r.isMiss()){
                                        Map<Object,Object> regexMap = new HashMap<>();
                                        regexMap.put("miss",r.isMiss());
                                        regexMap.put("pattern",r.getPattern());
                                        regexMap.put("autoConvert",r.isAutoConvert());
                                        return regexMap;
                                    }else if (!r.isAutoConvert()) {
                                        Map<Object,Object> regexMap = new HashMap<>();
                                        regexMap.put("pattern",r.getPattern());
                                        regexMap.put("autoConvert",r.isAutoConvert());
                                        return regexMap;
                                    }else{
                                        return r.getPattern();
                                    }
                                }else{
                                    return null;
                                }
                            }
                        }
                ),
                new CmdWithElseConstruct(
                        "regex",
                        (str,prefix,suffix) -> new Regex(str),
                        (json) -> new Regex(
                                json.getString("pattern","")
                                ,json.getBoolean("miss",false)
                                ,json.getBoolean("autoConvert",false)),
                        "pattern","miss","autoConvert"
                )
        );
        rtrn.addCmd(
                RepeatUntilSignal.class,
                "repeat-until",
                (cmd) -> cmd.getName(),
                (str,prefix,suffix) -> new RepeatUntilSignal(str),
                null
        );
        rtrn.addCmd(
                ScriptCmd.class,
                "script",
                (cmd) -> cmd.getName(),
                (str,prefix,suffix) -> new ScriptCmd(str),
                (json) -> new ScriptCmd(json.getString("name"), json.getBoolean("async", false), false)
                ,"name","async"
        );
        rtrn.addCmd(
                SendText.class,
                "send-text",
                (cmd) -> cmd.getText(),
                (str,prefix,suffix) -> new SendText(str),
                null
        );
        rtrn.addCmd(
                SetSignal.class,
                "set-signal",
                (cmd) -> {
                    if (cmd.isReset()) {
                        LinkedHashMap<Object, Object> map = new LinkedHashMap<>();
                        LinkedHashMap<Object, Object> opts = new LinkedHashMap<>();
                        map.put("set-signal", opts);
                        opts.put("name", cmd.getName());
                        opts.put("count", cmd.getInitial());
                        opts.put("reset", cmd.isReset());
                        return map;
                    } else {
                        return cmd.getName() + " " + cmd.getInitial();
                    }
                },
                (str,prefix,suffix) -> {
                    List<String> split = Parser.split(str,prefix,suffix);
                    if (split.size() != 2) {
                        throw new YAMLException("cannot create set-signal from " + str);
                    } else {
                        return new SetSignal(split.get(0), split.get(1));
                    }
                },
                (json) -> new SetSignal(json.getString("name"), json.getString("count"), json.getBoolean("reset", false))
                ,"name","count","reset"
        );
        rtrn.addCmd(
                SetState.class,
                "set-state",
                (cmd) -> {
                    return cmd.getKey() + (cmd.getValue() != null && !cmd.getValue().isEmpty() ? " " + cmd.getValue() : "");
                },
                (str,prefix,suffix) -> {
                    List<String> split = Parser.split(str,prefix,suffix);
                    if (split.size() <= 1) {
                        return new SetState(str);
                    } else {
                        String name = split.get(0);
                        String remainder = str.substring(name.length()).trim();
                        return new SetState(name, remainder);
                    }
                },
                (json) -> new SetState(
                    json.getString("key"),
                    json.getString("value", null),
                    json.getString("separator", StringUtil.PATTERN_DEFAULT_SEPARATOR),
                    json.getBoolean("silent", false),
                    json.getBoolean("autoConvert", true)
                ),
                "key","value","separator","silent","autoConvert"
        );
        rtrn.addCmd(
                Sh.class,
                "sh",
                (cmd) -> {
                    if (cmd.isSilent() || !cmd.getPrompt().isEmpty()) {
                        LinkedHashMap<Object, Object> map = new LinkedHashMap<>();
                        LinkedHashMap<Object, Object> opts = new LinkedHashMap<>();
                        map.put("sh", opts);
                        opts.put("command", cmd.getCommand());
                        if (cmd.isSilent()) {
                            opts.put("silent", cmd.isSilent());
                        }
                        if (!cmd.getPrompt().isEmpty()) {
                            opts.put("prompt", cmd.getPrompt());
                        }
                        if (cmd.hasIgnoreExitCode()) {
                            opts.put("ignore-exit-code",cmd.getIgnoreExitCode());
                        }
                        return map;
                    } else {
                        return cmd.getCommand();
                    }
                },
                (str,prefix,suffix) -> {
                    if (str == null || str.isEmpty()) {
                        throw new YAMLException("sh command cannot be empty");
                    }
                    Sh newCommand = new Sh(str);
                    if(rtrn.isAbortOnExitCode()){
                        newCommand.setIgnoreExitCode(Boolean.FALSE.toString());
                    }
                    return newCommand;
                },
                (json) -> {
                    if (!json.has("command") || json.getString("command", "").isEmpty()) {
                        throw new YAMLException("sh requires a non-empty command ");
                    }
                    Sh sh = new Sh(json.getString("command"), json.getBoolean("silent", false));
                    if(json.has("ignore-exit-code")) {
                        sh.setIgnoreExitCode(json.getString("ignore-exit-code",""));
                    }else if (rtrn.isAbortOnExitCode()) {
                        sh.setIgnoreExitCode(Boolean.FALSE.toString());
                    }
                    if (json.has("prompt")) {
                        json.getJson("prompt", new Json()).forEach((k, v) -> sh.addPrompt(k.toString(), v.toString()));
                    }
                    return sh;
                },
                "command","prompt","ignore-exit-code","silent"
        );
        rtrn.addCmd(
                Signal.class,
                "signal",
                (cmd) -> cmd.getName(),
                (str,prefix,suffix) -> new Signal(str),
                null
        );

        rtrn.addCmd(
                Sleep.class,
                "sleep",
                (cmd) -> cmd.getAmount(),
                (str,prefix,suffix) -> new Sleep(str),
                null
        );
        rtrn.addCmd(
                Upload.class,
                "upload",
                (cmd) -> (cmd.getPath() + " " + cmd.getDestination()),
                (str,prefix,suffix) -> {
                    List<String> split = Parser.split(str,prefix,suffix);
                    if (split.size() <= 1) {
                        return new Upload(str);
                    } else if (split.size() == 2) {
                        return new Upload(split.get(0), split.get(1));
                    } else {
                        throw new YAMLException("cannot create upload from " + str);
                    }
                },
                (json) -> new Upload(json.getString("path"), json.getString("destination", ""))
                ,"path","destination"
        );

        rtrn.addCmd(
                WaitFor.class,
                "wait-for",
                (cmd) -> cmd.getName(),
                (str,prefix,suffix) -> {
                    List<String> split = Parser.split(str,prefix,suffix);
                    if (split.size() <= 1) {
                        return new WaitFor(str);
                    } else {
                        String name = split.get(0);
                        String remainder = str.substring(name.length()).trim();
                        return new WaitFor(name, remainder);
                    }
                },
                (json) -> new WaitFor(json.getString("name"), json.getString("initial", null))
                ,"name","initial"
        );
        rtrn.addCmd(
                XmlCmd.class,
                "xml",
                (cmd) -> {
                    if (cmd.getOperations().isEmpty()) {
                        return cmd.getPath();
                    } else {
                        LinkedHashMap<Object, Object> map = new LinkedHashMap<>();
                        LinkedHashMap<Object, Object> opts = new LinkedHashMap<>();
                        map.put("xml", opts);
                        opts.put("path", cmd.getPath());
                        opts.put("operations", cmd.getOperations());
                        return map;
                    }
                },
                (str,prefix,suffix) -> new XmlCmd(str),
                (json) -> {
                    List<String> operations = new LinkedList<>();
                    json.getJson("operations", new Json()).forEach(entry -> {
                        operations.add(entry.toString());
                    });
                    return new XmlCmd(json.getString("path"), operations.toArray(new String[]{}));
                },
                "operations","path"
        );
        CmdMapping<Script> scriptCmdMapping = new CmdMapping<Script>("script", null) {

            @Override
            public Map<Object, Object> getMap(Object o) {
                String name = ((Script) o).getName();
                Map<Object, Object> rtrn = super.getMap(o);
                if (rtrn.containsKey("then")) {
                    Object value = rtrn.get("then");
                    rtrn.remove("then");
                    rtrn.put(name, value);
                }
                return rtrn;
            }
        };
        rtrn.mapRepresenter.addMapping(Script.class, scriptCmdMapping);
        return rtrn;
    }

    private Yaml yaml;
    private OverloadConstructor constructor;
    private MapRepresenter mapRepresenter;
    private Map<String, FromString> noArgs;
    private Map<Class, CmdMapping> cmdMappings;
    private boolean abortOnExitCode;

    private Parser() {
        constructor = new OverloadConstructor(){

            @Override
            public Object constructObject(Node node){
                return super.constructObject(node);
            }
        };
        constructor.setExactMatchOnly(false);
        mapRepresenter = new MapRepresenter();
        cmdMappings = new HashMap<>();
        noArgs = new HashMap<>();
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setWidth(1024);
        dumperOptions.setIndent(2);
        Resolver resolver = new Resolver(){

            @Override
            public Tag resolve(NodeId kind, String value, boolean implicit) {
                Tag resolved = super.resolve(kind,value,implicit);
                return resolved;
            }
        };

        resolver.addImplicitResolver(new Tag("pattern"), Pattern.compile("\\$"),"$",Integer.MAX_VALUE);
        yaml = new Yaml(constructor, mapRepresenter, dumperOptions,resolver);
//        constructor.addTypeDescription(new TypeDescription(
//                Host.class,
//                new Tag("local")
//        ));
        constructor.addConstruct(new Tag("local"),new HostDefinitionConstruct(){
            @Override
            public Object construct(Node node){
                return new Host("","",null,22,null,true,"podman",null);
            }
        });
        constructor.addConstruct(new Tag("pattern"), new DeferableConstruct() {
            @Override
            public Object construct(Node node) {
                return defer(node);
            }
        });
        constructor.addConstruct(new Tag("cmd"), new DeferableConstruct() {
            @Override
            public Object construct(Node node) {
                if (node instanceof ScalarNode) {
                    String value = ((ScalarNode) node).getValue();
                    if (noArgs.containsKey(value)) {
                        return noArgs.get(value).apply("",StringUtil.PATTERN_PREFIX,StringUtil.PATTERN_SUFFIX);
                    }
                } else {
                    //TODO cmd !=ScalarNode
                }
                return defer(node);
            }
        });
        abortOnExitCode = false;

        try (InputStreamReader fileStream = new InputStreamReader(ParseCommand.class.getClassLoader().getResourceAsStream("schema.json"));
             BufferedReader reader = new BufferedReader(fileStream))
        {
            String content = reader.lines().collect(Collectors.joining("\n"));
            validator = new JsonValidator(Json.fromString(content));
        } catch (IOException e) {
            logger.error("failed to create json validator",e);
            validator = new JsonValidator(new Json(false));
        }

    }

    public boolean isAbortOnExitCode(){return abortOnExitCode;}
    public void setAbortOnExitCode(boolean abortOnExitCode){
        this.abortOnExitCode = abortOnExitCode;
    }

    public Object representCommand(Cmd cmd) {
        return cmdMappings.containsKey(cmd.getClass()) ? cmdMappings.get(cmd.getClass()).getEncoder().encode(cmd) : "";
    }

    public String dump(Object o) {
        return yaml.dump(o);
    }

    public MapRepresenter getMapRepresenter() {
        return mapRepresenter;
    }

    public <T extends Cmd> void addCmd(Class<T> clazz, CmdEncoder<T> encoder, TypeDescription description) {
        CmdMapping cmdMapping = new CmdMapping<T>(description.getTag().getValue(), encoder);
        cmdMappings.put(clazz, cmdMapping);
        mapRepresenter.addMapping(clazz, cmdMapping);
        constructor.addTypeDescription(description);

    }

    public <T extends Cmd> void addCmd(Class<T> clazz, String tag, CmdEncoder<T> encoder, FromString<T> fromString, Function<Json, Cmd> fromJson, String... expectedKeys) {
        addCmd(clazz, tag, false, encoder, fromString, fromJson, expectedKeys);
    }

    public <T extends Cmd> void addCmd(Class<T> clazz, String tag, boolean noArg, CmdEncoder<T> encoder, FromString<T> fromString, Function<Json, Cmd> fromJson, String... expectedKeys) {
        Construct construct = new CmdConstruct(tag, fromString, fromJson, expectedKeys);
        CmdMapping cmdMapping = new CmdMapping<T>(tag, encoder);

        if (noArg) {
            this.noArgs.put(tag, fromString);
            mapRepresenter.addEncoding(clazz, (t) -> {
                Map<Object, Object> map = cmdMapping.getMap(t);
                if (map != null && map.size() == 1) {
                    Object key = map.entrySet().iterator().next().getKey();
                    Object value = map.entrySet().iterator().next().getValue();
                    if (value == null || value.toString().isEmpty()) {
                        return tag;
                    }
                }
                return map;
            });
        } else {

        }
        addCmd(clazz, tag, cmdMapping, construct);
    }

    public <T extends Cmd> void addCmd(Class<T> clazz, String tag, CmdMapping cmdMapping, Construct construct) {
        Tag nodeTag = new Tag(tag);
        Tag tagFullyQualified = new Tag(clazz);

        constructor.addConstruct(tagFullyQualified, construct);
        cmdMappings.put(clazz, cmdMapping);
        mapRepresenter.addMapping(clazz, cmdMapping);
        constructor.addConstruct(nodeTag, construct);
        constructor.addTargetTag(nodeTag, tag);
    }

    public void addValueType(String key, String valueTag) {
        constructor.addValueTag(new Tag(valueTag), key);
    }

    public <T> void addMap(Class<T> clazz, String tag, DeferableConstruct construct, Mapping<T> mapping) {
        Tag tagImpl = new Tag(tag);
        Tag tagFullyQualified = new Tag(clazz);
        mapRepresenter.addMapping(clazz, mapping);
        constructor.addConstruct(tagImpl, construct);
        constructor.addConstruct(tagFullyQualified, construct);
    }

    public <T> void addEncoding(Class<T> clazz, String tag, DeferableConstruct construct, Function<T, Object> encoder) {
        Tag tagImpl = new Tag(tag);
        Tag tagFullyQualified = new Tag(clazz);
        mapRepresenter.addEncoding(clazz, encoder);
        constructor.addConstruct(tagImpl, construct);
        constructor.addConstruct(tagFullyQualified, construct);
    }

    public void addValuePattern(String tag, String pattern) {
        constructor.addStringTag(new Tag(tag), pattern);
    }

    public void addMapping(String tag, Set<String> requiredKeys) {
        constructor.addMapKeys(new Tag(tag), requiredKeys);
    }


    public YamlFile loadFile(String path) {
        InputStream stream = FileUtility.getInputStream(path);
        return loadFile(path, stream);
    }

    public YamlFile loadFile(String path, InputStream stream) {
        String content = new BufferedReader(new InputStreamReader(stream))
                .lines().collect(Collectors.joining("\n"));
        return loadFile(path, content);
    }

    public YamlFile loadFile(String path, String content) {
        YamlFile loaded = null;
        try {
            loaded = yaml.loadAs(content, YamlFile.class);
        } catch (YAMLException e) {
            logger.error("Failed to load {} as yaml\n{}", path, e.getMessage());
        } catch (RuntimeException e) {
            logger.error("Failed to load {}\n{}", path, e.getMessage());
        }
        if (loaded != null) {
            loaded.setPath(path);
        }
        return loaded;
    }
    //Moved from CmdBuilder because removing waml code
    //TODO split should not split ${{...}}
    public static List<String> split(String input){
        return split(input,StringUtil.PATTERN_PREFIX,StringUtil.PATTERN_SUFFIX);
    }
    public static List<String> split(String input, String prefix, String suffix){

        List<String> rtrn = new LinkedList<>();
        int start=0;
        int current=0;
        int stateDepth=0;
        boolean quoted = false;
        boolean pop = false;
        char quoteChar = '"';
        while(current<input.length()){
            switch (input.charAt(current)){
                case '\'':
                case '"':
                    if(!quoted){
                        quoted=true;
                        quoteChar = input.charAt(current);
                        if(current>start){
                            pop=true;
                        }else if (current==start){
                            start++;
                        }
                    } else {
                        if (quoteChar == input.charAt(current)) {
                            if ('\\' == input.charAt(current - 1)) {

                            } else {
                                quoted = false;
                                if (current > start && stateDepth == 0) {
                                    pop = true;
                                }
                            }
                        }else{
                            //this characters was not what started the quote so just in the quote
                        }
                    }

                    break;
                case ' ':
                case '\t':
                    if(!quoted){
                        if(current>start && stateDepth == 0){
                            pop=true;
                        }
                    }
                default:
                    if(input.startsWith(prefix,current)){
                        stateDepth++;
                        current+=prefix.length()-1;
                    }
                    if(input.startsWith(suffix,current)){
                        stateDepth--;
                        current+=suffix.length()-1;
                    }
            }
            if(pop){
                String arg = input.substring(start,current);
                if(arg.startsWith("\"")){
                    arg = arg.substring(1);
                }
                //don't need to check for tailing " because current is not yet incremented


                start = current+1;
                //drop spaces if not already at end
                if(current+1<input.length()) {
                    int drop = current + 1;
                    while (drop+1 < input.length() && (input.charAt(drop) == ' ' || input.charAt(drop) == '\t') ) {
                        drop++;
                    }
                    start = drop;
                }
                rtrn.add(arg);
                pop=false;
            }

            current++;

        }

        if(start<current){
            rtrn.add(input.substring(start,current));
        }
        return rtrn;
    }

}
