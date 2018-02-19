package perf.ssh.cmd.impl;

import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.Context;
import perf.ssh.cmd.CommandResult;
import perf.yaup.file.FileUtility;
import perf.yaup.xml.Xml;
import perf.yaup.xml.XmlLoader;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.stream.Collectors;

public class XPath extends Cmd {
    String path;
    public XPath(String path){
        this.path = path;
    }

    @Override
    public String toString(){
        return "xpath: "+path;
    }

    @Override
    protected void run(String input, Context context, CommandResult result) {
        XmlLoader loader = new XmlLoader();
        Xml xml = null;
        if(path.indexOf(FileUtility.SEARCH_KEY)>0){ //file>xpath ...
            String filePath = Cmd.populateStateVariables(path.substring(0,path.indexOf(FileUtility.SEARCH_KEY)),this,context.getState());
            path = path.substring(path.indexOf(FileUtility.SEARCH_KEY)+FileUtility.SEARCH_KEY.length());
            try {
                File tmpDest = File.createTempFile("cmd-"+this.getUid()+"-"+context.getSession().getHostName(),"."+System.currentTimeMillis());
                context.getLocal().download(filePath,tmpDest.getPath(),context.getSession().getHost());
                xml = loader.loadXml(tmpDest.toPath());
                int opIndex = FileUtility.OPERATIONS.stream().mapToInt(op->{
                    int rtrn = path.indexOf(op);
                    return rtrn;
                }).max().getAsInt();

                String search = opIndex>-1 ? Cmd.populateStateVariables(path.substring(0,opIndex),this,context.getState()).trim() : path;
                String operation = opIndex>-1 ? Cmd.populateStateVariables(path.substring(opIndex),this,context.getState()).trim() : "";

                xml = loader.loadXml(tmpDest.toPath());
                List<Xml> found = xml.getAll(search);
                if(operation.isEmpty()){
                    //convert found to a string and send it to next
                    if(found.isEmpty()){
                        result.skip(this,input);
                    }else {
                        String rtrn = found.stream().map(Xml::toString).collect(Collectors.joining("\n"));
                        tmpDest.delete();
                        result.next(this, rtrn);
                    }
                }else{
                    if(found.isEmpty()){
                        result.skip(this,input);
                    }else{
                        found.forEach(x->x.modify(operation));
                        try(  PrintWriter out = new PrintWriter( tmpDest )  ){
                            out.print(xml.documentString());
                        }
                        context.getLocal().upload(tmpDest.getPath(),filePath,context.getSession().getHost());
                        tmpDest.delete();
                        result.next(this,input);//TODO decide a more appropriate output
                    }

                }
            } catch (IOException e) {
                logger.error("{}@{} failed to create local tmp file",this.toString(),context.getSession().getHostName(),e);
            }
        }else{
            //assume the input is the xml to process
            xml = loader.loadXml(input);
        }

    }

    @Override
    protected Cmd clone() {
        return new XPath(this.path).with(this.with);
    }
}
