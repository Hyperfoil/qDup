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
    public void jsonpath_find(){
        Cmd cmd = Cmd.sh("ls");
        State state = new State(State.RUN_PREFIX);
        state.set("hosts",Json.fromString("[{\"publicInterface\":\"eno2\",\"privateMac\":\"0c:29:ef:78:eb:51\",\"hostname\":\"mwperf-server01.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"eno1\",\"privateIp\":\"192.168.0.1\",\"name\":\"mwperf-server01\",\"publicIp\":\"10.1.184.215\",\"publicMac\":\"0c:29:ef:78:eb:52\"},{\"publicInterface\":\"eno2\",\"privateMac\":\"0c:29:ef:78:eb:5e\",\"hostname\":\"mwperf-server02.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"eno1\",\"privateIp\":\"192.168.0.2\",\"name\":\"mwperf-server02\",\"publicIp\":\"10.1.184.216\",\"publicMac\":\"0c:29:ef:78:eb:5f\"},{\"publicInterface\":\"eno2\",\"privateMac\":\"0c:29:ef:78:eb:6b\",\"hostname\":\"mwperf-server03.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"eno1\",\"privateIp\":\"192.168.0.3\",\"name\":\"mwperf-server03\",\"publicIp\":\"10.1.184.217\",\"publicMac\":\"0c:29:ef:78:eb:6c\"},{\"publicInterface\":\"eno2\",\"privateMac\":\"0c:29:ef:78:eb:78\",\"hostname\":\"mwperf-server04.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"eno1\",\"privateIp\":\"192.168.0.4\",\"name\":\"mwperf-server04\",\"publicIp\":\"10.1.184.218\",\"publicMac\":\"0c:29:ef:78:eb:79\"},{\"publicInterface\":\"\",\"privateMac\":\"\",\"hostname\":\"mwperf-server05.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"\",\"privateIp\":\"192.168.0.x\",\"name\":\"mwperf-server05\",\"publicIp\":\"10.1.184.219\",\"publicMac\":\"\"},{\"publicInterface\":\"eno2\",\"privateMac\":\"0c:29:ef:78:c6:9e\",\"hostname\":\"mwperf-server06.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"eno1\",\"privateIp\":\"192.168.0.6\",\"name\":\"mwperf-server06\",\"publicIp\":\"10.1.184.220\",\"publicMac\":\"0c:29:ef:78:c6:9f\"},{\"publicInterface\":\"eno2\",\"privateMac\":\"0c:29:ef:78:c6:ab\",\"hostname\":\"mwperf-server07.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"eno1\",\"privateIp\":\"192.168.0.7\",\"name\":\"mwperf-server07\",\"publicIp\":\"10.1.184.221\",\"publicMac\":\"0c:29:ef:78:c6:ac\"},{\"publicInterface\":\"eno2\",\"privateMac\":\"0c:29:ef:78:c6:b8\",\"hostname\":\"mwperf-server08.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"eno1\",\"privateIp\":\"192.168.0.8\",\"name\":\"mwperf-server08\",\"publicIp\":\"10.1.184.222\",\"publicMac\":\"0c:29:ef:78:c6:b9\"},{\"publicInterface\":\"eno2\",\"privateMac\":\"0c:29:ef:78:ba:51\",\"hostname\":\"mwperf-server09.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"eno1\",\"privateIp\":\"192.168.0.9\",\"name\":\"mwperf-server09\",\"publicIp\":\"10.1.184.223\",\"publicMac\":\"0c:29:ef:78:ba:52\"},{\"publicInterface\":\"em2\",\"privateMac\":\"0c:29:ef:78:ba:5e\",\"hostname\":\"mwperf-server10.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"em1\",\"privateIp\":\"192.168.0.10\",\"name\":\"mwperf-server10\",\"publicIp\":\"10.1.184.224\",\"publicMac\":\"0c:29:ef:78:ba:5f\"},{\"publicInterface\":\"eno2\",\"privateMac\":\"0c:29:ef:78:ba:6b\",\"hostname\":\"mwperf-server11.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"eno1\",\"privateIp\":\"192.168.0.11\",\"name\":\"mwperf-server11\",\"publicIp\":\"10.1.184.225\",\"publicMac\":\"0c:29:ef:78:ba:6c\"},{\"publicInterface\":\"eno2\",\"privateMac\":\"0c:29:ef:78:ba:78\",\"hostname\":\"mwperf-server12.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"eno1\",\"privateIp\":\"192.168.0.12\",\"name\":\"mwperf-server12\",\"publicIp\":\"10.1.184.226\",\"publicMac\":\"0c:29:ef:78:ba:79\"},{\"publicInterface\":\"\",\"privateMac\":\"\",\"hostname\":\"mwperf-server13.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"\",\"privateIp\":\"192.168.0.x\",\"name\":\"mwperf-server13\",\"publicIp\":\"10.1.184.2xx\",\"publicMac\":\"\"},{\"publicInterface\":\"\",\"privateMac\":\"\",\"hostname\":\"mwperf-server14.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"\",\"privateIp\":\"192.168.0.x\",\"name\":\"mwperf-server14\",\"publicIp\":\"10.1.184.2xx\",\"publicMac\":\"\"},{\"publicInterface\":\"\",\"privateMac\":\"\",\"hostname\":\"mwperf-server15.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"\",\"privateIp\":\"192.168.0.x\",\"name\":\"mwperf-server15\",\"publicIp\":\"10.1.184.2xx\",\"publicMac\":\"\"},{\"publicInterface\":\"\",\"privateMac\":\"\",\"hostname\":\"mwperf-server16.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"\",\"privateIp\":\"192.168.0.x\",\"name\":\"mwperf-server16\",\"publicIp\":\"10.1.184.2xx\",\"publicMac\":\"\"}]"));
        Cmd.Ref ref = new Cmd.Ref(cmd);

        PatternValuesMap map = new PatternValuesMap(cmd,state,null,ref);

        String response = Cmd.populateStateVariables("${{hosts[?(@.name == \"mwperf-server16\")]}}",cmd,state,null,ref);
    }
    @Test
    public void jsonpath_miss(){
        Cmd cmd = Cmd.sh("ls");
        State state = new State(State.RUN_PREFIX);
        state.set("hosts",Json.fromString("[{\"publicInterface\":\"eno2\",\"privateMac\":\"0c:29:ef:78:eb:51\",\"hostname\":\"mwperf-server01.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"eno1\",\"privateIp\":\"192.168.0.1\",\"name\":\"mwperf-server01\",\"publicIp\":\"10.1.184.215\",\"publicMac\":\"0c:29:ef:78:eb:52\"},{\"publicInterface\":\"eno2\",\"privateMac\":\"0c:29:ef:78:eb:5e\",\"hostname\":\"mwperf-server02.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"eno1\",\"privateIp\":\"192.168.0.2\",\"name\":\"mwperf-server02\",\"publicIp\":\"10.1.184.216\",\"publicMac\":\"0c:29:ef:78:eb:5f\"},{\"publicInterface\":\"eno2\",\"privateMac\":\"0c:29:ef:78:eb:6b\",\"hostname\":\"mwperf-server03.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"eno1\",\"privateIp\":\"192.168.0.3\",\"name\":\"mwperf-server03\",\"publicIp\":\"10.1.184.217\",\"publicMac\":\"0c:29:ef:78:eb:6c\"},{\"publicInterface\":\"eno2\",\"privateMac\":\"0c:29:ef:78:eb:78\",\"hostname\":\"mwperf-server04.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"eno1\",\"privateIp\":\"192.168.0.4\",\"name\":\"mwperf-server04\",\"publicIp\":\"10.1.184.218\",\"publicMac\":\"0c:29:ef:78:eb:79\"},{\"publicInterface\":\"\",\"privateMac\":\"\",\"hostname\":\"mwperf-server05.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"\",\"privateIp\":\"192.168.0.x\",\"name\":\"mwperf-server05\",\"publicIp\":\"10.1.184.219\",\"publicMac\":\"\"},{\"publicInterface\":\"eno2\",\"privateMac\":\"0c:29:ef:78:c6:9e\",\"hostname\":\"mwperf-server06.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"eno1\",\"privateIp\":\"192.168.0.6\",\"name\":\"mwperf-server06\",\"publicIp\":\"10.1.184.220\",\"publicMac\":\"0c:29:ef:78:c6:9f\"},{\"publicInterface\":\"eno2\",\"privateMac\":\"0c:29:ef:78:c6:ab\",\"hostname\":\"mwperf-server07.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"eno1\",\"privateIp\":\"192.168.0.7\",\"name\":\"mwperf-server07\",\"publicIp\":\"10.1.184.221\",\"publicMac\":\"0c:29:ef:78:c6:ac\"},{\"publicInterface\":\"eno2\",\"privateMac\":\"0c:29:ef:78:c6:b8\",\"hostname\":\"mwperf-server08.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"eno1\",\"privateIp\":\"192.168.0.8\",\"name\":\"mwperf-server08\",\"publicIp\":\"10.1.184.222\",\"publicMac\":\"0c:29:ef:78:c6:b9\"},{\"publicInterface\":\"eno2\",\"privateMac\":\"0c:29:ef:78:ba:51\",\"hostname\":\"mwperf-server09.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"eno1\",\"privateIp\":\"192.168.0.9\",\"name\":\"mwperf-server09\",\"publicIp\":\"10.1.184.223\",\"publicMac\":\"0c:29:ef:78:ba:52\"},{\"publicInterface\":\"em2\",\"privateMac\":\"0c:29:ef:78:ba:5e\",\"hostname\":\"mwperf-server10.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"em1\",\"privateIp\":\"192.168.0.10\",\"name\":\"mwperf-server10\",\"publicIp\":\"10.1.184.224\",\"publicMac\":\"0c:29:ef:78:ba:5f\"},{\"publicInterface\":\"eno2\",\"privateMac\":\"0c:29:ef:78:ba:6b\",\"hostname\":\"mwperf-server11.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"eno1\",\"privateIp\":\"192.168.0.11\",\"name\":\"mwperf-server11\",\"publicIp\":\"10.1.184.225\",\"publicMac\":\"0c:29:ef:78:ba:6c\"},{\"publicInterface\":\"eno2\",\"privateMac\":\"0c:29:ef:78:ba:78\",\"hostname\":\"mwperf-server12.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"eno1\",\"privateIp\":\"192.168.0.12\",\"name\":\"mwperf-server12\",\"publicIp\":\"10.1.184.226\",\"publicMac\":\"0c:29:ef:78:ba:79\"},{\"publicInterface\":\"\",\"privateMac\":\"\",\"hostname\":\"mwperf-server13.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"\",\"privateIp\":\"192.168.0.x\",\"name\":\"mwperf-server13\",\"publicIp\":\"10.1.184.2xx\",\"publicMac\":\"\"},{\"publicInterface\":\"\",\"privateMac\":\"\",\"hostname\":\"mwperf-server14.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"\",\"privateIp\":\"192.168.0.x\",\"name\":\"mwperf-server14\",\"publicIp\":\"10.1.184.2xx\",\"publicMac\":\"\"},{\"publicInterface\":\"\",\"privateMac\":\"\",\"hostname\":\"mwperf-server15.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"\",\"privateIp\":\"192.168.0.x\",\"name\":\"mwperf-server15\",\"publicIp\":\"10.1.184.2xx\",\"publicMac\":\"\"},{\"publicInterface\":\"\",\"privateMac\":\"\",\"hostname\":\"mwperf-server16.perf.lab.eng.rdu2.redhat.com\",\"privateInterface\":\"\",\"privateIp\":\"192.168.0.x\",\"name\":\"mwperf-server16\",\"publicIp\":\"10.1.184.2xx\",\"publicMac\":\"\"}]"));
        Cmd.Ref ref = new Cmd.Ref(cmd);

        PatternValuesMap map = new PatternValuesMap(cmd,state,null,ref);

        String response = Cmd.populateStateVariables("${{hosts[?(@.name == \"does_not_exist\")]}}",cmd,state,null,ref);
    }


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
    public void find_from_state_jsonpath(){
        Cmd cmd = Cmd.sh("ls");
        State state = new State(State.RUN_PREFIX);
        state.set("key",Json.fromString("[ {\"key\":\"uno-uno\",\"value\":\"one\"}, {\"key\":\"dos-dos\",\"value\":\"two\"}]"));
        Cmd.Ref ref = new Cmd.Ref(cmd);
        PatternValuesMap map = new PatternValuesMap(cmd,state,null,ref);
        try {
            String response = StringUtil.populatePattern("${{key[?(@.key == \"uno-uno\")]}}", map, Collections.emptyList(), StringUtil.PATTERN_PREFIX, StringUtil.PATTERN_DEFAULT_SEPARATOR, StringUtil.PATTERN_SUFFIX, StringUtil.PATTERN_JAVASCRIPT_PREFIX);
        }catch (PopulatePatternException e){
            fail(e.getMessage());
        }
    }

    @Test
    public void not_find_from_state_jsonpath(){
        Cmd cmd = Cmd.sh("ls");
        State state = new State(State.RUN_PREFIX);
        state.set("key",Json.fromString("[ {\"key\":\"uno-uno\",\"value\":\"one\"}, {\"key\":\"dos-dos\",\"value\":\"two\"}]"));
        Cmd.Ref ref = new Cmd.Ref(cmd);
        PatternValuesMap map = new PatternValuesMap(cmd,state,null,ref);
        try {
            String response = StringUtil.populatePattern("${{key[?(@.key == \"uno-dos\")]}}", map, Collections.emptyList(), StringUtil.PATTERN_PREFIX, StringUtil.PATTERN_DEFAULT_SEPARATOR, StringUtil.PATTERN_SUFFIX, StringUtil.PATTERN_JAVASCRIPT_PREFIX);
        }catch (PopulatePatternException e){
            fail(e.getMessage());
        }
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
