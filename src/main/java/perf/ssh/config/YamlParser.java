package perf.ssh.config;

import perf.parse.Eat;
import perf.parse.Exp;
import perf.parse.Merge;
import perf.parse.Parser;
import perf.parse.Rule;
import perf.parse.reader.TextLineReader;
import perf.ssh.Host;
import perf.ssh.RunConfig;
import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.Script;
import perf.util.json.Json;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class YamlParser {

    public static final String child = "child";
    public static final String key = "key";
    public static final String value = "value";
    public static final String comment = "comment";





    Parser parser;
    TextLineReader reader;
    Json json;


    public List<String> errors;
    public YamlParser(){
        errors = new LinkedList<>();
        json = new Json();
        parser = new Parser();
        //comments

        //the start of documents

        //the start of key / value
        String prefix="^\\s*,?\\s*";
        String separator="\\s*:\\s*";
        String suffix="";
        String normalKey = "(?<"+key+">[^,\\s\"][^,\\]]*[^\"\\s,\\]*])";
        String quoteKey = "\"(?<"+key+">[^\"]+)\"";
        String normalValue = "(?<"+value+">(?!\")[^,}]*[^\\n\\s,}])";//(?=\s*[,}])
        String quoteValue = "\"(?<"+value+">[^\"]+)\"";

        Exp keyed = new Exp("key","(?<"+key+">[^:#\\s]+)")
                .eat(Eat.Match)
                .add(new Exp("abvList","^\\s*:\\s*\\[")
                        .eat(Eat.Match)
                        .set(Rule.RepeatChildren)
                        .add(new Exp("abvListQuotedEntry","^\\s*,?\\s*\"(?<"+key+">[^\"]+)\"")
                                .group(child)
                                .set(Merge.Entry)
                                .eat(Eat.Match)
                        )
                        .add(new Exp("abvListEntry","^\\s*,?\\s*(?<"+key+">[^,\\]]*[^\\s,\\]])")
                                .group(child)
                                .set(Merge.Entry)
                                .eat(Eat.Match)
                        )
                )
                .add(new Exp("abvMap","^\\s*:\\s*\\{")
                        .eat(Eat.Match)
                        .set(Rule.RepeatChildren)
                        .add(new Exp("abvMapQQEntry",prefix+quoteKey+"\\s*:\\s*"+quoteValue+suffix)
                                .group(child)
                                .set(Merge.Entry)
                                .eat(Eat.Match)
                        )
                        .add(new Exp("abvMapQNEntry",prefix+quoteKey+separator+normalValue+suffix)
                                .group(child)
                                .set(Merge.Entry)
                                .eat(Eat.Match)
                        )
                        .add(new Exp("abvMapNQEntry",prefix+normalKey+separator+quoteValue+suffix)
                                .group(child)
                                .set(Merge.Entry)
                                .eat(Eat.Match)
                        )
                        .add(new Exp("abvMapNNEntry",prefix+normalKey+separator+normalValue+suffix)
                                .group(child)
                                .set(Merge.Entry)
                                .eat(Eat.Match)
                        )
                )
                .add(new Exp("value",":\\s*(?<"+value+">[^\\s\\n].*)"));
        parser.add( new Exp("comment","\\s*#(?<"+comment+">.*)") );
        parser.add( new Exp("newDoc","---").eat(Eat.Line) );
        parser.add(
            new Exp("spaces","^\\s+(?!-)").debug()
            .eat(Eat.Match)
            .add(keyed)
        );
        parser.add(
            new Exp("nest", "^(?<"+child+":nestLength>[\\s-]*)")
            .eat(Eat.Match)
            .add(keyed)
        );
        parser.add(new Exp("kv","\\s+"+"(?<"+key+">[^-:#\\s]+)\\s*:\\s*(?<"+value+">[^\\s\\n].*)").set(Merge.Entry));
        parser.add(yamJson->{json.add(yamJson);});

        reader = new TextLineReader();
        reader.addParser(parser);
    }

    public boolean hasErrors(){return !errors.isEmpty();}
    public List<String> getErrors(){return Collections.unmodifiableList(errors);}
    private void addError(String error){
        errors.add(error);
    }

    public void load(String yamlPath){
        try(InputStream stream = new FileInputStream(yamlPath)){
            load(yamlPath,new FileInputStream(yamlPath));
        } catch (FileNotFoundException e) {
            addError("could not find "+yamlPath);
        } catch (IOException e) {
            addError("failed to read "+yamlPath);
        }
    }
    public void load(String fileName,InputStream stream){
        reader.read(stream);
    }

    public Json getJson(){return json;}
    public void populateRunConfig(RunConfig config){

        Json json = getJson();
        for(Object key : json.keySet()){
            Object value = json.get(key);
            if(value instanceof Json){
                Json valueJson = (Json)value;
                if(valueJson.has(child) && valueJson.get(child) instanceof Json){
                    parseDocumentJson(valueJson.getJson(child),config);
                }

            }else{
                config.addError("expected json document but found: "+value);
            }
        }
    }
    private Host addHost(Json json,RunConfig config){
        Host rtrn = null;
        String name = "";
        if(json.has(key) && json.get(key) instanceof String){
            name = json.getString(key);
        }

        if(json.has(value) && json.get(value) instanceof String){

            String hostString = json.getString(value);

            rtrn = parseHostString(hostString);
            if(rtrn==null){
                config.addError("failed to parse host "+name+" = "+hostString);
            }
        }else if (json.has(child) && json.get(child) instanceof Json){
            rtrn = parseHostJson(json.getJson(child),config);
        }else{//assume we parse the host from name
            rtrn = parseHostString(name);
        }
        if(rtrn == null ){
            config.addError("failed to parse host from "+json);
        }else{
            if(name==null || name.isEmpty()){
                name = rtrn.toString();
            }
            config.addHost(name,rtrn);
        }
        return rtrn;
    }
    private Host parseHostJson(Json json,RunConfig config){
        Host rtrn = null;
        String username=null;
        String hostname = null;
        int port = Host.DEFAULT_PORT;
        if(json.isArray()){
            for(int i=0; i<json.size();i++){
                Object entry = json.get(i);
                if(entry instanceof Json){
                    Json entryJson = (Json)entry;
                    switch (entryJson.get(key).toString()){
                        case "username":
                            username = entryJson.getString(value);
                            break;
                        case "hostname":
                            hostname = entryJson.getString(value);
                            break;
                        case "port":
                            port = Integer.parseInt(entryJson.get(value).toString());
                            break;

                        default:
                            config.addError("unknown host configuration "+entryJson.get(key)+" for "+json);
                    }

                }else{
                    config.addError("could not host from: "+json);
                }
            }
        }
        if(username!=null && hostname!=null){
            rtrn = new Host(username,hostname,port);
        }
        if(username==null){
            config.addError("missing username for host "+json);
        }
        if(hostname==null){
            config.addError("missing hostname for host "+json);
        }
        return rtrn;
    }
    private Host parseHostString(String hostString){
        Host rtrn = null;
        if(hostString.contains("@")){
            String username = hostString.substring(0,hostString.indexOf("@"));
            String hostname = hostString.substring(hostString.indexOf("@")+1);
            int port = 22;
            if(hostname.contains(":")){
                port = Integer.parseInt(hostname.substring(hostname.indexOf(":")+1));
                hostname = hostname.substring(0,hostname.indexOf(":"));
            }
            rtrn = new Host(username,hostname,port);
        }else{

        }
        return rtrn;
    }
    protected void processScript(Json json,RunConfig config){
        String name = "";
        if(json.has(key) && json.get(key) instanceof String){
            name = json.getString(key);
        }
        if(json.has(value)){ // script shouldn't have a value

        }
        if(json.has(child) && json.get(child) instanceof Json){
            Json children = json.getJson(child);

        }
    }
    private Cmd parseCommandJson(Json json, RunConfig config){
        String name = json.getString(key);
        if(name==null || name.isEmpty()){
            config.addError("could not identify Command from: "+json);
            return Cmd.NO_OP();
        }
        if( json.has(value) ){
            String args = json.getString(value);

        }
        return null;
    }
    private Script parseScriptJson(Json json, RunConfig config){
        System.out.println("parseScriptJson "+json);
        String name = json.getString(key);

        if(name==null || name.isEmpty()){
            name = "run-"+System.currentTimeMillis();
        }


        Script rtrn = new Script(name);
        return rtrn;
    }
    private void parseDocumentJson(Json json, RunConfig config){
        if(json.isArray()){
            for(int i=0; i<json.size(); i++){
                Object entry = json.get(i);
                if(entry instanceof Json){
                    Json jsonEntry = (Json)entry;

                    String entryKey = jsonEntry.getString(key);
                    Object children = jsonEntry.get(child);
                    if(entryKey==null || entryKey.isEmpty()){
                        //config.addError("key should not be null for "+jsonEntry);
                        //could be a comment
                    }else{
                        switch (entryKey){
                            case "name":
                                if(value instanceof String) {
                                    config.setName(value.toString());
                                }else{
                                    config.addError("name should be a string but found: "+value);
                                }
                                break;
                            case "hosts":
                                if(children instanceof Json){
                                    Json hostList = (Json)children;
                                    if(hostList.isArray()){
                                        for(int s=0; s<hostList.size(); s++){
                                            Object hostEntry = hostList.get(s);
                                            if(hostEntry instanceof Json){
                                                Json hostJson = (Json)hostEntry;
                                                String name = hostJson.getString(key);
                                                if(name==null || name.isEmpty()){
                                                    config.addError("host is missing name "+hostJson);
                                                }else{
                                                    addHost(hostJson,config);
                                                }
                                            }else{
                                                config.addError("hosts should be name : hostConfig but found: "+hostEntry);
                                            }
                                        }
                                    }else{
                                        config.addError("hosts should be a list hostName : hostConfig but found: "+hostList);
                                    }
                                }else{
                                    config.addError("hosts should have a list of name : hostConfig but found: "+children);
                                }
                                break;
                            case "scripts":
                                if(children instanceof Json){
                                    Json scriptList = (Json)children;
                                    for(int s=0; s<scriptList.size(); s++){
                                        Object scriptEntry = scriptList.get(s);
                                        if(scriptEntry instanceof Json){
                                            Json scriptJson = (Json)scriptEntry;
                                            parseScriptJson(scriptJson,config);
                                        }else{
                                            config.addError("script should be name : scriptConfig but found: "+scriptEntry);
                                        }
                                    }
                                }else{
                                    config.addError("scripts should have a list of name : scriptConfig but found: "+children);
                                }
                                break;
                            case "state":
                                break;
                            case "roles":
                                break;
                            case child:
                                break;
                            default:
                                config.addError("unknown document section: "+entryKey+" "+jsonEntry);
                        }
                    }
                }
            }
        }else{
            config.addError("yaml document should parse to a json array"+json);
        }
    }
    public void reset(){
        json = new Json();
        parser.close();
    }
}
