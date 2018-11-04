package perf.qdup.cmd.impl;

import org.junit.Test;
import perf.qdup.State;
import perf.qdup.cmd.ScriptContext;
import perf.qdup.cmd.SpyCommandResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JsCmdTest {


    @Test
    public void testReturnTrue(){
        JsCmd jsCmd = new JsCmd("function(a,b){ return true;}");
        SpyCommandResult context = new SpyCommandResult();
        jsCmd.run("input",context);

        assertTrue("return true should call next",context.hasNext());

    }

    @Test
    public void testReturnString(){
        JsCmd jsCmd = new JsCmd("function(a,b){ return 'passed';}");
        State state = new State(State.RUN_PREFIX);

        SpyCommandResult context = new SpyCommandResult();

        jsCmd.run("",context);

        assertTrue("return true should call next",context.hasNext());
        assertEquals("result should be passed","passed",context.getNext());
    }
    @Test
    public void testNoReturn(){
        JsCmd jsCmd = new JsCmd("function(a,b){}");
        State state = new State(State.RUN_PREFIX);

        SpyCommandResult context = new SpyCommandResult();

        jsCmd.run("input",context);

        assertTrue("no return should pass to next",context.hasNext());
        assertEquals("no return should use input","input",context.getNext());
    }

    @Test
    public void testReadState(){
        JsCmd jsCmd = new JsCmd("function(input,state){return state.get('foo');}");
        SpyCommandResult context = new SpyCommandResult();
        context.getState().set("foo","FOO");
        jsCmd.run("input",context);

        assertTrue("returning state entry that exists should pass to next",context.hasNext());
        assertEquals("next should be FOO","FOO",context.getNext());
    }
    @Test
    public void testMissingState(){
        JsCmd jsCmd = new JsCmd("function(input,state){return state.get('foo');}");
        State state = new State(State.RUN_PREFIX);

        SpyCommandResult context = new SpyCommandResult();
        jsCmd.run("input",context);
        assertTrue("returning a missing state should skip",context.hasSkip());
    }
    @Test
    public void testSetState(){
        JsCmd jsCmd = new JsCmd("function(input,state){state.set('foo','FOO');return true;}");

        SpyCommandResult context = new SpyCommandResult();
        jsCmd.run("input",context);

        assertEquals("state should have foo","FOO",context.getState().get("foo"));
    }


}
