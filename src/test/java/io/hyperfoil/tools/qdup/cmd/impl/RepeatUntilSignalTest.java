package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.SshTestBase;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import org.junit.Assert;
import org.junit.Test;

import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class RepeatUntilSignalTest extends SshTestBase  {

    @Test
    public void selfSignaling(){
        RepeatUntilSignal repeatUntilSignal = new RepeatUntilSignal("foo");
        repeatUntilSignal.then(Cmd.sleep("1s").then(Cmd.signal("foo")));
        Assert.assertTrue("Should count as self signaling\n"+repeatUntilSignal.tree(2,true),repeatUntilSignal.isSelfSignaling());
    }

    @Test
    public void selfSignaling_regex_onMiss(){
        RepeatUntilSignal repeatUntilSignal = new RepeatUntilSignal("foo");
        Regex regex = new Regex(".*");
        regex.onElse(Cmd.signal("foo"));
        repeatUntilSignal.then(regex);

        Assert.assertTrue("Should count as self signaling\n"+repeatUntilSignal.tree(2,true),repeatUntilSignal.isSelfSignaling());
    }

    @Test
    public void nextAndSkipTest(){
        Cmd start = Cmd.NO_OP();
        start.then(
            Cmd.repeatUntil("FOO")
                .then(Cmd.sh("1"))
                .then(Cmd.sh("2"))
        )
        .then(Cmd.sh("3"));
        Cmd repeat = start.getNext();

        Cmd one = repeat.getNext();
        Cmd two = one.getNext();

        Assert.assertEquals("2.next should be repeat",true,two.getNext().toString().contains("FOO"));
        Assert.assertEquals("repeat.skip should be 3",true,repeat.getSkip().toString().contains("3"));
    }

    @Test
    public void setSignalCountTest(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("", stream("" +
                "scripts:",
                "  repeat-until:",
                "  - repeat-until: TEST_DONE",
                "    then:",
                "    - wait-for: READY",
                "    - set-state: RUN.counter ${{= ${{RUN.counter}} + 1}}",
                "    - set-signal: READY 1",
                "  run-test:",
                "  - for-each:",
                "      name: it",
                "      input: ${{tests}}",
                "    then:",
                "    - log: running test ${{it}}",
                "    - signal: READY",
                "    - sleep: 1s",
                "  - signal: TEST_DONE",
                "hosts:",
                "  local: " + getHost(),
                "roles:",
                "  doit:",
                "    hosts: [local]",
                "    run-scripts:",
                "      - repeat-until",
                "      - run-test",
                "states:",
                "  counter: 0",
                "  tests: ['test1', 'test2', 'test3']"
        )));

        RunConfig config = builder.buildConfig(parser);
        assertFalse("unexpected errors:\n"+config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")),config.hasErrors());

        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);

        doit.run();
        dispatcher.shutdown();
        State state = config.getState();
        assertTrue("state should have key",state.has("counter"));
        assertEquals(3,state.get("counter"));
    }

}
