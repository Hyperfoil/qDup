package io.hyperfoil.tools.qdup.config;

import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.SshTestBase;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import io.hyperfoil.tools.yaup.json.Json;
import org.junit.Test;

import static org.junit.Assert.*;

public class GlobalExpressionTest extends SshTestBase {
    @Test
    public void parse_with_javascript() {
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        StringBuilder sb = new StringBuilder();

        builder.loadYaml(parser.loadFile("test",
                """
                scripts:
                  parse-metrics:
                  - set-state: RUN.output.config.client ${{=  argsMapper("${{config.CLIENT_PROCESS_ARGS}}")  }}
                  - log: Extracted rate - ${{RUN.output.config.client.rate}}
                  - set-state: RUN.output.const ${{=  returnConst()  }}
                  - log: Javascript Constant - ${{RUN.output.const}}
                  - log: ${{RUN.output}}
                hosts:
                  local: TARGET_HOST
                roles:
                  doit:
                    hosts: [local]
                    run-scripts: [parse-metrics]
                globals:
                  javascript: |
                    const someValue = 20;
                    function returnConst() {
                      return someValue;
                    }
                    function argsMapper(args) {
                      return JSON.parse('{' + args.split(' ').map(optionsMapper).filter(nullFilter).reduce(jsonString) + '}');
                    }
                    function nullFilter(value){
                      return value!= null;
                    }
                    function jsonString(cur, element){
                      return cur + ', ' + element;
                    }
                    function optionsMapper(value, index, array) {
                      return value.slice(0,2) === '--' ? '"' + value.slice(2,value.length)  + '": ' + (array[index+1].slice(0,2) === '--' ? true : '"' + array[index+1] + '"' ) : null
                    }
                states:
                  config.CLIENT_PROCESS_ARGS: --rate 30000 --warmup 20 --max-pending 100 --show-latency --url tcp://localhost:61616?confirmationWindowSize=20000 --consumer-url tcp://localhost:61616 queue://TEST_QUEUE --warmup 10 --duration 60
                """.replaceAll("TARGET_HOST",getHost().toString())
        ));
        RunConfig config = builder.buildConfig(parser);
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();
        dispatcher.shutdown();
        State runState = config.getState();
        Object endConfigState = runState.get("output.config.client");
        assertNotNull("state config null", endConfigState);
        assertTrue("state config is not Json", endConfigState instanceof Json);
        Json postStateConfig = (Json) endConfigState;
        assertTrue("rate key missing from output.config.client", postStateConfig.has("rate"));
        assertEquals("rate value not expected value", "30000", postStateConfig.get("rate").toString());

        Object endConstState = runState.get("output.const");
        assertNotNull("state const null", endConfigState);
        assertTrue("state const is not Long", endConstState instanceof Long);
        assertEquals("const value not expected value", 20l, endConstState);

    }

    @Test
    public void javascript_empty_array_push_test() {
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        StringBuilder sb = new StringBuilder();

        builder.loadYaml(parser.loadFile("test",
                """
                scripts:
                  array-push-test:
                  - set-state: RUN.output.initialized []
                  - set-state: RUN.output.initialized ${{= arrayPush(${{RUN.output.initialized}}, 200) }}
                  - log: Run Output Initialized;  - ${{RUN.output.initialized}}
                  - set-state: RUN.output.default ${{= arrayPush(${{RUN.output.default:[]}}, 300) }}
                  - log: Run Output Default;  - ${{RUN.output.default}}
                  - set-state: RUN.output.default ${{= arrayPush(${{RUN.output.default}}, 500) }}
                hosts:
                  local: TARGET_HOST
                roles:
                  doit:
                    hosts: [local]
                    run-scripts: [array-push-test]
                globals:
                  javascript: |
                      function arrayPush(arr, value) {
                        return [...arr, value];
                      }
                """.replaceAll("TARGET_HOST",getHost().toString())
        ));
        RunConfig config = builder.buildConfig(parser);
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();
        dispatcher.shutdown();
        State runState = config.getState();
        Object initializedState = runState.get("output.initialized");
        assertNotNull("state config null", initializedState);
        assertTrue("state config is not JSON", initializedState instanceof Json);
        assertTrue("state config is not Array", ((Json) initializedState).isArray());
        assertEquals("array length not 1", 1, ((Json) initializedState).size());
        assertEquals("value is not 200", 200, ((Json) initializedState).get(0));
        Object defaultState = runState.get("output.default");
        assertNotNull("state config null", defaultState);
        assertTrue("state config is not JSON", defaultState instanceof Json);
        assertTrue("state config is not Array", ((Json) defaultState).isArray());
        assertEquals("array length not 2", 2, ((Json) defaultState).size());
        assertEquals("value is not 300", 300, ((Json) defaultState).get(0));

    }


    @Test
    public void multiple_global_test() {
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        StringBuilder sb = new StringBuilder();

        builder.loadYaml(parser.loadFile("test",
                """
                scripts:
                  array-push-test:
                  - set-state: RUN.output.default ${{= arrayPush(${{RUN.output.default:[]}}, 300) }}
                  - set-state: RUN.output.default ${{= arrayPush(${{RUN.output.default}}, 500) }}
                  - set-state: RUN.output.popped ${{= arrayPop(${{RUN.output.default}}) }}
                hosts:
                  local: TARGET_HOST
                roles:
                  doit:
                    hosts: [local]
                    run-scripts: [array-push-test]
                globals:
                  javascript: |
                      function arrayPush(arr, value) {
                        return [...arr, value];
                      }
                globals:
                  javascript: |
                      function arrayPop(arr) {
                        return arr.pop();
                      }
                """.replaceAll("TARGET_HOST",getHost().toString())
        ));
        RunConfig config = builder.buildConfig(parser);
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();
        dispatcher.shutdown();
        State runState = config.getState();
        Object poppedState = runState.get("output.popped");
        assertNotNull("state config null", poppedState);
        assertTrue("state config is not Long", poppedState instanceof Long);
        assertEquals("value is not 500", 500l, poppedState);

    }


    @Test
    public void global_settings() {
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        StringBuilder sb = new StringBuilder();

        builder.loadYaml(parser.loadFile("test",
                """
                scripts:
                  parse-metrics:
                  - log: running with settings
                hosts:
                  local: TARGET_HOST
                roles:
                  doit:
                    hosts: [local]
                    run-scripts: [parse-metrics]
                globals:
                  settings:
                    someValue: 20
                """.replaceAll("TARGET_HOST",getHost().toString())
        ));
        RunConfig config = builder.buildConfig(parser);
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();
        dispatcher.shutdown();
        State runState = config.getState();
    }

}
