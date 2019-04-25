package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;
import perf.yaup.StringUtil;
import perf.yaup.file.FileUtility;
import perf.yaup.xml.XmlOperation;
import perf.yaup.xml.pojo.Xml;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static perf.yaup.StringUtil.removeQuotes;



public class XmlCmd extends Cmd {

    public static final String SET_STATE_KEY = "->";

    String path;
    List<String> operations;
    //TODO calling removeQuotes in Cmd just exposes artifacts from yaml parsing, should be done in CmdBuilder
    public XmlCmd(String path){
        this(
            path.substring(0,path.indexOf(FileUtility.SEARCH_KEY)),

            path.substring(
                path.indexOf(FileUtility.SEARCH_KEY)+FileUtility.SEARCH_KEY.length()
            ).split("\n")
        );
    }
    public XmlCmd(String path,String...operations){
        this.path = removeQuotes(path);
        this.operations = Stream.of(operations).map(StringUtil::removeQuotes).collect(Collectors.toList());
    }

    public String getPath(){return path;}

    public List<String> getOperations(){return Collections.unmodifiableList(operations);}

    @Override
    public String toString(){
        return "xml: {path:"+path+", operations: "+operations+"}";
    }

    @Override
    public void run(String input, Context context) {
        Xml xml = null;
        boolean successful = true;
        String output = input;
        File tmpDest = null;
        try {
            String remotePath = Cmd.populateStateVariables(path,this,context.getState(),true);

            if(remotePath!=null){
                remotePath = removeQuotes(remotePath);

                if(!remotePath.isEmpty() && !remotePath.startsWith("/")){
                    String pwd = context.getSession().shSync("pwd");
                    remotePath = pwd+File.separator+path;
                }
            }
            if(remotePath==null || remotePath.isEmpty()){
                xml = Xml.parse(input);
            }else{
                tmpDest = File.createTempFile("cmd-"+this.getUid()+"-"+context.getSession().getHost().getHostName(),"."+System.currentTimeMillis());

                context.getLocal().download(remotePath,tmpDest.getPath(),context.getSession().getHost());
                xml = Xml.parseFile(tmpDest.getPath());
            }
            for(int i=0; i<operations.size(); i++){
                String operation = Cmd.populateStateVariables(operations.get(i),this,context.getState(),true);
                operation = removeQuotes(operation);
                XmlOperation xmlOperation = XmlOperation.parse(operation);
                if(XmlOperation.Operation.None.equals(xmlOperation.getOperation()) &&
                    xmlOperation.getPath().contains(SET_STATE_KEY) ){
                    String path = xmlOperation.getPath().substring(0,xmlOperation.getPath().indexOf(SET_STATE_KEY)).trim();
                    String stateValue = xmlOperation.getPath().substring(xmlOperation.getPath().indexOf(SET_STATE_KEY)+SET_STATE_KEY.length()).trim();

                    stateValue = Cmd.populateStateVariables(stateValue,this,context.getState());
                    xmlOperation = XmlOperation.parse(path);
                    String response = xml.apply(xmlOperation);
                    context.getState().set(stateValue,response);
                }else {
                    String response = xml.apply(xmlOperation);
                    if (!response.isEmpty()) {
                        output = response;
                    }
                }
            }
            if(remotePath!=null && !remotePath.isEmpty() && tmpDest!=null) {
                try (PrintWriter out = new PrintWriter(tmpDest)) {
                    out.print(xml.documentString());
                    out.flush();
                }
                context.getLocal().upload(tmpDest.getPath(), remotePath, context.getSession().getHost());
            }
        } catch (IOException e) {
            logger.error("{}@{} failed to create local tmp file",this.toString(),context.getSession().getHost().getHostName(),e);
            successful = false;
            output = "COULD NOT LOAD: "+path;
        } finally {
            if(successful){
                context.next(output);
            }else{
                context.skip(output);
            }
            if(tmpDest != null & tmpDest.exists()){
                tmpDest.delete();
            }

        }
    }

    @Override
    public Cmd copy() {
        return new XmlCmd(this.path,this.operations.toArray(new String[]{}));
    }

}
