package io.hyperfoil.tools.qdup.config.yaml;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CmdMappingTest {

   @Test
   public void getMap_prefix(){
      Cmd cmd = Cmd.NO_OP();
      cmd.setPatternPrefix("$[[");
      CmdMapping<Cmd> mapping = new CmdMapping<>("cmd",null);
      Map<Object,Object> map = mapping.getMap(cmd);
      assertTrue("map should contain prefix",map.containsKey(CmdMapping.PREFIX));
      assertEquals("map prefix","$[[",map.get(CmdMapping.PREFIX));
   }
   @Test
   public void getMap_suffix(){
      Cmd cmd = Cmd.NO_OP();
      cmd.setPatternSuffix("]]");
      CmdMapping<Cmd> mapping = new CmdMapping<>("cmd",null);
      Map<Object,Object> map = mapping.getMap(cmd);
      assertTrue("map should contain suffix",map.containsKey(CmdMapping.SUFFIX));
      assertEquals("map suffix","]]",map.get(CmdMapping.SUFFIX));
   }
   @Test
   public void getMap_separator(){
      Cmd cmd = Cmd.NO_OP();
      cmd.setPatternSeparator("_");
      CmdMapping<Cmd> mapping = new CmdMapping<>("cmd",null);
      Map<Object,Object> map = mapping.getMap(cmd);
      assertTrue("map should contain separator",map.containsKey(CmdMapping.SEPARATOR));
      assertEquals("map separator","_",map.get(CmdMapping.SEPARATOR));
   }
   @Test
   public void getMap_js_prefix(){
      Cmd cmd = Cmd.NO_OP();
      cmd.setPatternJavascriptPrefix("js=");
      CmdMapping<Cmd> mapping = new CmdMapping<>("cmd",null);
      Map<Object,Object> map = mapping.getMap(cmd);
      assertTrue("map should contain js_prefix",map.containsKey(CmdMapping.JS_PREFIX));
      assertEquals("map js_prefix","js=",map.get(CmdMapping.JS_PREFIX));
   }
}
