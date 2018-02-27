package perf.qdup.cmd.impl;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SleepTest {

    @Test
    public void parseMs(){

        assertEquals("1_000",1000,Sleep.parseToMs("1_000"));
        assertEquals("1ms",1,Sleep.parseToMs("1ms"));
        assertEquals("5s",5000,Sleep.parseToMs("5s"));
        assertEquals("2m",120000,Sleep.parseToMs("2m"));
        assertEquals("1h",60*60*1000,Sleep.parseToMs("1h"));

        assertEquals("1h 2m3s",(60*60*1000 + 120_000 + 3000),Sleep.parseToMs("1h 2m3s"));
        assertEquals("1ms2m3s",(1+120_000+3_000),Sleep.parseToMs("1ms2m3s"));

    }
}
