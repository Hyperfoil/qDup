package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;
import org.jboss.logging.Logger;

import java.lang.invoke.MethodHandles;

public class Echo extends Cmd {

    private final static Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass());

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
