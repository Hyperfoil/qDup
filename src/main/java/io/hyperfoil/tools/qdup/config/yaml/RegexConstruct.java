package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.cmd.impl.Regex;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.SequenceNode;

public class RegexConstruct extends CmdConstruct {
   public RegexConstruct() {
      super(
         "regex",
         (str)->new Regex(str),
         (json)-> new Regex(json.getString("pattern",""),json.getBoolean("miss",false))
      );
      this.addTopLevelkey("else",(cmd,node)->{
         if(cmd instanceof Regex && node instanceof SequenceNode){
            Regex r = (Regex)cmd;
            SequenceNode sequenceNode = (SequenceNode)node;
            this.sequenceToCmds(sequenceNode).forEach(r::onMiss);
         }else{
            throw new YAMLException("regex else requires a sequence of commands "+node.getStartMark());
         }

      });
   }
}
