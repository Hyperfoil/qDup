package perf.qdup.cmd;

import org.junit.Assert;
import org.junit.Test;
import perf.qdup.Run;
import perf.qdup.config.CmdBuilder;
import perf.qdup.config.RunConfig;
import perf.qdup.config.RunConfigBuilder;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class CmdTest {


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
