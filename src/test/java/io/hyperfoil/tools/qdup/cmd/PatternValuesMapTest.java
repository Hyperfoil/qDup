package io.hyperfoil.tools.qdup.cmd;

import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.yaup.PopulatePatternException;
import io.hyperfoil.tools.yaup.StringUtil;
import io.hyperfoil.tools.yaup.json.Json;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

public class PatternValuesMapTest {

    @Test
    public void find_from_state(){
        Cmd cmd = Cmd.sh("ls");
        State state = new State(State.RUN_PREFIX);
        state.set("key","value");
        Cmd.Ref ref = new Cmd.Ref(cmd);

        PatternValuesMap map = new PatternValuesMap(cmd,state,null,ref);

        assertTrue("map should have key",map.containsKey("key"));
        assertEquals("key should be value","value",map.get("key"));
    }
    @Test
    public void find_from_state_with_prefix(){
        Cmd cmd = Cmd.sh("ls");
        State state = new State(State.RUN_PREFIX);
        state.set("key","value");
        Cmd.Ref ref = new Cmd.Ref(cmd);

        PatternValuesMap map = new PatternValuesMap(cmd,state,null,ref);

        assertTrue("map should have key",map.containsKey("RUN.key"));
        assertEquals("key should be value","value",map.get("RUN.key"));
    }

    @Test
    public void find_from_state_with_javascript(){
        Cmd cmd = Cmd.sh("ls");
        State state = new State(State.RUN_PREFIX);
        state.set("key",Json.fromString("[ \"uno\", \"dos\"]"));
        Cmd.Ref ref = new Cmd.Ref(cmd);

        PatternValuesMap map = new PatternValuesMap(cmd,state,null,ref);

        try {
            String response = StringUtil.populatePattern("${{= [...${{RUN.key}}, \"tres\"] }}", map, Collections.emptyList(), StringUtil.PATTERN_PREFIX, StringUtil.PATTERN_DEFAULT_SEPARATOR, StringUtil.PATTERN_SUFFIX, StringUtil.PATTERN_JAVASCRIPT_PREFIX);
            assertTrue("response should be valid json",Json.isJsonLike(response));
            assertEquals("should populate from state", "[\"uno\",\"dos\",\"tres\"]", response);
        } catch (PopulatePatternException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void find_from_with(){
        Cmd cmd = Cmd.sh("ls");
        State state = new State(State.RUN_PREFIX);
        cmd.with("key","value");
        Cmd.Ref ref = new Cmd.Ref(cmd);

        PatternValuesMap map = new PatternValuesMap(cmd,state,null,ref);

        assertTrue("map should have key",map.containsKey("key"));
        assertEquals("key should be value","value",map.get("key"));
    }
    @Test
    public void find_from_ref_with(){
        Cmd cmd = Cmd.sh("ls");
        State state = new State(State.RUN_PREFIX);
        cmd.with("key", "foo");
        Cmd.Ref ref = new Cmd.Ref(cmd);
        Cmd use = Cmd.sh("pwd");
        PatternValuesMap map = new PatternValuesMap(use,state,null,ref);

        assertTrue("map should have key",map.containsKey("key"));
        assertEquals("key should be foo","foo",map.get("key"));
    }

    @Test
    public void find_from_timer_with_json_on_parent() {
        Cmd top = Cmd.NO_OP();
        Cmd mid = Cmd.NO_OP();
        Cmd bot = Cmd.NO_OP();

        top.then(mid);
        top.addTimer(100,bot);

        top.with("foo", Json.fromString("{ \"bar\":\"${{biz}}\"}"));

        State state = new State("");
        state.set("foo", "state");
        state.set("biz", "value");

        Cmd.Ref ref = new Cmd.Ref(mid);
        PatternValuesMap map = new PatternValuesMap(bot,state,null,ref);

        assertTrue("map should have key",map.containsKey("foo.bar"));
        assertEquals("key.value should be value","value",map.get("foo.bar"));

        String populated = Cmd.populateStateVariables("${{foo.bar}}", bot, state,null);
        assertEquals("with should take priority over state", "value", populated);
    }

    @Test
    public void find_from_ref_with_to_state_json(){
        Cmd cmd = Cmd.sh("ls");
        State state = new State(State.RUN_PREFIX);
        state.set("charlie",Json.fromString("{\"value\":\"foo\"}"));
        //cmd.with("key", Json.fromString("{ \"value\":\"foo\"}"));
        cmd.with("key","${{charlie}}");
        Cmd.Ref ref = new Cmd.Ref(cmd);
        Cmd use = Cmd.sh("pwd");
        PatternValuesMap map = new PatternValuesMap(use,state,null,ref);
        ref.loadAllWithDefs(state,null);
        use.loadAllWithDefs(state,null);
        assertTrue("map should have key",map.containsKey("key.value"));
        assertEquals("key.value should be value","foo",map.get("key.value"));
    }

    @Test
    public void find_from_ref_with_json(){
        Cmd cmd = Cmd.sh("ls");
        State state = new State(State.RUN_PREFIX);
        //cmd.with("key", Json.fromString("{ \"value\":\"foo\"}"));
        cmd.with(Json.fromString("{\"key\":{\"value\":\"foo\"}}"));
        Cmd.Ref ref = new Cmd.Ref(cmd);
        Cmd use = Cmd.sh("pwd");
        PatternValuesMap map = new PatternValuesMap(use,state,null,ref);

        assertTrue("map should have key",map.containsKey("key.value"));
        assertEquals("key.value should be value","foo",map.get("key.value"));
    }
    @Test
    public void find_from_with_json(){
        Cmd use = Cmd.sh("pwd");
        Cmd cmd = Cmd.sh("ls");
        State state = new State(State.RUN_PREFIX);
        //cmd.with("key", Json.fromString("{ \"value\":\"foo\"}"));
        cmd.with(Json.fromString("{\"key\":{\"value\":\"foo\"}}"));

        Cmd.Ref ref = new Cmd.Ref(use);

        PatternValuesMap map = new PatternValuesMap(cmd,state,null,ref);

        assertTrue("map should have key",map.containsKey("key.value"));
        assertEquals("key.value should be value","foo",map.get("key.value"));
    }

    @Test
    public void variable_in_with(){
        Cmd use = Cmd.sh("pwd");
        Cmd cmd = Cmd.sh("ls");
        State state = new State(State.RUN_PREFIX);
        //cmd.with("key", Json.fromString("{ \"value\":\"foo\"}"));
        cmd.with(Json.fromString("{\"key\":{\"value\":\"foo\"}}"));

        Cmd.Ref ref = new Cmd.Ref(use);

        PatternValuesMap map = new PatternValuesMap(cmd,state,null,ref);

        assertTrue("map should have key",map.containsKey("key.value"));
        assertEquals("key.value should be value","foo",map.get("key.value"));
    }
}
