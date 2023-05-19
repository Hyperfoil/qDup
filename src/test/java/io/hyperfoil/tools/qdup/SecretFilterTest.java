package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.cmd.SpyContext;
import io.hyperfoil.tools.qdup.cmd.impl.Regex;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import io.hyperfoil.tools.yaup.json.Json;
import org.junit.Test;

import java.util.Iterator;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

import static io.hyperfoil.tools.qdup.SecretFilter.REPLACEMENT;
import static org.junit.Assert.*;

public class SecretFilterTest extends SshTestBase {

   @Test
   public void secret_order(){
      SecretFilter filter = new SecretFilter();
      filter.addSecret("barb");
      filter.addSecret("bara");
      filter.addSecret("barbar");

      Iterator<String> iter = filter.getSecrets().iterator();
      assertTrue(iter.hasNext());
      String current = iter.next();
      assertEquals("first entry should be longest","barbar",current);
      assertTrue(iter.hasNext());
      current = iter.next();
      assertEquals("second entry should be alphabetical","bara",current);
      current = iter.next();
      assertEquals("third entry should be alphabetical","barb",current);

   }
   @Test
   public void secret_contains_another_secret(){
      SecretFilter filter = new SecretFilter();
      filter.addSecret("bar");
      filter.addSecret("foobar");
      String output = filter.filter("foobar");
      assertEquals("full input should be filtered: "+filter.getSecrets(), REPLACEMENT,output);
   }

   @Test
   public void add_empty_filter(){
      SecretFilter filter = new SecretFilter();
      filter.addSecret("");
      assertTrue("adding an empty string filter should be rejected",filter.getSecrets().isEmpty());
   }

   @Test
   public void filter_with_curly_brackets(){
      SecretFilter filter = new SecretFilter();
      filter.addSecret("{\"key\":\"value\"}");
      try {
         String output = filter.filter("foo {\"key\":\"value\"} bar");
         assertEquals("output should remove json","foo "+REPLACEMENT+" bar",output);
      }catch(Exception e){
         fail("should not throw an exception when filtering\n"+e.getMessage());
      }
   }

   @Test
   public void filter_repeated(){
      SecretFilter filter = new SecretFilter();
      filter.addSecret("foo");
      try {
         String output = filter.filter("foobarfoobarfoo");
         assertEquals("output should remove secret",REPLACEMENT+"bar"+REPLACEMENT+"bar"+REPLACEMENT,output);
      }catch(Exception e){
         fail("should not throw an exception when filtering\n"+e.getMessage());
      }
   }

   @Test
   public void detect_secret_in_state(){
      State state = new State("");

      state.set(SecretFilter.SECRET_NAME_PREFIX+"foo","BAR");

      SecretFilter secretFilter = state.getSecretFilter();

      assertEquals("expect 1 secret",1,secretFilter.size());
      assertTrue("state should add key without prefix: "+state.getKeys(),state.has("foo"));
      assertFalse("state should not add key with prefix: "+state.getKeys(),state.allKeys().contains(SecretFilter.SECRET_NAME_PREFIX+"foo"));
      //assertEquals("get with prefix returns value","BAR",state.get(SecretFilter.SECRET_NAME_PREFIX+"foo"));
      assertEquals("get with prefix returns value","BAR",state.get("foo"));
   }

   @Test
   public void detect_secret_in_regex(){
      Regex regex = new Regex("(?<"+SecretFilter.SECRET_NAME_PREFIX+"foo>\\d+)");
      SpyContext spyContext = new SpyContext();
      regex.run("123abc",spyContext);
      State state = spyContext.getState();
      SecretFilter secretFilter = state.getSecretFilter();

      assertEquals("exect an entry in the filter",1,secretFilter.size());
      assertTrue("expect foo in state: "+state.allKeys(),state.allKeys().contains("foo"));
   }

