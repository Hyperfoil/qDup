package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.impl.Regex;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class RegexMappingTest {


   @Test
   public void regex_miss(){
      Regex cmd = new Regex("foo",true);

      RegexMapping mapping = new RegexMapping();
      Map<Object,Object> map = mapping.getMap(cmd);

      assertTrue("map should contain regex "+map.keySet(),map.containsKey("regex"));
      assertTrue("regex should be a map "+map.get("regex"),map.get("regex") instanceof Map);
      Map<Object,Object> regexMap = (Map<Object,Object>)map.get("regex");
      assertTrue("regex map should have miss "+regexMap.keySet(),regexMap.containsKey("miss"));
      assertTrue("regex map should contain pattern "+regexMap.keySet(),regexMap.containsKey("pattern"));
   }

   @Test
   public void regex_else(){
      Regex cmd = new Regex("foo",false);
      cmd.onElse(Cmd.sh("ls"));
      cmd.then(Cmd.echo());

      Parser p = Parser.getInstance();
      RegexMapping mapping = new RegexMapping();
      mapping.setDefer(p.getMapRepresenter());
      Map<Object,Object> map = mapping.getMap(cmd);

      assertTrue("map should contain regex "+map.keySet(),map.containsKey("regex"));
      assertTrue("regex should be a String "+map.get("regex"),map.get("regex") instanceof String);
      assertTrue("map should contain else "+map.keySet(),map.containsKey("else"));
      assertTrue("else should be a list "+map.get("else"),map.get("else") instanceof List);
      List<Object> elseList = (List<Object>)map.get("else");
      assertEquals("else should contain one entry",1,elseList.size());
      assertNotNull("else entry should not be null",elseList.get(0));
      assertTrue("else should contain ls",elseList.get(0).toString().contains("ls"));
   }
}
