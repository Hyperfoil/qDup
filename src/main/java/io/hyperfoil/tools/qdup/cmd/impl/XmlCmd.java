package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.yaup.StringUtil;
import io.hyperfoil.tools.yaup.file.FileUtility;
import io.hyperfoil.tools.yaup.xml.XmlOperation;
import io.hyperfoil.tools.yaup.xml.pojo.Xml;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.hyperfoil.tools.yaup.StringUtil.removeQuotes;



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
        String output = "";
        File tmpDest = null;
        try {
            String remotePath = Cmd.populateStateVariables(path,this,context);

            if(remotePath!=null){
                remotePath = removeQuotes(remotePath);

                if(!remotePath.isEmpty() && !remotePath.startsWith("/")){
                    String pwd = context.getShell().shSync("pwd");
                    if(path.startsWith("./")){
                        path = path.substring(2);//remove the ./
                    }
                    remotePath = pwd+File.separator+path;
                }
            }
            if(remotePath==null || remotePath.isEmpty()){
                xml = Xml.parse(input);
            }else{
                tmpDest = File.createTempFile("cmd-"+this.getUid()+"-"+context.getShell().getHost().getHostName(),"."+System.currentTimeMillis());

                boolean ok = context.getLocal().download(remotePath,tmpDest.getPath(),context.getShell().getHost());
                if(!ok){
                    successful=false;
                    context.error("failed to fetch xml file "+remotePath);
                    context.abort(false);
                }
                xml = Xml.parseFile(tmpDest.getPath());
            }
            for(int i=0; i<operations.size(); i++){
                String operation = Cmd.populateStateVariables(operations.get(i),this,context);
                operation = removeQuotes(operation);
                XmlOperation xmlOperation = XmlOperation.parse(operation);
                if( XmlOperation.Operation.None.equals(xmlOperation.getOperation()) ){
                    if (xmlOperation.getPath().contains(SET_STATE_KEY)) {

                        String path = xmlOperation.getPath().substring(0, xmlOperation.getPath().indexOf(SET_STATE_KEY)).trim();
                        String stateValue = xmlOperation.getPath().substring(xmlOperation.getPath().indexOf(SET_STATE_KEY) + SET_STATE_KEY.length()).trim();

                        stateValue = Cmd.populateStateVariables(stateValue, this, context);
                        xmlOperation = XmlOperation.parse(path);

                        List<Xml> found = xml.getAll(path);
                        String response = xml.apply(xmlOperation);
                        context.getState().set(stateValue, response);
                    } else {
                        List<Xml> found = xml.getAll(xmlOperation.getPath());

                        String response = xml.apply(xmlOperation);
                        if (!response.isEmpty()) {
                            output = response;
                        }
                    }
                } else {
                    String response = xml.apply(xmlOperation);
                    if (!response.isEmpty()) {
                        output = output+System.lineSeparator()+response;
                    }else{
                        
                    }
                }
            }
            if(remotePath!=null && !remotePath.isEmpty() && tmpDest!=null) {
                try (PrintWriter out = new PrintWriter(tmpDest)) {
                    out.print(xml.documentString());
                    out.flush();
                }
                boolean ok = context.getLocal().upload(tmpDest.getPath(), remotePath, context.getShell().getHost());
                if(!ok){
                    successful=false;
                    context.error("failed to upload xml to "+remotePath);
                    context.abort(false);
                }
            }
        } catch (IOException e) {
            logger.error("{}@{} failed to create local tmp file",this.toString(),context.getShell().getHost().getHostName(),e);
            successful = false;
            output = "COULD NOT LOAD: "+path;
        } finally {
            if(output.isBlank()){
                output = input;
            }
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
