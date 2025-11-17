package io.hyperfoil.tools.qdup.cmd.impl;

import com.github.dockerjava.api.model.HostConfig;
import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.SshTestBase;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.cmd.PatternValuesMap;
import io.hyperfoil.tools.qdup.cmd.Result;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.HostDefinition;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AddPromptTest extends SshTestBase {

    @Test
    public void qdup_prompt_in_add_prompt(){

        System.out.printf("${{"+ PatternValuesMap.QDUP_GLOBAL+"."+ HostDefinition.QDUP_PROMPT_VARIABLE+"}}");

        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("",
                """
                scripts:
                  foo:
                  - add-prompt: (my-venv-prefix)${{REPLACE_ME}}(mv-venv-suffix)
                  - sh: export PS1='(my-venv-prefix)${{REPLACE_ME}}(mv-venv-suffix)'
                  - sh: echo "PS1=[${PS1}]"
                    then:
                    - set-state: RUN.output
                hosts:
                  local: TARGET_HOST
                roles:
                  doit:
                    hosts: [local]
                    run-scripts: [foo]
                """.replaceAll("TARGET_HOST",getHost().toString())
                        .replaceAll("REPLACE_ME",PatternValuesMap.QDUP_GLOBAL+"."+ HostDefinition.QDUP_PROMPT_VARIABLE)
        ));

        RunConfig config = builder.buildConfig(parser);
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();

        State state = doit.getConfig().getState();
        assertTrue(state.has("output"));
        Object output= state.get("output");
        System.out.println(output);

        dispatcher.shutdown();
    }


   //@Test(timeout = 10_000)
   @Test
   public void export_PS1(){
         Parser parser = Parser.getInstance();
         RunConfigBuilder builder = getBuilder();
         builder.loadYaml(parser.loadFile("",
            """
            scripts:
              foo:
              - add-prompt: FOO
              - sh: export PS1='FOO'
              - sh: echo "PS1=[${PS1}]"
            hosts:
              local: TARGET_HOST
            roles:
              doit:
                hosts: [local]
                run-scripts: [foo]
            """.replaceAll("TARGET_HOST",getHost().toString())
         ));

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
