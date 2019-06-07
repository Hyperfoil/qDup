package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.State;
import org.junit.Test;
import io.hyperfoil.tools.qdup.cmd.SpyContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JsCmdTest {


    @Test
    public void testReturnTrue(){
        JsCmd jsCmd = new JsCmd("function(a,b){ return true;}");
        SpyContext context = new SpyContext();
        jsCmd.run("input",context);

        assertTrue("return true should call next",context.hasNext());

    }

    @Test
    public void testReturnString(){
        JsCmd jsCmd = new JsCmd("function(a,b){ return 'passed';}");
        State state = new State(State.RUN_PREFIX);

        SpyContext context = new SpyContext();

        jsCmd.run("",context);

        assertTrue("return true should call next",context.hasNext());
        assertEquals("result should be passed","passed",context.getNext());
    }
    @Test
    public void testNoReturn(){
        JsCmd jsCmd = new JsCmd("function(a,b){}");
        State state = new State(State.RUN_PREFIX);

        SpyContext context = new SpyContext();

        jsCmd.run("input",context);

        assertTrue("no return should pass to next",context.hasNext());
        assertEquals("no return should use input","input",context.getNext());
    }

    @Test
    public void testReadState(){
        JsCmd jsCmd = new JsCmd("function(input,state){return state.get('foo');}");
        SpyContext context = new SpyContext();
        context.getState().set("foo","FOO");
        jsCmd.run("input",context);

        assertTrue("returning state entry that exists should pass to next",context.hasNext());
        assertEquals("next should be FOO","FOO",context.getNext());
    }
    @Test
    public void testMissingState(){
        JsCmd jsCmd = new JsCmd("function(input,state){return state.get('foo');}");
        State state = new State(State.RUN_PREFIX);

        SpyContext context = new SpyContext();
        jsCmd.run("input",context);
        assertTrue("returning a missing state should skip",context.hasSkip());
    }
    @Test
    public void testSetState(){
        JsCmd jsCmd = new JsCmd("function(input,state){state.set('foo','FOO');return true;}");

        SpyContext context = new SpyContext();
        jsCmd.run("input",context);

        assertEquals("state should have foo","FOO",context.getState().get("foo"));
    }


}
