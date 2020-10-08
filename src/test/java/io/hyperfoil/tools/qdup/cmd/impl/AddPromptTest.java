package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.SshTestBase;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.cmd.Result;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AddPromptTest extends SshTestBase {


   //@Test(timeout = 10_000)
   @Test
   public void export_PS1(){
         Parser parser = Parser.getInstance();
         RunConfigBuilder builder = getBuilder();
         builder.loadYaml(parser.loadFile("",stream(""+
               "scripts:",
            "  foo:",
            "  - add-prompt: FOO",
            "  - sh: export PS1='FOO'",
            "  - sh: echo \"PS1=[${PS1}]\"",
            "hosts:",
            "  local: " + getHost(),
            "roles:",
            "  doit:",
            "    hosts: [local]",
            "    run-scripts: [foo]"
         )));

         RunConfig config = builder.buildConfig(parser);
         Cmd foo = config.getScript("foo");

         StringBuilder sb = new StringBuilder();

         foo.getTail().then(Cmd.code(((input, state) -> {
            sb.append(input);
            return Result.next(input);
         })));

         Dispatcher dispatcher = new Dispatcher();
         Run doit = new Run(tmpDir.toString(), config, dispatcher);
         doit.run();
         dispatcher.shutdown();
         assertEquals("PS1=[FOO]",sb.toString());
   }
}
