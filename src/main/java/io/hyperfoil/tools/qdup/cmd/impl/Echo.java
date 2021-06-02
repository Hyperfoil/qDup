package io.hyperfoil.tools.qdup.cmd.impl;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;

import java.lang.invoke.MethodHandles;

public class Echo extends Cmd {

    final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());


    public Echo(){}

    @Override
    public void run(String input, Context context) {
        //logger.info(input);
        context.next(input);
    }

    @Override
    public Cmd copy() {
        return new Echo();
    }
    @Override
    public String toString(){return "echo";}
}
