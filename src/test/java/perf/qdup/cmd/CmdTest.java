package perf.qdup.cmd;

import org.junit.Assert;
import org.junit.Test;
import perf.qdup.Run;
import perf.qdup.State;
import perf.qdup.config.CmdBuilder;
import perf.qdup.config.RunConfig;
import perf.qdup.config.RunConfigBuilder;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class CmdTest {

    @Test
    public void populateStateVariables_envVariable(){
        String populated = Cmd.populateStateVariables("$foo",null,null,false);

        assertEquals("$ env variable reference","$foo",populated);

        populated = Cmd.populateStateVariables("${foo}",null,null,false);
        assertEquals("${} env variable reference","${foo}",populated);
    }
    @Test
    public void populateStateVariables_foundState(){
        State state = new State("RUN");
        state.set("foo","FOO");
        state.set("bar","BAR");

        String populated = Cmd.populateStateVariables("${{foo}}.${{foo}}.${{bar}}",null,state,true);

        assertEquals("all 3 found","FOO.FOO.BAR",populated);
    }
    @Test
    public void populateStateVariables_foundCmd(){
        Cmd cmd = Cmd.NO_OP();
        cmd.with("foo","foo");

        State state = new State("RUN");
        state.set("foo","FOO");
        state.set("bar","BAR");

        String populated = Cmd.populateStateVariables("${{foo}}.${{foo}}.${{bar}}",cmd,state,true);

        assertEquals("cmd override state","foo.foo.BAR",populated);
    }

    @Test
    public void populateStateVariables_parentState(){
        State parent = new State("RUN.");
        State state = parent.addChild("localhost","HOST.");

        parent.set("FOO","foo");

        String populated;
        populated = Cmd.populateStateVariables("${{FOO}}",null,state,true);
        assertEquals("should use parent value","foo",populated);

        state.set("FOO","FOO");
        populated = Cmd.populateStateVariables("${{FOO}}",null,state,true);
        assertEquals("should use child value","FOO",populated);


    }
    @Test
    public void populateStateVariables_prefixState(){
        State parent = new State("RUN.");
        State state = parent.addChild("localhost","HOST.");

        parent.set("FOO","foo");
        state.set("FOO","FOO");

        String populated;
        populated = Cmd.populateStateVariables("${{RUN.FOO}}",null,state,true);
        assertEquals("should use parent value due to prefix","foo",populated);
        populated = Cmd.populateStateVariables("${{HOST.FOO}}",null,state,true);
        assertEquals("should use parent value due to prefix","FOO",populated);


    }
    @Test
    public void populateStateVariables_nestVariable(){
        State state = new State("RUN.");
        state.set("FOO","BAR");
        state.set("BAR","bar");

        String populated;
        populated = Cmd.populateStateVariables("${{${{FOO}}}}",null,state,true);

        assertEquals("evaluate as two state refernces","bar",populated);
    }

    @Test
    public void populateStateVariables_notFound(){
        String populated = Cmd.populateStateVariables("${{FOO}}",null,null,false);

        assertEquals("not replaced","${{FOO}}",populated);
        populated = Cmd.populateStateVariables("${{FOO}}",null,null,true);
        assertEquals("replaced","",populated);
    }

    @Test
    public void populateStateVariables_defaultValue(){
        String populated = Cmd.populateStateVariables("${{FOO:foo}}",null,null,true);
        assertEquals("use default value","foo",populated);
    }
    @Test
    public void populateStateVariables_defaultEmpty_bindState(){
        State state = new State("RUN.");
        state.set("FOO","foo");
        String populated = Cmd.populateStateVariables("${{FOO:}}",null,state,false);

        assertEquals("should populate from state","foo",populated);
    }
    @Test
    public void populateStateVariables_defaultEmpty_bindWith(){
        State state = new State("RUN.");
        state.set("FOO","bar");
        Cmd cmd = Cmd.NO_OP();
        cmd.with("FOO","foo");
        String populated = Cmd.populateStateVariables("${{FOO:}}",cmd,state,false);

        assertEquals("should populate from state","foo",populated);
    }

    @Test
    public void testWith(){
        RunConfigBuilder builder = new RunConfigBuilder(CmdBuilder.getBuilder());

        StringBuilder first = new StringBuilder();
        StringBuilder second = new StringBuilder();
        StringBuilder third = new StringBuilder();

        Script runScript = new Script("run-with");
        runScript.then(Cmd.setState("FOO","BAR"));
        runScript.then(Cmd.sh("echo 1-${{FOO}}").then(Cmd.code((input,state)->{
            first.append(input.trim());
            return Result.next(input);
        })));
        runScript.then(Cmd.NO_OP().with("FOO","FOO").then(Cmd.sh("echo 2-${{FOO}}").then(Cmd.code((input,state)->{
            second.append(input.trim());
            return Result.next(input);
        }))));
        runScript.then(Cmd.sh("echo 3-${{FOO}}").then(Cmd.code((input,state)->{
            third.append(input.trim());
            return Result.next(input);
        })));

        builder.addScript(runScript);

        builder.addHostAlias("local","wreicher@localhost:22");
        builder.addHostToRole("role","local");
        builder.addRoleRun("role","run-with",new HashMap<>());

        RunConfig config = builder.buildConfig();
        CommandDispatcher dispatcher = new CommandDispatcher();
        Run run = new Run("/tmp",config,dispatcher);
        run.run();

        assertEquals("first should see FOO=BAR","1-BAR",first.toString());
        assertEquals("second should see FOO=FOO","2-FOO",second.toString());
        assertEquals("third should see FOO=BAR","3-BAR",third.toString());
    }

    @Test
    public void testCmdTreePrevious(){
        Cmd A = Cmd.log("A")
        .then(Cmd.log("B")
            .then(Cmd.log("C")
                .then(Cmd.log("D")))

        )
        .then(Cmd.log("X"));

        Cmd X = A.getTail();

        Assert.assertEquals("X should be tail of A",true,X.toString().contains("X"));
        Assert.assertEquals("X previous should be B",true,X.getPrevious().toString().contains("B"));

    }
}
