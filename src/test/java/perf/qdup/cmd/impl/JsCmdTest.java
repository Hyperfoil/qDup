package perf.qdup.cmd.impl;

import org.junit.Test;
import perf.qdup.State;
import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.CommandResult;
import perf.qdup.cmd.Context;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JsCmdTest {

    private class SpyCommandResult implements CommandResult {

        List<String> updates;
        String next;
        String skip;

        public SpyCommandResult(){
            updates = new ArrayList<>();
            next = null;
            skip = null;
        }


        @Override
        public void next(Cmd command, String output) {
            next = output;
        }

        @Override
        public void skip(Cmd command, String output) {
            skip = output;
        }

        @Override
        public void update(Cmd command, String output) {
            updates.add(output);
        }

        public String getNext(){return next;}
        public boolean hasNext(){return next!=null;}
        public String getSkip(){return skip;}
        public boolean hasSkip(){return skip!=null;}
    }


    @Test
    public void testReturnTrue(){
        JsCmd jsCmd = new JsCmd("function(a,b){ return true;}");
        State state = new State(State.RUN_PREFIX);

        Context context = new Context(null,state,null,null);
        SpyCommandResult result = new SpyCommandResult();

        jsCmd.run("input",context,result);

        assertTrue("return true should call next",result.hasNext());

    }

    @Test
    public void testReturnString(){
        JsCmd jsCmd = new JsCmd("function(a,b){ return 'passed';}");
        State state = new State(State.RUN_PREFIX);

        Context context = new Context(null,state,null,null);
        SpyCommandResult result = new SpyCommandResult();

        jsCmd.run("",context,result);

        assertTrue("return true should call next",result.hasNext());
        assertEquals("result should be passed","passed",result.getNext());
    }
    @Test
    public void testNoReturn(){
        JsCmd jsCmd = new JsCmd("function(a,b){}");
        State state = new State(State.RUN_PREFIX);

        Context context = new Context(null,state,null,null);
        SpyCommandResult result = new SpyCommandResult();

        jsCmd.run("input",context,result);

        assertTrue("no return should pass to next",result.hasNext());
        assertEquals("no return should use input","input",result.getNext());
    }

    @Test
    public void testReadState(){
        JsCmd jsCmd = new JsCmd("function(input,state){return state.get('foo');}");
        State state = new State(State.RUN_PREFIX);
        state.set("foo","FOO");

        Context context = new Context(null,state,null,null);
        SpyCommandResult result = new SpyCommandResult();

        jsCmd.run("input",context,result);

        assertTrue("returning state entry that exists should pass to next",result.hasNext());
        assertEquals("next should be FOO","FOO",result.getNext());
    }
    @Test
    public void testMissingState(){
        JsCmd jsCmd = new JsCmd("function(input,state){return state.get('foo');}");
        State state = new State(State.RUN_PREFIX);

        Context context = new Context(null,state,null,null);
        SpyCommandResult result = new SpyCommandResult();

        jsCmd.run("input",context,result);
        assertTrue("returning a missing state should skip",result.hasSkip());
    }
    @Test
    public void testSetState(){
        JsCmd jsCmd = new JsCmd("function(input,state){state.set('foo','FOO');return true;}");
        State state = new State(State.RUN_PREFIX);

        Context context = new Context(null,state,null,null);
        SpyCommandResult result = new SpyCommandResult();

        jsCmd.run("input",context,result);

        assertEquals("state should have foo","FOO",state.get("foo"));
    }


}