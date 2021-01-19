package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.CmdWithElse;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class CmdWithElseTest {

    //test bug where only first else would run
    @Test
    public void else_next(){
        CmdWithElse cmdWithElse = Cmd.regex("foo");
        Cmd first = Cmd.sh("echo first");
        Cmd second = Cmd.sh("echo second");
        cmdWithElse.onElse(first);
        cmdWithElse.onElse(second);


        Cmd next = first.getNext();
        assertNotNull("next should not be null",next);
        assertEquals("next should be second",second,next);
    }
}
