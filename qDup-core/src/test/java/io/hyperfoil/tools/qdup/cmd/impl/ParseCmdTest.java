package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.SshTestBase;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import org.junit.Test;

import static org.junit.Assert.*;

public class ParseCmdTest extends SshTestBase {

    @Test
    public void parser_name_pattern(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("",
                """
                scripts:
                  foo:
                  - sh: echo "foo=one bar=two"
                  - parse:
                    - name: foo
                      pattern: foo=(?<foo>\\S+)
                    - name: bar
                      pattern: bar=(?<bar>\\S+)
                    then:
                    - js: |
                        (input,state)=>{state["RUN.data"]=input}
                hosts:
                  local: TARGET_HOST
                roles:
                  doit:
                    hosts: [local]
                    setup-scripts:
                    - foo
                states:
                  data: "miss"
                """.replaceAll("TARGET_HOST",getHost().toString())
        ));
        RunConfig config = builder.buildConfig(parser);
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();
        dispatcher.shutdown();
        State state = config.getState();
        assertNotEquals("global state should be changed by parse:"+state.get("data"),"miss",state.get("data"));
        String found = state.get("data").toString();
        assertNotNull(found);
        assertTrue("state should contain foo: "+found,found.contains("foo"));
        assertTrue("state should contain bar: "+found,found.contains("bar"));
    }
    public void parser_pattern(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("",
                """
                scripts:
                  foo:
                  - sh: echo "foo=one bar=two"
                  - parse:
                    - pattern: foo=(?<foo>\\S+)
                    - pattern: bar=(?<bar>\\S+)
                    then:
                    - js: |
                        (input,state)=>{state["RUN.data"]=input}
                hosts:
                  local: TARGET_HOST
                roles:
                  doit:
                    hosts: [local]
                    setup-scripts:
                    - foo
                states:
                  data: "miss"
                """.replaceAll("TARGET_HOST",getHost().toString())
        ));
        RunConfig config = builder.buildConfig(parser);
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();
        dispatcher.shutdown();
        State state = config.getState();
        assertNotEquals("global state should be changed by parse:"+state.get("data"),"miss",state.get("data"));
        String found = state.get("data").toString();
        assertNotNull(found);
        assertTrue("state should contain foo: "+found,found.contains("foo"));
        assertTrue("state should contain bar: "+found,found.contains("bar"));
        System.out.println(state.get("data"));

    }
    public void parser_just_a_string(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("",
                """
                scripts:
                  foo:
                  - sh: echo "foo=one bar=two"
                  - parse:
                    - foo=(?<foo>\\S+)
                    - bar=(?<bar>\\S+)
                    then:
                    - js: |
                        (input,state)=>{state["RUN.data"]=input}
                hosts:
                  local: TARGET_HOST
                roles:
                  doit:
                    hosts: [local]
                    setup-scripts:
                    - foo
                states:
                  data: "miss"
                """.replaceAll("TARGET_HOST",getHost().toString())
        ));
        RunConfig config = builder.buildConfig(parser);
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();
        dispatcher.shutdown();
        State state = config.getState();
        assertNotEquals("global state should be changed by parse:"+state.get("data"),"miss",state.get("data"));
        String found = state.get("data").toString();
        assertNotNull(found);
        assertTrue("state should contain foo: "+found,found.contains("foo"));
        assertTrue("state should contain bar: "+found,found.contains("bar"));
        System.out.println(state.get("data"));

    }
}
