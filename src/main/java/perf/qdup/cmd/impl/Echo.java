package perf.qdup.cmd.impl;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;

import java.lang.invoke.MethodHandles;

public class Echo extends Cmd {

    final static XLogger logger = XLoggerFactory.getXLogger(MethodHandles.lookup().lookupClass());


    public Echo(){}

    @Override
    public void run(String input, Context context) {
        logger.info(input); //TODO should echo also go to the run logger?
        context.next(input);
    }

    @Override
    public Cmd copy() {
        return new Echo();
    }
    @Override
    public String toString(){return "echo:";}
}