   @Test
   public void check_log_for_secret_from_with_setState_states(){
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("",stream(""+
         "scripts:",
         "  secrets:",
         "  - set-state: \""+SecretFilter.SECRET_NAME_PREFIX+"foo BAR\"",
         "  - sh: echo ${{foo}}__${{biz}}",
         "    with:",
         "      "+SecretFilter.SECRET_NAME_PREFIX+"biz: BAR",
         "  - set-state: RUN.output",
         "  - regex: (?<"+SecretFilter.SECRET_NAME_PREFIX+"RUN.match>.*)",
         "    then:",
         "    - sh: echo ${{match}}",
         "hosts:",
         "  local: " + getHost(),
         "roles:",
         "  doit:",
         "    hosts: [local]",
         "    setup-scripts: [secrets]",
         "states:",
         "  "+SecretFilter.SECRET_NAME_PREFIX+"FOO: BAR"
      )));
      RunConfig config = builder.buildConfig(parser);
      assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());

      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run(tmpDir.toString(), config, dispatcher);
      doit.run();

      Object output = doit.getConfig().getState().get("output");
      Object match = doit.getConfig().getState().get("match");
      assertNotNull("run state should contain output",output);
      assertNotNull("run state should contain match",match);
      assertEquals("output should be actual value of secret","BAR__BAR",output.toString());
      assertEquals("matches should be actual value of secret","BAR__BAR",match.toString());
      String runLogContents = readFile(tmpDir.getPath().resolve("run.log"));
      String runJsonContents = readFile(tmpDir.getPath().resolve("run.json"));
      assertFalse("log should not contain BAR\n"+runLogContents,runLogContents.contains("BAR"));
      assertFalse("run.json should not contain BAR\n"+runJsonContents,runLogContents.contains("BAR"));
      try {
         Json json = Json.fromString(runJsonContents);
         assertFalse("json should not be empty",json.isEmpty());
      }catch(RuntimeException e){
         fail("Exception creating json from run.js\n"+e.getMessage());
      }
   }

   @Test
   public void check_lock_for_sendText_from_args(){
      try {
         File yamlFile = File.createTempFile("qdup","yaml",tmpDir.getPath().toFile());
         FileOutputStream fileOutputStream = new FileOutputStream(yamlFile);
         Files.copy(stream(""+
                 "scripts:",
                 "  secrets:",
                 "  - sh: sleep 10s",
                 "    timer:",
                 "      1s:",
                 "      - send-text: ${{foo}}",
                 "      4s:",
                 "      - ctrlC",
                 "hosts:",
                 "  local: " + getHost(),
                 "roles:",
                 "  doit:",
                 "    hosts: [local]",
                 "    setup-scripts: [secrets]",
                 "states:",
                 "  foo: blank"
         ),yamlFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

         String[] args = {"-S","_foo=bar","-i", getIdentity(), "-B", tmpDir.toString(), yamlFile.getPath()};
         QDup qDup = new QDup(args);
         boolean success = qDup.run();
         assertTrue("qdup didn't run",success);

         String runLogContents = readFile(tmpDir.getPath().resolve("run.log"));
         String runJsonContents = readFile(tmpDir.getPath().resolve("run.json"));
         assertFalse("log should not contain bar\n"+runLogContents,runLogContents.contains("bar"));
         assertFalse("run.json should not contain bar\n"+runJsonContents,runLogContents.contains("bar"));


      }catch(IOException e){
         e.printStackTrace();
         fail("failed to create test yaml file");
      }

   }
   @Test
   public void check_log_for_sendText_from_states(){
      Parser parser = Parser.getInstance();
      RunConfigBuilder builder = getBuilder();
      builder.loadYaml(parser.loadFile("",stream(""+
              "scripts:",
              "  secrets:",
              "  - sh: sleep 10s",
              "    timer:",
              "      1s:",
              "      - send-text: ${{foo}}",
              "      4s:",
              "      - ctrlC",
              "hosts:",
              "  local: " + getHost(),
              "roles:",
              "  doit:",
              "    hosts: [local]",
              "    setup-scripts: [secrets]",
              "states:",
              "  "+SecretFilter.SECRET_NAME_PREFIX+"foo: bar"
      )));
      RunConfig config = builder.buildConfig(parser);
      assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());

      Dispatcher dispatcher = new Dispatcher();
      Run doit = new Run(tmpDir.toString(), config, dispatcher);
      doit.run();

      String runLogContents = readFile(tmpDir.getPath().resolve("run.log"));
      String runJsonContents = readFile(tmpDir.getPath().resolve("run.json"));
      assertFalse("log should not contain bar\n"+runLogContents,runLogContents.contains("bar"));
      assertFalse("run.json should not contain bar\n"+runJsonContents,runLogContents.contains("bar"));
      try {
         Json json = Json.fromString(runJsonContents);
         assertFalse("json should not be empty",json.isEmpty());
      }catch(RuntimeException e){
         fail("Exception creating json from run.js\n"+e.getMessage());
      }
   }
}
