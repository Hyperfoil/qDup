package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.yaup.json.Json;
import org.junit.Test;
import io.hyperfoil.tools.qdup.cmd.SpyContext;

import static org.junit.Assert.*;

public class JsCmdTest {


    @Test
    public void boolean_expression_true(){
        JsCmd jsCmd = new JsCmd("true === true");
        SpyContext context = new SpyContext();
        jsCmd.run("input",context);

        assertTrue("return true should call next",context.hasNext());
    }
    @Test
    public void boolean_expression_false(){
        JsCmd jsCmd = new JsCmd("true === false");
        SpyContext context = new SpyContext();
        jsCmd.run("input",context);

        assertFalse("return false should call skip",context.hasNext());
        assertTrue("return false should call skip",context.hasSkip());
    }

    @Test
    public void return_regex_capture(){
        JsCmd jsCmd = new JsCmd("function(a,b){ let rtrn = a.match(/<(.*)>/); return rtrn[1];}");
        SpyContext context = new SpyContext();
        jsCmd.run("<h1>",context);
        assertEquals("js should return h1","h1",context.getNext());

    }

    @Test
    public void return_true(){
        JsCmd jsCmd = new JsCmd("function(a,b){ return true;}");
        SpyContext context = new SpyContext();
        jsCmd.run("input",context);

        assertTrue("return true should call next",context.hasNext());
    }
    @Test
    public void return_false(){
        JsCmd jsCmd = new JsCmd("function(a,b){ return false;}");
        SpyContext context = new SpyContext();
        jsCmd.run("input",context);

        assertTrue("return false should call skip",context.hasSkip());
        assertFalse("return false shoudl call skip",context.hasNext());
    }
    @Test
    public void return_false_with_else(){
        JsCmd jsCmd = new JsCmd("function(a,b){ return false;}");
        jsCmd.onElse(Cmd.NO_OP());
        SpyContext context = new SpyContext();
        jsCmd.run("input",context);

        assertFalse("return false should call next for else",context.hasSkip());
        assertTrue("return false should call next for else",context.hasNext());
        Cmd elseCmd = jsCmd.getNext();
        assertNotNull("next should not be null",elseCmd);
    }

    @Test
    public void return_string(){
        JsCmd jsCmd = new JsCmd("function(a,b){ return 'passed';}");
        State state = new State(State.RUN_PREFIX);

        SpyContext context = new SpyContext();

        jsCmd.run("",context);

        assertTrue("return true should call next",context.hasNext());
        assertEquals("result should be passed","passed",context.getNext());
    }
    @Test
    public void no_return(){
        JsCmd jsCmd = new JsCmd("function(a,b){}");
        State state = new State(State.RUN_PREFIX);

        SpyContext context = new SpyContext();


        jsCmd.run("input",context);

        assertTrue("no return should pass to next",context.hasNext());
        assertEquals("no return should use input","input",context.getNext());
    }

    @Test
    public void add_to_state_array(){
        SpyContext context = new SpyContext();
        context.getState().set("FOO", Json.fromString("[\"one\",\"two\"]"));
        JsCmd jsCmd = new JsCmd("(input,state)=>{state['FOO'].push(\'three\'); return 'three'}");
        jsCmd.run("input",context);
        assertEquals("jscmd should pass 'three to next","three",context.getNext());
        assertTrue("state.FOO should be json",context.getState().get("FOO") instanceof Json);
        assertTrue("state.FOO should be an array",((Json)context.getState().get("FOO")).isArray());
        assertEquals("state.FOO should contain 3 entries",3,((Json)context.getState().get("FOO")).size());


    }
    @Test
    public void javascript_object_keys(){
        SpyContext context = new SpyContext();
        context.getState().set("FOO","foo");
        JsCmd jsCmd = new JsCmd("(input,state)=>state['keys']=Object.keys(state)");
        jsCmd.with("BAR","bar");

        jsCmd.doRun("input",context);
        assertFalse("context should not call skip",context.hasSkip());
    }
    @Test
    public void state_from_with(){
        SpyContext context = new SpyContext();
        context.getState().set("FOO","BAR");
        JsCmd jsCmd = new JsCmd("(input,state)=>state['FOO']");
        jsCmd.with("FOO","BIZ");
        jsCmd.run("input",context);
        assertEquals("js should return value from with not context state","BIZ",context.getNext());
    }

