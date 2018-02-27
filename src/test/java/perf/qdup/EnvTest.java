package perf.qdup;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EnvTest {


    @Test
    public void parse(){
        String input = "KEY=/value\n" +
                "NOVALUE=\n" +
                "MULTI_LINE=first\n"+
                "second\n"+
                "  third\n"+
                "EQUAL_IN_VALUE=foo=bar\n";


        Map<String,String> map = Env.parse(input);
        assertEquals("Expect 4 entires including the env variables without a value",4,map.size());
        assertTrue("should contain entries for keys without values",map.containsKey("NOVALUE"));
        assertEquals("MULTI_LINE should have 3 lines",3,map.get("MULTI_LINE").split("\n").length);
        assertEquals("EQUAL_IN_VALUE should split on first equal sign","foo=bar",map.get("EQUAL_IN_VALUE"));
    }

    @Test
    public void diffTo(){
        Map<String,String> from = new LinkedHashMap<>();
        from.put("ONE","alpha");
        from.put("TWO","bravo");
        from.put("THREE","");

        Map<String,String> to = new LinkedHashMap<>();
        to.put("ONE","alpha");
        to.put("TWO","baker");
        to.put("FOUR","delta");

        Env.Diff diff = new Env(from).diffTo(new Env(to));

        assertFalse("diff should not be empty",diff.isEmpty());
        assertEquals("diff should have 1 unset",1,diff.unset().size());
        assertEquals("diff TWO should be baker","baker",diff.get("TWO"));
        assertFalse("diff should not contain ONE",diff.keys().contains("ONE") || diff.unset().contains("ONE"));
        assertTrue("diff shoudl have FOUR",diff.keys().contains("FOUR"));
    }
}
