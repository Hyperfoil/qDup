package perf.qdup.cmd.impl;

import org.junit.Assert;
import org.junit.Test;
import perf.qdup.Run;
import perf.qdup.SshTestBase;
import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.CommandDispatcher;
import perf.qdup.cmd.Result;
import perf.qdup.cmd.Script;
import perf.qdup.config.CmdBuilder;
import perf.qdup.config.RunConfig;
import perf.qdup.config.RunConfigBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ForEachTest extends SshTestBase {

    @Test
    public void getTail_noTail(){
        Cmd start = Cmd.sh("one");
        Cmd tail = start.getTail();
        assertEquals("tail without children is self",start,tail);
    }

    @Test
    public void then_injects_with_children(){
        Cmd start = Cmd.NO_OP();
        start
        .then(
            Cmd.forEach("FOO")
                .then(Cmd.sh("1"))
                .then(Cmd.sh("2")
                    .then(Cmd.sh("2.1"))
                    .then(Cmd.sh("2.2"))
                )
        )
        .then(Cmd.sh("3"));

        System.out.println(start.tree(2,true));

        Cmd forEach = start.getNext();

        Cmd one = forEach.getNext();
        Cmd two = one.getNext();

        Assert.assertEquals("2.next should be for-each",true,two.getNext().toString().contains("FOO"));
        Assert.assertEquals("for-each.skip should be 3",true,forEach.getSkip().toString().contains("3"));

    }


    @Test
    public void loopCount(){
        List<String> lines = new ArrayList<>();
        List<String> args = new ArrayList<>();
        AtomicBoolean tail = new AtomicBoolean(false);
        StringBuilder tailInput = new StringBuilder();
        Script runScript = new Script("run");
        runScript
        .then(Cmd.code(((input, state) -> Result.next("one\ntwo\nthree"))))
        .then(
            Cmd.forEach("ARG")
                .then(Cmd.code((input,state)->{
                    lines.add(input);
                    System.out.println(state.get("ARG"));
                    return Result.next(input);
                }))
        )
        .then(Cmd.code(((input, state) -> {
            tail.set(true);
            tailInput.append("||"+input+"||");
            return Result.next(input);
        })));

        RunConfigBuilder builder = new RunConfigBuilder(CmdBuilder.getBuilder());

        builder.addScript(runScript);
        builder.addHostAlias("local",getHost().toString());
        builder.addHostToRole("role","local");
        builder.addRoleRun("role","run",new HashMap<>());

        RunConfig config = builder.buildConfig();
        assertFalse("unexpected errors:\n"+config.getErrors().stream().collect(Collectors.joining("\n")),config.hasErrors());

        CommandDispatcher dispatcher = new CommandDispatcher();
        Run run = new Run("/tmp",config,dispatcher);
        run.run();

        System.out.println("lines: "+lines);
        System.out.println("tail: "+tail.get());
        System.out.println("input: "+tailInput.toString());
    }
}
