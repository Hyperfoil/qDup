package perf.qdup.cmd.impl;

import org.junit.Assert;
import org.junit.Test;
import perf.qdup.cmd.Cmd;

public class RepeatUntilSignalTest {

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

}
