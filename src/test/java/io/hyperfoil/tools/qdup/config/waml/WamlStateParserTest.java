package io.hyperfoil.tools.qdup.config.waml;

import org.junit.Ignore;
import org.junit.Test;
import io.hyperfoil.tools.qdup.SshTestBase;
import perf.yaup.json.Json;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WamlStateParserTest extends SshTestBase {

   static WamlStateParser parse(String...args){
      WamlStateParser parser = new WamlStateParser();
      parser.load("test",stream(args),false);
      return parser;
   }
   public static String join(String...args){
      return Arrays.asList(args).stream().collect(Collectors.joining("\n"));
   }
   public static String[] split(String arg){
      return arg.split("\n");
   }
   static String convert(String...args){
      WamlStateParser parser = new WamlStateParser();
      return parser.load("test",stream(args),true);
   }

   @Test @Ignore
   public void duplicate_key(){
      WamlStateParser parser = parse(""+
         "timer: 5s",
         "  - foo: bar",
         "timer: 2m",
         "  - biz: buz"
      );

   }

   @Test
   public void convert_add_then(){
      String output = convert(new String[]{""+
         "foo: fox",
         "- bar: bug"
      });
      assertEquals(join(""+
         "foo: fox",
         "then:",
         "- bar: bug"
         ),output);
   }
   @Test
   public void convert_nest_thens(){
      String output = convert(new String[]{
         "" +
         "foo: alpha",
         "- bar: bravo",
         "  - biz: charlie",
         "    - buz: delta",
         "- bar: echo"
      });
      assertEquals(join(
      "" +
         "foo: alpha",
         "then:",
         "- bar: bravo",
         "  then:",
         "  - biz: charlie",
         "    then:",
         "    - buz: delta",
         "- bar: echo"
         ),output);
   }

   @Test
   public void map_array_map_array(){
      WamlStateParser parser = parse(""+
         "foo:",
         "- bar: bug",
         "  biz: ",
         "  - buz: #comment",
         "    - fiz"
      );
      assertEquals(Json.fromJs("[{foo: [{bar: 'bug', biz: [{buz: ['fiz'] }] }] }]"),parser.getLoaded());
   }

   @Test @Ignore
   public void timer_child(){
      WamlStateParser parser = parse(""+
            "scripts:",
         "  # Records system-wide perf events",
         "  perf-record: #WAIT_START WAIT_STOP PERF_DATA",
         "    - wait-for: ${{WAIT_START:}}",
         "    - sh: perf record -a -g -o ${{PERF_DATA:/tmp/perf.data}} & export PERF_RECORD_PID=\"$!\"",
         "    - wait-for: ${{WAIT_STOP}}",
         "    - sh: kill ${PERF_RECORD_PID}",
         "       - timer: 5s # No idea why this is getting stuck at times",
         "          - ctrlC"
      );
   }

   @Test
   public void convert_then_after_scalar(){
      String output = convert(new String[]{
         ""+
         "foo: |",
         "  one",
         "  two",
         "  three",
         "- bar: biz",
      });
      assertEquals(join(
         ""+
         "foo: |",
         "  one",
         "  two",
         "  three",
         "then:",
         "- bar: biz"
      ),output);
   }

   @Test
   public void quoted_key_value(){
      WamlStateParser parser = parse(""+
         "\"foo\": \"fox\"",
         "'bar': 'bug'"
      );
      assertEquals(Json.fromJs("[{foo:'fox',bar:'bug'}]"),parser.getLoaded());
   }

   @Test
   public void quoted_entry(){
      WamlStateParser parser = parse(""+
         "[\"one\",'two']");
      assertEquals(Json.fromJs("[['one','two']]"),parser.getLoaded());
   }


   @Test
   public void comment_line(){
      WamlStateParser parser = parse(""+
         "#just a comment",
         "  #indentedComment",
         "- #evil comment"
      );
      assertEquals(Json.fromJs("[[]]"),parser.getLoaded());
   }
   @Test
   public void document_array(){
      WamlStateParser parser = parse(""+
         "-key1:value1",
         "-key2:value2"
      );
      Json loaded = parser.getLoaded();
      assertEquals(Json.fromJs("[[{key1:'value1'},{key2:'value2'}]]"),loaded);
   }
   @Test
   public void document_array_comment(){
      WamlStateParser parser = parse(""+
         "-entry#comment"
      );
      assertEquals(Json.fromJs("[['entry']]"),parser.getLoaded());
   }

   @Test
   public void document_map(){
      WamlStateParser parser = parse(""+
         "key1:value1",
         "key2:value2"
      );
      Json loaded = parser.getLoaded();
      assertEquals(Json.fromJs("[{key1:'value1',key2:'value2'}]"),loaded);
   }
   @Test
   public void document_map_key_quoted(){
      WamlStateParser parser = parse(""+
         "\"ke:y\":value"
      );
      assertEquals(Json.fromJs("[{'ke:y':'value'}]"),parser.getLoaded());
   }
   @Test
   public void document_map_comments(){
      WamlStateParser parser = parse(""+
         "foo:#commennt",
         "  bar: buz#comment"
      );
      assertEquals(Json.fromJs("[{foo:{bar:'buz'}}]"),parser.getLoaded());
   }
   @Test
   public void map_nested(){
      WamlStateParser parser = parse(""+
         "foo:",
         " - one: uno",
         "   two: dos",
         " - one: foo",
         "   two: foo",
         "bar:",
         " - one: uno",
         "   two: dos",
         " - one: bar",
         "   two: bar"
      );
      assertEquals(Json.fromJs("[{foo:[{one:'uno',two:'dos'},{one:'foo',two:'foo'}],bar:[{one:'uno',two:'dos'},{one:'bar',two:'bar'}]}]"),parser.getLoaded());
   }
   @Test
   public void nested_three(){
      WamlStateParser parser = parse(""+
         "a: 1",
         "- aa: 11",
         "  - aaa: 111",
         "    - aaaa: 1111",
         "- ab: 12",
         "  ac: 13"
      );
      assertEquals(Json.fromJs("[{a:'1',then:[{aa:'11',then:[{aaa:'111',then:[{aaaa:'1111'}]}]},{ab:'12',ac:'13'}]}]"),parser.getLoaded());
   }
   @Test
   public void map_inline_array(){
      WamlStateParser parser = parse(""+
         "foo: [a, b, c  ,    d]",
         "bar: [a, \"b[]][{}}{,\" , c,d]",
         "biz: [a, [ aa, ab], [b, c]]"
      );
      assertEquals(Json.fromJs("[{foo:[a,b,c,d],bar:[a,'b[]][{}}{,',c,d],biz:[a,[aa,ab],[b,c]]}]"),parser.getLoaded());
   }

   @Test
   public void inline_map(){
      WamlStateParser parser = parse(""+
            "{key1:value1}"
      );
      assertEquals(Json.fromJs("[{key1:'value1'}]"),parser.getLoaded());
   }
   @Test
   public void inline_map_multiline(){
      WamlStateParser parser = parse(""+
         "{",
         " key1:value1",
         "}"
      );
      assertEquals(Json.fromJs("[{key1:'value1'}]"),parser.getLoaded());
   }
   @Test
   public void inline_array(){
      WamlStateParser parser = parse(""+
         "[key1, value1]"
      );
      assertEquals(Json.fromJs("[['key1', 'value1']]"),parser.getLoaded());
   }
   @Test
   public void inline_array_map(){
      WamlStateParser parser = parse(""+
         "[{key1: value1}, {key2: value2}]"
      );
      assertEquals(Json.fromJs("[[{key1: 'value1'}, {key2: 'value2'}]]"),parser.getLoaded());
   }
   @Test
   public void inline_map_array(){
      WamlStateParser parser = parse(""+
         "{key1: [value1], key2: [value2]}"
      );
      assertEquals(Json.fromJs("[{key1: [value1], key2: [value2]}]"),parser.getLoaded());
   }
   @Test
   public void map_nesting(){
      WamlStateParser parser = parse(""+
         "foo:",
         "  bar:",
         "  #comment",
         "    biz: buz",
         "  fiz: fuz"
      );
      assertEquals(Json.fromJs("[{foo:{bar:{biz:'buz'},fiz:'fuz'}}]"),parser.getLoaded());
   }
   @Test
   public void waml_child(){
      WamlStateParser parser = parse(""+
         "foo: fox",
         "- bar: bat"
      );
      assertEquals(Json.fromJs("[{foo:'fox',then:[{bar:'bat'}]}]"),parser.getLoaded());
   }
   @Test
   public void waml_nesting(){
      WamlStateParser parser = parse(""+
         "foo: fox",
         "- bar: bat",
         "  - biz: bug",
         "- fiz: fuz"
      );
      assertEquals(Json.fromJs("[{foo:'fox',then:[{bar:'bat',then:[{biz:'bug'}]},{fiz:'fuz'}]}]"),parser.getLoaded());
   }

   @Test
   public void waml_after_value_scalar(){
      WamlStateParser parser = parse(""+
         "foo: |",
         "  one",
         "  two",
         "  three",
         "- bar: biz");
      assertEquals(Json.fromJs("[{foo: 'one\\ntwo\\nthree',then:[{bar: 'biz'}]}]"),parser.getLoaded());
   }

   @Test
   public void map_value_scalar(){
      WamlStateParser parser = parse(""+
         "foo: |",
         "  one",
         "  two",
         "  three"
      );
      assertEquals(Json.fromJs("[{foo: 'one\\ntwo\\nthree'}]"),parser.getLoaded());
   }
   @Test
   public void array_entry_scalar(){
      WamlStateParser parser = parse(""+
         "- |",
         "  one",
         "  two",
         "  three"
      );
      assertEquals(Json.fromJs("[['one\\ntwo\\nthree']]"),parser.getLoaded());
   }
   @Test
   public void map_value_folded(){
      WamlStateParser parser = parse(""+
         "foo: >",
         "  one",
         "  two",
         "  three",
         "bar: 'bug'"
      );
      assertEquals(Json.fromJs("[{foo: 'one two three',bar: 'bug'}]"),parser.getLoaded());
   }
   @Test
   public void array_entry_folded(){
      WamlStateParser parser = parse(""+
         "- >",
         "  one",
         "  two",
         "  three"
      );
      assertEquals(Json.fromJs("[[\"one two three\"]]"),parser.getLoaded());
   }
   @Test
   public void multi_document(){
      WamlStateParser parser = parse(""+
         "---",
         "foo: fox",
         "---",
         "---",
         "bar: bug"
      );
      assertEquals(Json.fromJs("[{foo:'fox'},{bar:'bug'}]"),parser.getLoaded());
   }
   @Test
   public void invalid_multi_echo(){
      WamlStateParser parser = parse(""+
         "key: foo '",
         "bar",
         "biz' buz"
      );
      assertTrue(parser.getLoaded().has("error"));
   }

   @Test
   public void variable_entry(){
      WamlStateParser parser = parse(""+
         "- ${{foo}}"
      );
      assertEquals(Json.fromJs("[['${{foo}}']]"),parser.getLoaded());
   }
   @Test
   public void variable_map(){
      WamlStateParser parser = parse(""+
         "${{foo}}: ${{bar}}"
      );
      assertEquals(Json.fromJs("[{'${{foo}}':'${{bar}}'}]"),parser.getLoaded());
   }

}