    @Test
    public void state_from_dot_notation(){
        SpyContext context = new SpyContext();
        context.getState().set("FOO","BAR");
        JsCmd jsCmd = new JsCmd("(input,state)=>state.FOO");
        jsCmd.run("input",context);
        assertEquals("js should return value from state","BAR",context.getNext());
    }

    @Test
    public void state_from_nested_dot_notation(){
        SpyContext context = new SpyContext();
        context.getState().set("FOO",Json.fromString("{\"BAR\":\"buz\"}"));
        JsCmd jsCmd = new JsCmd("(input,state)=>state.FOO.BAR");
        jsCmd.run("input",context);
        assertEquals("js should return value from state","buz",context.getNext());
    }


    @Test
    public void state_set_value_as_json_array(){
        SpyContext context = new SpyContext();
        context.getState().set("FOO","BAR");
        JsCmd jsCmd = new JsCmd("(input,state)=>{state['BIZ']=['one','two']; return state['FOO']}");
        jsCmd.doRun("input",context);
        Object biz = context.getState().get("BIZ");
        assertNotNull("state should contain BIZ",biz);
        assertTrue("biz should be json "+(biz.getClass().getSimpleName()),biz instanceof Json);
        Json json = (Json)biz;
        assertTrue("biz should be an array "+json,json.isArray());
        assertEquals("biz should have 2 entries: "+json,2,json.size());
    }
    @Test
    public void state_set_value_as_json_map(){
        SpyContext context = new SpyContext();
        context.getState().set("FOO","BAR");
        JsCmd jsCmd = new JsCmd("(input,state)=>{state['BIZ']={one:'one',two:'two'}; return state['FOO']}");
        jsCmd.doRun("input",context);
        Object biz = context.getState().get("BIZ");
        assertNotNull("state should contain BIZ",biz);
        assertTrue("biz should be json "+(biz.getClass().getSimpleName()),biz instanceof Json);
        Json json = (Json)biz;
        assertFalse("biz should be an object "+json,json.isArray());
        assertEquals("biz should have 2 entries: "+json,2,json.size());
    }

    @Test
    public void state_set_value_as_constant(){
        SpyContext context = new SpyContext();
        context.getState().set("FOO","BAR");
        JsCmd jsCmd = new JsCmd("(input,state)=>{state['BIZ']='BUZ'; return state['FOO']}");
        jsCmd.run("input",context);


        assertTrue("state should include BIZ\n"+context.getState().tree(),context.getState().has("BIZ"));
        assertEquals("BIZ should be BUZ","BUZ",context.getState().get("BIZ"));
        assertEquals("JsCmd should call next with javascript output","BAR",context.getNext());
    }

    @Test
    public void state_pattern_in_function(){
        SpyContext context = new SpyContext();
        context.getState().set("FOO","BAR");
        JsCmd jsCmd = new JsCmd("(input,state)=>'${{FOO}}'");
        jsCmd.run("input",context);
        assertEquals("context should call next with BAR","BAR",context.getNext());
    }

    @Test
    public void testReadState(){
        JsCmd jsCmd = new JsCmd("function(input,state){return state['foo'];}");
        SpyContext context = new SpyContext();
        context.getState().set("foo","FOO");
        jsCmd.run("input",context);

        assertTrue("returning state entry that exists should pass to next",context.hasNext());
        assertEquals("next should be FOO","FOO",context.getNext());
    }
    @Test
    public void testMissingState(){
        JsCmd jsCmd = new JsCmd("function(input,state){return state['foo'];}");
        SpyContext context = new SpyContext();
        jsCmd.doRun("input",context);
        assertFalse("missing state should not go to next: "+context.getNext(),context.hasNext());
        assertTrue("returning a missing state should skip: "+context.getSkip(),context.hasSkip());
    }
    @Test
    public void testSetState(){
        JsCmd jsCmd = new JsCmd("function(input,state){state['foo']='FOO';return true;}");
        SpyContext context = new SpyContext();
        jsCmd.run("input",context);

        assertEquals("state should have foo","FOO",context.getState().get("foo"));
    }


}
