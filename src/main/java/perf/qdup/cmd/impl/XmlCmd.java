package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;
import perf.qdup.cmd.CommandResult;
import perf.yaup.StringUtil;
import perf.yaup.file.FileUtility;
import perf.yaup.xml.Xml;
import perf.yaup.xml.XmlLoader;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static perf.yaup.StringUtil.removeQuotes;

public class XmlCmd extends Cmd {
    String path;
    List<String> operations;
    //TODO calling remoeQuotes in Cmd just exposes artifacts from yaml parsing, should be done in CmdBuilder
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
    protected void run(String input, Context context, CommandResult result) {

        XmlLoader loader = new XmlLoader();
        Xml xml = null;
        boolean successful = true;
        String output = input;
        File tmpDest = null;
        try {

            if(path==null || path.isEmpty()){
                xml = loader.loadXml(input);
            }else{
                tmpDest = File.createTempFile("cmd-"+this.getUid()+"-"+context.getSession().getHostName(),"."+System.currentTimeMillis());

                context.getLocal().download(path,tmpDest.getPath(),context.getSession().getHost());
                xml = loader.loadXml(tmpDest.toPath());
            }
            for(int i=0; i<operations.size(); i++){
                String operation = operations.get(i);
                int opIndex = FileUtility.OPERATIONS.stream().mapToInt(op->{
                    int rtrn = operation.indexOf(op);
                    return rtrn;
                }).max().getAsInt();
                String search = opIndex>-1 ? Cmd.populateStateVariables(operation.substring(0,opIndex),this,context.getState()).trim() : operation;
                String modification = opIndex>-1 ? Cmd.populateStateVariables(operation.substring(opIndex),this,context.getState()).trim() : "";

                List<Xml> found = xml.getAll(search);
                if(found.isEmpty()){

                    if(modification.isEmpty()){
                        //we didn't find the xml

                        successful = false;

                    }else{
                        //TODO check if set operation and create node
                    }
                } else {
                    if(modification.isEmpty()){
                        output = found.stream().map(Xml::toString).collect(Collectors.joining("\n"));

                    }else{
                        found.forEach(x->x.modify(modification));
                    }
                }
            }

            try(  PrintWriter out = new PrintWriter( tmpDest )  ){
                out.print(xml.documentString());
                out.flush();
            }
            context.getLocal().upload(tmpDest.getPath(),path,context.getSession().getHost());

        } catch (IOException e) {
            logger.error("{}@{} failed to create local tmp file",this.toString(),context.getSession().getHostName(),e);
            successful = false;
            output = "COULD NOT LOAD: "+path;
        } finally {
            if(tmpDest != null){
                tmpDest.delete();
            }
            if(successful){
                result.next(this,output);
            }else{
                result.skip(this,output);
            }
        }
    }

    @Override
    protected Cmd clone() {
        return new XmlCmd(this.path,this.operations.toArray(new String[]{})).with(this.with);
    }

}
