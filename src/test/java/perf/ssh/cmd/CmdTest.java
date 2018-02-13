package perf.ssh.cmd;

import org.junit.Assert;
import org.junit.Test;

public class CmdTest {


    @Test
    public void testCmdTreePrevious(){
        //TODO tree to test where commands get their input (previous)
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
