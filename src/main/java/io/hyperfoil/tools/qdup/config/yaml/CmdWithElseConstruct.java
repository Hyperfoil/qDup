package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.CmdWithElse;
import io.hyperfoil.tools.yaup.json.Json;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.SequenceNode;

import java.util.function.Function;

public class CmdWithElseConstruct extends CmdConstruct{
    public CmdWithElseConstruct(String tag, Function<String, Cmd> fromString, Function<Json, Cmd> fromJson, String... keys) {
        super(tag, fromString, fromJson, keys);
        this.addTopLevelkey("else",(cmd,node)->{
            if(cmd instanceof CmdWithElse && node instanceof SequenceNode){
                CmdWithElse elseCmd = (CmdWithElse)cmd;
                SequenceNode sequenceNode = (SequenceNode)node;
                this.sequenceToCmds(sequenceNode).forEach(elseCmd::onElse);
            }else{
                throw new YAMLException("else requires a sequence of commands\n"+node.getStartMark()+"\n cmd: "+cmd+"\nnode: "+node);
            }
        });
    }
}
