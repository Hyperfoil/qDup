package io.hyperfoil.tools.qdup;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.qdup.cmd.ContextObserver;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.cmd.impl.CtrlSignal;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import org.junit.Test;
import io.hyperfoil.tools.yaup.json.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class StateTest extends SshTestBase{


    @Test
    public void has_jsonpath_search_found(){
        State s = new State("");
        s.set("key",Json.fromString("[ {\"key\":\"uno-uno\",\"value\":\"one\"}, {\"key\":\"dos-dos\",\"value\":\"two\"}]"));
        boolean found = s.has("key[?(@.key == \"uno-uno\")]");
        assertTrue(found);
    }
    @Test
    public void has_jsonpath_search_missing(){
        State s = new State("");
        s.set("key",Json.fromString("[ {\"key\":\"uno-uno\",\"value\":\"one\"}, {\"key\":\"dos-dos\",\"value\":\"two\"}]"));
        boolean found = s.has("key[?(@.key == \"uno-dos\")]");
        assertTrue(found);
    }
    @Test
    public void get_jsonpath_search_found(){
        State s = new State("");
        s.set("key",Json.fromString("[ {\"key\":\"uno-uno\",\"value\":\"one\"}, {\"key\":\"dos-dos\",\"value\":\"two\"}]"));
        Object found = s.get("key[?(@.key == \"uno-uno\")]");
    }
    @Test
    public void get_jsonpath_search_missing(){
        State s = new State("");
        s.set("key",Json.fromString("[ {\"key\":\"uno-uno\",\"value\":\"one\"}, {\"key\":\"dos-dos\",\"value\":\"two\"}]"));
        Object found = s.get("key[?(@.key == \"uno-dos\")]");
    }


    @Test
    public void run_read_in_cleanup_from_host_insetup(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("",stream(""+
            "scripts:",
            "  buz:",
            "  - read-state: ${{key}}",
            "    then:",
            "    - set-state: RUN.found true",
            "  biz:",
            "  - set-state: HOST.key value",
            "hosts:",
            "  local: " + getHost(),
            "roles:",
            "  doit:",
            "    hosts: [local]",
            "    setup-scripts: [biz]",
            "    cleanup-scripts: [buz]"
        )));
        RunConfig config = builder.buildConfig(parser);
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();
        dispatcher.shutdown();
        assertTrue("state should have found\n"+config.getState().toJson().toString(2),config.getState().has("found"));
    }

    @Test
    public void array_state(){
        State state = new State("");
        Json json = new Json();
        json.add("uno");
        json.add("dos");
        state.set("foo", json);

        Object found = state.get("foo");

        assertTrue("state should have foo", found != null);
        assertTrue("state.get(foo) should be Json", found instanceof Json);

        Json foundJson = (Json)found;

        assertTrue("state.foo should be an array",foundJson.isArray());

    }

    @Test
    public void populateStateVariables_object_spread(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("",stream(""+
              "scripts:",
           "  foo:",
           "  - sh: pwd",
           "hosts:",
           "  local: " + getHost(),
           "roles:",
           "  doit:",
           "    hosts: [local]",
           "    run-scripts: [foo]",
           "states:",
           "  inline: {one : { name : 'uno'}, two : { name : 'dos' } }",
           "  multi:",
           "   - one",
           "   - two"
        )));

        RunConfig config = builder.buildConfig(parser);
        String one = Cmd.populateStateVariables("${{inline.one}}",null,config.getState(),null,null);
        assertEquals("inline.one","{\"name\":\"uno\"}",one);
        String populated = Cmd.populateStateVariables("${{={...${{inline.one}} } }}",null,config.getState(),null,null);
        assertEquals("spread inline.one","{\"name\":\"uno\"}",populated);
    }

    @Test
    public void array_object_reference(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("",stream(""+
              "scripts:",
           "  foo:",
           "  - sh: pwd",
           "hosts:",
           "  local: " + getHost(),
           "roles:",
           "  doit:",
           "    hosts: [local]",
           "    run-scripts: [foo]",
           "states:",
           "  inline: [{name: 'jvm'},{name: 'native'}]",
           "  multi:",
           "   - one",
           "   - two"
        )));

        RunConfig config = builder.buildConfig(parser);

        String populated = Cmd.populateStateVariables("${{inline[1]}}",null,config.getState(),null,null);
        assertEquals("{\"name\":\"native\"}",populated);
    }

    @Test
    public void array_index_reference(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("",stream(""+
              "scripts:",
           "  foo:",
           "  - sh: pwd",
           "hosts:",
           "  local: " + getHost(),
           "roles:",
           "  doit:",
           "    hosts: [local]",
           "    run-scripts: [foo]",
           "states:",
           "  inline: ['one','two']",
           "  multi:",
           "   - one",
           "   - two"
        )));

        RunConfig config = builder.buildConfig(parser);

        String populated = Cmd.populateStateVariables("${{inline[1]}}",null,config.getState(),null,null);
        assertEquals("two",populated);
    }

    @Test
    public void array_state_in_yaml(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder();
        builder.loadYaml(parser.loadFile("",stream(""+
           "scripts:",
           "  foo:",
           "  - sh: pwd",
           "hosts:",
           "  local: " + getHost(),
           "roles:",
           "  doit:",
           "    hosts: [local]",
           "    run-scripts: [foo]",
           "states:",
           "  inline: ['one','two']",
           "  multi:",
           "   - one",
           "   - two"
        )));

        RunConfig config = builder.buildConfig(parser);

        Object found = config.getState().get("inline");

        assertTrue("state should have inline", found != null);
        assertTrue("state.get(inline) should be Json", found instanceof Json);

        Json foundJson = (Json)found;

        assertTrue("state.foo should be an array",foundJson.isArray());

        found = config.getState().get("multi");

        assertTrue("state should have multi", found != null);
        assertTrue("state.get(inline) should be Json", found instanceof Json);

        foundJson = (Json)found;

        assertTrue("state.foo should be an array",foundJson.isArray());
    }

    @Test
    public void set_with_dots() {
        State state = new State("");
        state.set("key.with.dots", "value");
        assertTrue("state should have key", state.has("key"));
        assertTrue("state.get(key) should be Json", state.get("key") instanceof Json);
        assertTrue("state should have key.with.dots", state.has("key.with.dots"));
    }

    @Test
    public void get_nested_search() {
        State state = new State("");
        Json json = new Json();
        json.set("bar", new Json());
        json.getJson("bar").set("biz", "value");
        state.set("foo", json);
        Object found;
        found = state.get("foo");
        assertTrue("state should have foo", found != null);
        assertTrue("state.get(foo) should be Json", found instanceof Json);
        found = state.get("foo.bar");
        assertTrue("state should find foo.bar", found != null);
        assertTrue("state.get(foo.bar) should be Json", found instanceof Json);
        found = state.get("foo.bar.biz");
        assertTrue("state should find foo.bar.biz", found != null);
        assertEquals("state.get(foo.bar.biz)", "value", found);
    }


    @Test
    public void emptyPrefixMakesParentsReadOnly() {
        State top = new State("TOP.");
        State mid = top.addChild("mid", "MID.");
        State bot = mid.addChild("bot", "");

        bot.set("TOP.foo", "foo");

        assertFalse("top should be immutable from bot", top.has("foo"));
        assertTrue(bot.has("TOP.foo"));
        mid.set("TOP.foo", "foo");
        assertEquals("top[foo] should be settable from mid", "foo", top.get("foo"));
    }

    @Test
    public void parent_prefix_json_search() {
        State top = new State("TOP.");
        State mid = top.addChild("mid", "MID.");
        State bot = mid.addChild("bot", null);
        top.set("foo", Json.fromJs("{alpha:{bravo:'charlie'}}"));
        mid.set("foo", Json.fromJs("{alpha:{bravo:'cookie'}}"));

        assertEquals("bot[foo.alpha.bravo] should get the value from mid", "cookie", bot.get("foo.alpha.bravo"));
        assertEquals("bot[MID.foo.alpha.bravo] should get the value from mid", "cookie", bot.get("MID.foo.alpha.bravo"));
        assertEquals("bot[TOP.foo.alpha.bravo] should get hte value from top", "charlie", bot.get("TOP.foo.alpha.bravo"));
    }

    @Test
    public void parent_prefix_json_search_miss() {
        State top = new State("TOP.");
        State mid = top.addChild("mid", "MID.");
        State bot = mid.addChild("bot", null);
        top.set("foo", Json.fromJs("{alpha:{bravo:'charlie'}}"));
        mid.set("foo", Json.fromJs("{alpha:{baker:'cookie'}}"));
        bot.set("foo", Json.fromJs("{alpha:{bravo:'cake'}}"));
        assertEquals("bot[foo.alpha.bravo] should find the value from bot", "cake", bot.get("foo.alpha.bravo"));
        assertEquals("bot[foo.alpha.baker] should find the value from mid", "cookie", bot.get("foo.alpha.baker"));
        assertEquals("bot[MID.foo.alpha.bravo] should find the value from top", "charlie", bot.get("MID.foo.alpha.bravo"));
        assertEquals("bot[TOP.foo.alpha.bravo] should find then value from top", "charlie", bot.get("TOP.foo.alpha.bravo"));

    }

    @Test
    public void set_parent_prefix() {
        State top = new State("TOP.");
        State mid = top.addChild("mid", "MID.");
        State bot = mid.addChild("bot", null);

        mid.set("foo", "first");
        bot.set("MID.foo", "middle");

    }


    @Test
    public void parentPrefix() {
        State top = new State("TOP.");
        State mid = top.addChild("mid", "MID.");
        State bot = mid.addChild("bot", null);

        top.set("foo", "top");
        mid.set("foo", "mid");

        assertEquals("bot[foo] should get the value from mid", "mid", bot.get("foo"));
        assertEquals("bot[MID.foo] should get the value from mid", "mid", bot.get("MID.foo"));
        assertEquals("bot[TOP.foo] should get the value from top", "top", bot.get("TOP.foo"));

        bot.set("foo", "bot");
        assertEquals("bot[MID.foo] should get the value from mid", "mid", bot.get("MID.foo"));

        bot.set("MID.foo", "middle");
        assertEquals("mid[foo] should now be middle", "middle", mid.get("foo"));
        bot.set("TOP.foo", "topper");
        assertEquals("top[foo] should now be topper", "topper", top.get("foo"));
    }

    @Test
    public void test_escape_numeric_key() {
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("",stream(""+
                "scripts:",
                "  generate-state:",
                "  - for-each: APP ${{APPS}}",
                "    then:",
                "    - for-each: VARIANT ${{VARIANT}}",
                "      then:",
                "      - for-each: CPU ${{CPUS}}",
                "        then:",
                "          - set-state: SCOMBO ${{APP}}.${{VARIANT}}.${{CPU}}",
                "          - set-state: RUN.${{SCOMBO}} 1",
                "hosts:",
                "  test: " + getHost(),
                "roles:",
                "  doit:",
                "    hosts: [test]",
                "    run-scripts: ",
                "    - generate-state",
                "states:",
                "  APPS: ",
                "    - 'config-quickstart'",
                "    - 'getting-started'",
                "  VARIANT: ",
                "    - 'JVM'",
                "    - 'native'",
                "  CPUS: ",
                "    - 1",
                "    - 2"

        )));

        RunConfig config = builder.buildConfig(parser);
        assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());

        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();
        State state = config.getState();

        Object found = config.getState().get("getting-started");

        assertTrue("state should have getting-started", found != null);
        assertTrue("state.get(getting-started) should be Json", found instanceof Json);

        Json foundJson = (Json)found;

        assertTrue("state.getting-started should have JVM",foundJson.has("JVM"));
        foundJson = (Json)foundJson.get("JVM");

        String toStringJson =  foundJson.toString();

        assertEquals("{\"1\":1,\"2\":1}",toStringJson);


    }
}
