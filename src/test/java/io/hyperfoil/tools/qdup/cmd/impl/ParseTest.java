package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.SshTestBase;
import io.hyperfoil.tools.qdup.cmd.SpyContext;
import io.hyperfoil.tools.yaup.json.Json;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

public class ParseTest extends SshTestBase {

    @Test
    public void match_decimal(){
        Json cfg = Json.fromString("[{\"pattern\": \"(?<decimal>\\\\d+\\\\.\\\\d+{0,3})\" }]");
        ParseCmd cmd = new ParseCmd(cfg.toString(0));
        SpyContext spyContext = new SpyContext();

        cmd.run("6.001",spyContext);
        assertTrue("context should call next",spyContext.hasNext());
        String next = spyContext.getNext();
        assertTrue("next should be json",Json.isJsonLike(next));
        Json js = Json.fromString(next);
        assertNotNull("next should convert to json",js);
        assertTrue("response should have matched pattern",js.has("decimal"));
        //have to convert with parseDouble because it apparently uses BigDecimal?
        assertEquals(6.001,Double.parseDouble(js.get("decimal").toString()),0.0001);
    }
}
