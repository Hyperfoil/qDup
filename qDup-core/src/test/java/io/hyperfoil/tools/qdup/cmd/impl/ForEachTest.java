package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.JsonServer;
import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.SshTestBase;
import io.hyperfoil.tools.qdup.State;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.cmd.Result;
import io.hyperfoil.tools.qdup.cmd.Script;
import io.hyperfoil.tools.qdup.cmd.SpyContext;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import io.hyperfoil.tools.yaup.Sets;
import io.hyperfoil.tools.yaup.json.Json;
import io.vertx.core.Vertx;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ForEachTest extends SshTestBase {


    @Test
    public void javascript_from_input() {
        ForEach forEach = new ForEach("FOO");
        SpyContext context = new SpyContext();
        Json input = new Json(true);
        input.add("one");
        input.add("two");
        input.add("three");

        forEach.run(input.toString(), context);

        assertEquals("first run should see one", "one", context.getNext());
    }

    @Test
    public void array_of_integers() {
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("",
                """
                scripts:
                  foo:
                  - for-each:
                      name: index
                      input: ${{=[...Array(${{iterations:1}}).keys()]}}
                    then:
                    - set-state: bar ${{=(${{index}}).toString().padStart(3,"0")}}
                    - set-state:
                        key: RUN.results
                        value: ${{=[...${{RUN.results_[]}},${{bar}}]}}
                        separator: _
                hosts:
                  local: TARGET_HOST
                roles:
                  doit:
                    hosts: [local]
                    run-scripts: [foo]
                """.replaceAll("TARGET_HOST",getHost().toString())
        ));

        RunConfig config = builder.buildConfig(parser);
        Dispatcher dispatcher = new Dispatcher();

        Cmd foo = config.getScript("foo");

        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();
        dispatcher.shutdown();

        State state = config.getState();

        assertTrue("state should have results\n"+state.tree(), state.has("results"));

    }

    @Test
    public void empty_array_of_integers() {
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("",
                """
                scripts:
                  foo:
                  - for-each:
                      name: index
                      input: ${{=[...Array(${{iterations:0}}).keys()]}}
                    then:
                    - set-state:
                        key: RUN.ran
                        value: true
                hosts:
                  local: TARGET_HOST
                roles:
                  doit:
                    hosts: [local]
                    run-scripts: [foo]
                """.replaceAll("TARGET_HOST",getHost().toString())
        ));

        RunConfig config = builder.buildConfig(parser);
        Dispatcher dispatcher = new Dispatcher();

        Cmd foo = config.getScript("foo");

        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();
        dispatcher.shutdown();

        State state = config.getState();
        assertFalse("state should have ran\n"+state.tree(), state.has("ran"));
    }

    @Test
    public void script_with_same_argument() {
        String mac = "00:11:22:33:44:0b";
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("",
                """
                scripts:
                  foo:
                  - script: bar
                    with:
                      host: ${{TEST}}
                  bar:
                  - set-state:
                      key: RUN.key
                      value: ${{= "${{host.mac}}".replace(/\\:/g,'-')}}
                    separator: _
                hosts:
                  local: TARGET_HOST
                roles:
                  doit:
                    hosts: [local]
                    run-scripts: [foo]
                states:
                  TEST:
                    mac: "MAC_ADDRESS"
                """.replaceAll("TARGET_HOST",getHost().toString())
                .replaceAll("MAC_ADDRESS",mac)
        ));

        RunConfig config = builder.buildConfig(parser);
        Dispatcher dispatcher = new Dispatcher();

        Cmd foo = config.getScript("foo");

        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();
        dispatcher.shutdown();

        State state = config.getState();

        assertTrue("state should have key", state.has("key"));
        assertEquals("key should be mac with : replaced with -", mac.replace(":", "-"), state.get("key"));
    }

    @Test
    public void javascript_array_spread() {
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("",
                """
                scripts:
                  foo:
                  - for-each:
                      name: arg
                      input: ${{ [${{charlie}}, ...${{alpha}}, ...${{bravo}} ] }}
                    then:
                    - set-state: RUN.FOO ${{RUN.FOO:}}-${{arg.name}}
                hosts:
                  local: TARGET_HOST
                roles:
                  doit:
                    hosts: [local]
                    run-scripts: [foo]
                states:
                  alpha: [ {name: "ant"}, {name: "apple"} ]
                  bravo: [ {name: "bear"}, {name: "bull"} ]
                  charlie: {name: "cat"}
                """.replaceAll("TARGET_HOST",getHost().toString())
        ));
        RunConfig config = builder.buildConfig(parser);
        Dispatcher dispatcher = new Dispatcher();

        Cmd foo = config.getScript("foo");

        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();
        dispatcher.shutdown();
        assertEquals("input should include alpha, bravo, and charlie", "-cat-ant-apple-bear-bull", config.getState().get("FOO"));
    }

    @Test
    public void foreach_input_entry_asMap() {
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("",
                """
                scripts:
                  foo:
                  - for-each:
                      name: FOO
                      input: [{ name: "hibernate", pattern: "hibernate-core*jar" }, { name: "logging", pattern: "jboss-logging*jar" }]
                    then:
                    - set-state: RUN.BAR ${{RUN.BAR:}}-${{FOO.name}}
                hosts:
                  local: TARGET_HOST
                roles:
                  doit:
                    hosts: [local]
                    run-scripts: [foo]
                """.replaceAll("TARGET_HOST",getHost().toString())
        ));

        RunConfig config = builder.buildConfig(parser);
        assertFalse("unexpected errors:\n" + config.getErrors().stream().map(Objects::toString).collect(Collectors.joining("\n")), config.hasErrors());

        Cmd foo = config.getScript("foo");
        Cmd forEach = foo.getNext();

        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();
        dispatcher.shutdown();

        assertEquals("for-each should run two times", "-hibernate-logging", config.getState().get("BAR"));

    }

    @Test
    public void nested_loop_objects_from_state() {
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("",
                """
                scripts:
                  foo:
                  - for-each: FOO ${{DATA}}
                    then:
                    - for-each: BAR ${{FOO.bar}}
                      then:
                      - set-state: RUN.LOG ${{LOG:}} ${{FOO.name:}}=${{BAR:}}
                hosts:
                  local: TARGET_HOST
                roles:
                  doit:
                    hosts: [local]
                    run-scripts: [foo]
                states:
                  DATA: [{name: "one",bar: [1, 2, 3]},{name: "two",bar: [2,4,6]},{name: 'three' ,bar: [3,6,9]}]
                """.replaceAll("TARGET_HOST",getHost().toString())
        ));

        RunConfig config = builder.buildConfig(parser);
        Cmd foo = config.getScript("foo");

        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();
        dispatcher.shutdown();
        assertEquals("FOO should loop over bar entries", " one=1 one=2 one=3 two=2 two=4 two=6 three=3 three=6 three=9", config.getState().get("LOG"));
    }

    @Test
    public void nested_loop_count() {
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("",
                """
                scripts:
                  foo:
                  - for-each: ARG1 ${{FIRST}}
                    then:
                    - for-each: ARG2 ${{SECOND}}
                      then:
                      - set-state: RUN.LOG ${{LOG:}}-${{ARG1:}}.${{ARG2:}}
                hosts:
                  local: TARGET_HOST
                roles:
                  doit:
                    hosts: [local]
                    run-scripts: [foo]
                states:
                  FIRST: [1, 2, 3]
                  SECOND: [1, 2, 3]
                """.replaceAll("TARGET_HOST",getHost().toString())
        ));

        RunConfig config = builder.buildConfig(parser);
        Cmd foo = config.getScript("foo");

        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();
        dispatcher.shutdown();
        assertEquals("ARG1 and ARG2 should each loop 3 times", "-1.1-1.2-1.3-2.1-2.2-2.3-3.1-3.2-3.3", config.getState().get("LOG"));


    }


    @Test
    public void definedLoopCount() {
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("",
                """
                scripts:
                  foo:
                  - for-each: ARG1 ${{FIRST}}
                    then:
                    - for-each: ARG2 ${{SECOND}}
                      then:
                      - for-each: ARG3 ${{THIRD}}
                        then:
                        - set-state: RUN.LOG ${{LOG:}}-${{ARG1:}}.${{ARG2:}}.${{ARG3:}}
                  - sh: echo ${{LOG}}
                hosts:
                  local: TARGET_HOST
                roles:
                  doit:
                    hosts: [local]
                    run-scripts: [foo]
                states:
                  FIRST: [1, 2]
                  SECOND: [1, 2]
                  THIRD: [1, 2]
                """.replaceAll("TARGET_HOST",getHost().toString())
        ));
        RunConfig config = builder.buildConfig(parser);
        Cmd foo = config.getScript("foo");
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();
        dispatcher.shutdown();

        assertEquals("ARG1 ARG2 ARG3 should each loop 3 times", "-1.1.1-1.1.2-1.2.1-1.2.2-2.1.1-2.1.2-2.2.1-2.2.2", config.getState().get("LOG"));
    }

    @Test
    @Ignore
    public void inject_nested_and_sequence_loops() {
        Script first = new Script("first");
        Cmd arg1 = Cmd.forEach("ARG1");
        Cmd arg2 = Cmd.forEach("ARG2");
        Cmd arg3 = Cmd.forEach("ARG3");
        Cmd log1 = Cmd.log("args1");
        Cmd log2 = Cmd.log("args2");
        Cmd log3a = Cmd.log("args3a");
        Cmd log3b = Cmd.log("args3b");
        Cmd after1 = Cmd.log("after1");
        Cmd after2 = Cmd.log("after2");
        first.then(
                arg1
                        .then(log1)
                        .then(
                                arg2.then(log2))
                        .then(
                                arg3.then(log3a).then(log3b))
                        .then(after1)
                        .then(after2)
        );

    }

    @Test
    public void nest_3_then_sequence() {
        Script first = new Script("first");
        Cmd loop1 = Cmd.forEach("ARG1 ${{FIRST}}");
        Cmd loop2 = Cmd.forEach("ARG2 ${{SECOND}}");
        Cmd loop3 = Cmd.forEach("ARG3 ${{THIRD}}");
        Cmd log1 = Cmd.log("one");
        Cmd log2 = Cmd.log("two");
        Cmd log3a = Cmd.log("threeA");
        Cmd log3b = Cmd.log("threeB");
        Cmd log4 = Cmd.log("four");

        first.then(
                loop1.then(
                        loop2.then(
                                loop3
                                        .then(log1)
                                        .then(log2)
                                        .then(log3a.then(log3b))
                                        .then(log4)
                        )
                )
        );
        assertEquals("log4 should next back to loop3", loop3, log4.getNext().getNext());
        assertEquals("log4 should skip back to loop3", loop3, log4.getSkip().getNext());

        assertEquals("loop2 should skip back to loop1", loop1, loop2.getSkip().getNext());
        assertEquals("loop3 should skip back to loop2", loop2, loop3.getSkip().getNext());


    }

    @Test
    public void inject_nested_loop_with_after() {
        Script first = new Script("first");
        Cmd loop1 = Cmd.forEach("ARG1");
        Cmd loop2 = Cmd.forEach("ARG2");
        Cmd logOne = Cmd.log("one");
        Cmd logTwo = Cmd.log("two");
        Cmd after1 = Cmd.log("after1");
        Cmd after2 = Cmd.log("after2");
        first.then(
                loop1
                        .then(
                                loop2.then(
                                        logOne.then(logTwo)))
                        .then(after1)
                        .then(after2)
        );

        assertEquals("after2's next should be loop1", loop1, after2.getNext().getNext());
        assertEquals("after2 should skip to loop1", loop1, after2.getSkip().getNext());
        assertEquals("logOne should skip to loop2", loop2, logOne.getSkip().getNext());
        assertEquals("then should skip to loop2", loop2, logTwo.getSkip().getNext());
        assertEquals("then should next to loop2", loop2, logTwo.getNext().getNext());


        Cmd injected = Cmd.log("injected");

        logOne.then(injected);

        assertEquals("after2 should next to loop1", loop1, after2.getNext().getNext());
        assertEquals("after2 should skip to loop1", loop1, after2.getSkip().getNext());
        assertEquals("log: one should skip to loop2", loop2, logOne.getSkip().getNext());
        assertEquals("log: two should skip to injected", injected, logTwo.getSkip());
        assertEquals("log: two should next to injected", injected, logTwo.getNext());
        assertEquals("injected should skip to loop2", loop2, injected.getSkip().getNext());
        assertEquals("injected should next to loop2", loop2, injected.getNext().getNext());

    }


    @Test
    public void inject_nested_loop() {
        Script first = new Script("first");
        Cmd loop1 = Cmd.forEach("ARG1");
        Cmd loop2 = Cmd.forEach("ARG2");
        Cmd logOne = Cmd.log("one");
        Cmd logTwo = Cmd.log("two");
        first.then(
                loop1
                        .then(loop2
                                .then(logOne
                                        .then(logTwo)
                                )
                        )
        );

        assertEquals("loop1 next is loop2", loop2, loop1.getNext());
        assertEquals("loop2 next is logOne", logOne, loop2.getNext());
        assertEquals("loop2 skip is loop1", loop1, loop2.getSkip().getNext());
        assertEquals("logOne next is logTwo", logTwo, logOne.getNext());
        assertEquals("logOne skip is loop2", loop2, logOne.getSkip().getNext());
        assertEquals("logTwo next is loop2", loop2, logTwo.getNext().getNext());
        assertEquals("logTwo skip is loop2", loop2, logTwo.getSkip().getNext());
    }

    @Test
    public void inject_then() {
        Script first = new Script("first");
        first.then(
                Cmd.forEach("FOO")
                        .then(Cmd.sh("1"))
                        .then(Cmd.sh("2"))
        );
        Script second = new Script("second");
        second.then(Cmd.sh("foo"));

        Cmd copy = first.deepCopy();
        second.injectThen(copy);

        Cmd firstCopy = second.getNext();

        assertTrue("first next should be for-each", firstCopy.getNext().toString().contains("for-each"));
        //getSkip now depends on the state of parent.getNext() which cannot be checked statically
        //assertTrue("first skip is foo",firstCopy.getSkip().toString().contains("foo"));
        Cmd forEach = firstCopy.getNext();
        //assertTrue("for-each skip is foo",forEach.getSkip().toString().contains("foo"));
    }

    //created for https://github.com/RedHatPerf/qDup/issues/8
    @Test
    public void getSkip_nullWhenLast() {
        Cmd forEach = Cmd.forEach("FOO")
                .then(Cmd.sh("1"))
                .then(Cmd.sh("2"));
        assertNull("forEach skip should be null\n" + forEach.tree(2, true), forEach.getSkip());
    }

    @Test
    public void getSkip_notNullIfNotLast() {
        Cmd parent = Cmd.sh("parent");
        Cmd child = Cmd.sh("child");
        Cmd forEach = Cmd.forEach("FOO");

        parent
                .then(
                        forEach
                                .then(Cmd.sh("1"))
                                .then(Cmd.sh("2"))
                )
                .then(child);

        //getSkip now depends on the state of the parent getNext so it won't work statically
        //assertEquals("for-each skip should be child\n"+forEach.tree(2,true),child,forEach.getSkip());
    }

    @Test
    public void run_defined_spaces() {
        Cmd forEach = Cmd.forEach("FOO", "1 2");
        SpyContext context = new SpyContext();

        context.clear();
        forEach.run("", context);
        assertEquals("next", "1", context.getNext());
        assertNull("skip not called", context.getSkip());

        context.clear();
        forEach.run("", context);
        assertEquals("next", "2", context.getNext());
        assertNull("skip not called", context.getSkip());
        context.clear();
        forEach.run("", context);
        assertNull("next should be null", context.getNext());
        assertEquals("skip should be empty", "", context.getSkip());
    }

    @Test
    public void run_defined_newlines() {
        Cmd forEach = Cmd.forEach("FOO", "1\n2");
        SpyContext context = new SpyContext();

        context.clear();
        forEach.run("", context);
        assertEquals("next", "1", context.getNext());
        assertNull("skip not called", context.getSkip());

        context.clear();
        forEach.run("", context);
        assertEquals("next", "2", context.getNext());
        assertNull("skip not called", context.getSkip());
        context.clear();
        forEach.run("", context);
        assertNull("next should be null", context.getNext());
        assertEquals("skip should be empty", "", context.getSkip());
    }

    @Test
    public void getTail_noTail() {
        Cmd start = Cmd.sh("one");
        Cmd tail = start.getTail();
        assertEquals("tail without children is self", start, tail);
    }

    @Test
    public void split_json_array_strings() {
        List<Object> split = ForEach.split("[\"one\",\"two\",\"three\"]");
        assertEquals("split should have 3 entires\n" + split.stream().map(o -> o.getClass() + " " + o.toString()).collect(Collectors.joining()), 3, split.size());
    }

    @Test
    public void split_json_array_strings_singlequote() {
        List<Object> split = ForEach.split("['one','two','three']");
        assertEquals("split should have 3 entires\n" + split.stream().map(o -> o.getClass() + " " + o.toString()).collect(Collectors.joining()), 3, split.size());
    }

    @Test
    public void split_json_array_objects() {
        List<Object> split = ForEach.split("[{value:'one'},{value:'two'},{value:'three'}]");
        assertEquals("split should have 3 entires\n" + split.stream().map(o -> o.getClass() + " " + o.toString()).collect(Collectors.joining()), 3, split.size());
        assertTrue("split[0] should be json\n" + split.stream().map(o -> o.getClass() + " " + o.toString()).collect(Collectors.joining()), split.get(0) instanceof Json);
        assertTrue("split[1] should be json\n" + split.stream().map(o -> o.getClass() + " " + o.toString()).collect(Collectors.joining()), split.get(1) instanceof Json);
        assertTrue("split[2] should be json\n" + split.stream().map(o -> o.getClass() + " " + o.toString()).collect(Collectors.joining()), split.get(2) instanceof Json);
    }
    @Test
    public void split_json_array_objects_spaced() {
        List<Object> split = ForEach.split("[{'name': 'one', 'value': 'one'}, {'name': 'two', 'value':'two'}, {'name': 'three','value':'three'}]");
        assertEquals("split should have 3 entires\n" + split.stream().map(o -> o.getClass() + " " + o.toString()).collect(Collectors.joining()), 3, split.size());
        assertTrue("split[0] should be json\n" + split.stream().map(o -> o.getClass() + " " + o.toString()).collect(Collectors.joining()), split.get(0) instanceof Json);
        assertTrue("split[1] should be json\n" + split.stream().map(o -> o.getClass() + " " + o.toString()).collect(Collectors.joining()), split.get(1) instanceof Json);
        assertTrue("split[2] should be json\n" + split.stream().map(o -> o.getClass() + " " + o.toString()).collect(Collectors.joining()), split.get(2) instanceof Json);
    }

    @Test
    public void split_json_object() {
        List<Object> split = ForEach.split("{one:'uno',two:'dos',three:'tres'}");
        assertEquals("split should have 3 entires\n" + split.stream().map(o -> o.getClass() + " " + o.toString()).collect(Collectors.joining()), 3, split.size());
        for (int i = 0; i < 3; i++) {
            assertTrue("split[" + i + "] should have key & value as keys", ((Json) split.get(i)).keys().containsAll(Sets.of("key", "value")));
        }
    }

    @Test
    public void split_newLine_over_comma_or_space() {
        List<Object> split = ForEach.split("1 , 1\n2 , 2");
        assertEquals("two entires", 2, split.size());
        assertEquals("1 , 1", split.get(0));
        assertEquals("2 , 2", split.get(1));
    }

    @Test
    public void split_newLine() {
        List<Object> split = ForEach.split("1\n2");
        assertEquals("two entires", 2, split.size());
        assertEquals("1", split.get(0));
        assertEquals("2", split.get(1));
    }

    @Test
    public void split_space() {
        List<Object> split = ForEach.split("1 2");
        assertEquals("two entires", 2, split.size());
        assertEquals("1", split.get(0));
        assertEquals("2", split.get(1));
    }

    @Test
    public void split_comma_space() {
        List<Object> split = ForEach.split("1 , 2");
        assertEquals("two entires", 2, split.size());
        assertEquals("1", split.get(0));
        assertEquals("2", split.get(1));
    }

    @Test
    public void split_json_array() {
        String json = "[\n" +
                "  {\n" +
                "    \"apiVersion\": \"v1\",\n" +
                "    \"kind\": \"Pod\",\n" +
                "    \"metadata\": {\n" +
                "      \"annotations\": {\n" +
                "        \"k8s.v1.cni.cncf.io/networks-status\": \"[{\\n    \\\"name\\\": \\\"openshift-sdn\\\",\\n    \\\"interface\\\": \\\"eth0\\\",\\n    \\\"ips\\\": [\\n        \\\"10.130.5.48\\\"\\n    ],\\n    \\\"dns\\\": {},\\n    \\\"default-route\\\": [\\n        \\\"10.130.4.1\\\"\\n    ]\\n}]\",\n" +
                "        \"openshift.io/scc\": \"restricted\",\n" +
                "        \"serving.knative.dev/creator\": \"scalelab\",\n" +
                "        \"traffic.sidecar.istio.io/includeOutboundIPRanges\": \"172.30.0.0/16\"\n" +
                "      },\n" +
                "      \"creationTimestamp\": \"2020-03-30T15:26:00Z\",\n" +
                "      \"generateName\": \"getting-started-dcx5d-deployment-96f7f7bf9-\",\n" +
                "      \"labels\": {\n" +
                "        \"app\": \"getting-started-dcx5d\",\n" +
                "        \"pod-template-hash\": \"96f7f7bf9\",\n" +
                "        \"serving.knative.dev/configuration\": \"getting-started\",\n" +
                "        \"serving.knative.dev/configurationGeneration\": \"2\",\n" +
                "        \"serving.knative.dev/revision\": \"getting-started-dcx5d\",\n" +
                "        \"serving.knative.dev/revisionUID\": \"809c6ae8-7161-47ff-a8b6-12c6cf74b111\",\n" +
                "        \"serving.knative.dev/service\": \"getting-started\"\n" +
                "      },\n" +
                "      \"name\": \"getting-started-dcx5d-deployment-96f7f7bf9-ntzb7\",\n" +
                "      \"namespace\": \"quarkus-serverless\",\n" +
                "      \"ownerReferences\": [\n" +
                "        {\n" +
                "          \"apiVersion\": \"apps/v1\",\n" +
                "          \"blockOwnerDeletion\": true,\n" +
                "          \"controller\": true,\n" +
                "          \"kind\": \"ReplicaSet\",\n" +
                "          \"name\": \"getting-started-dcx5d-deployment-96f7f7bf9\",\n" +
                "          \"uid\": \"6b7651cb-06ac-4d69-a9e7-07443eff6496\"\n" +
                "        }\n" +
                "      ],\n" +
                "      \"resourceVersion\": \"41167838\",\n" +
                "      \"selfLink\": \"/api/v1/namespaces/quarkus-serverless/pods/getting-started-dcx5d-deployment-96f7f7bf9-ntzb7\",\n" +
                "      \"uid\": \"d9bc0597-6c9f-430f-9879-fdfb9f247908\"\n" +
                "    },\n" +
                "    \"spec\": {\n" +
                "      \"containers\": [\n" +
                "        {\n" +
                "          \"env\": [\n" +
                "            {\n" +
                "              \"name\": \"PORT\",\n" +
                "              \"value\": \"8080\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"K_REVISION\",\n" +
                "              \"value\": \"getting-started-dcx5d\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"K_CONFIGURATION\",\n" +
                "              \"value\": \"getting-started\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"K_SERVICE\",\n" +
                "              \"value\": \"getting-started\"\n" +
                "            }\n" +
                "          ],\n" +
                "          \"image\": \"image-registry.openshift-image-registry.svc:5000/quarkus-serverless/getting-started@sha256:6cbb1db17921335db3b4d9f8a421b026c660bd32957abbdf15cc77e4387ee4c8\",\n" +
                "          \"imagePullPolicy\": \"IfNotPresent\",\n" +
                "          \"lifecycle\": {\n" +
                "            \"preStop\": {\n" +
                "              \"httpGet\": {\n" +
                "                \"path\": \"/wait-for-drain\",\n" +
                "                \"port\": 8022,\n" +
                "                \"scheme\": \"HTTP\"\n" +
                "              }\n" +
                "            }\n" +
                "          },\n" +
                "          \"name\": \"user-container\",\n" +
                "          \"ports\": [\n" +
                "            {\n" +
                "              \"containerPort\": 8080,\n" +
                "              \"name\": \"user-port\",\n" +
                "              \"protocol\": \"TCP\"\n" +
                "            }\n" +
                "          ],\n" +
                "          \"resources\": {},\n" +
                "          \"securityContext\": {\n" +
                "            \"capabilities\": {\n" +
                "              \"drop\": [\n" +
                "                \"KILL\",\n" +
                "                \"MKNOD\",\n" +
                "                \"SETGID\",\n" +
                "                \"SETUID\"\n" +
                "              ]\n" +
                "            },\n" +
                "            \"runAsUser\": 1000620000\n" +
                "          },\n" +
                "          \"terminationMessagePath\": \"/dev/termination-log\",\n" +
                "          \"terminationMessagePolicy\": \"FallbackToLogsOnError\",\n" +
                "          \"volumeMounts\": [\n" +
                "            {\n" +
                "              \"mountPath\": \"/var/log\",\n" +
                "              \"name\": \"knative-var-log\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"mountPath\": \"/var/run/secrets/kubernetes.io/serviceaccount\",\n" +
                "              \"name\": \"default-token-4fscf\",\n" +
                "              \"readOnly\": true\n" +
                "            }\n" +
                "          ]\n" +
                "        },\n" +
                "        {\n" +
                "          \"env\": [\n" +
                "            {\n" +
                "              \"name\": \"SERVING_NAMESPACE\",\n" +
                "              \"value\": \"quarkus-serverless\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"SERVING_SERVICE\",\n" +
                "              \"value\": \"getting-started\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"SERVING_CONFIGURATION\",\n" +
                "              \"value\": \"getting-started\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"SERVING_REVISION\",\n" +
                "              \"value\": \"getting-started-dcx5d\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"QUEUE_SERVING_PORT\",\n" +
                "              \"value\": \"8012\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"CONTAINER_CONCURRENCY\",\n" +
                "              \"value\": \"0\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"REVISION_TIMEOUT_SECONDS\",\n" +
                "              \"value\": \"300\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"SERVING_POD\",\n" +
                "              \"valueFrom\": {\n" +
                "                \"fieldRef\": {\n" +
                "                  \"apiVersion\": \"v1\",\n" +
                "                  \"fieldPath\": \"metadata.name\"\n" +
                "                }\n" +
                "              }\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"SERVING_POD_IP\",\n" +
                "              \"valueFrom\": {\n" +
                "                \"fieldRef\": {\n" +
                "                  \"apiVersion\": \"v1\",\n" +
                "                  \"fieldPath\": \"status.podIP\"\n" +
                "                }\n" +
                "              }\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"SERVING_LOGGING_CONFIG\",\n" +
                "              \"value\": \"{\\n  \\\"level\\\": \\\"info\\\",\\n  \\\"development\\\": false,\\n  \\\"outputPaths\\\": [\\\"stdout\\\"],\\n  \\\"errorOutputPaths\\\": [\\\"stderr\\\"],\\n  \\\"encoding\\\": \\\"json\\\",\\n  \\\"encoderConfig\\\": {\\n    \\\"timeKey\\\": \\\"ts\\\",\\n    \\\"levelKey\\\": \\\"level\\\",\\n    \\\"nameKey\\\": \\\"logger\\\",\\n    \\\"callerKey\\\": \\\"caller\\\",\\n    \\\"messageKey\\\": \\\"msg\\\",\\n    \\\"stacktraceKey\\\": \\\"stacktrace\\\",\\n    \\\"lineEnding\\\": \\\"\\\",\\n    \\\"levelEncoder\\\": \\\"\\\",\\n    \\\"timeEncoder\\\": \\\"iso8601\\\",\\n    \\\"durationEncoder\\\": \\\"\\\",\\n    \\\"callerEncoder\\\": \\\"\\\"\\n  }\\n}\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"SERVING_LOGGING_LEVEL\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"SERVING_REQUEST_LOG_TEMPLATE\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"SERVING_REQUEST_METRICS_BACKEND\",\n" +
                "              \"value\": \"prometheus\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"TRACING_CONFIG_BACKEND\",\n" +
                "              \"value\": \"none\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"TRACING_CONFIG_ZIPKIN_ENDPOINT\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"TRACING_CONFIG_STACKDRIVER_PROJECT_ID\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"TRACING_CONFIG_DEBUG\",\n" +
                "              \"value\": \"false\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"TRACING_CONFIG_SAMPLE_RATE\",\n" +
                "              \"value\": \"0.100000\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"USER_PORT\",\n" +
                "              \"value\": \"8080\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"SYSTEM_NAMESPACE\",\n" +
                "              \"value\": \"knative-serving\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"METRICS_DOMAIN\",\n" +
                "              \"value\": \"knative.dev/internal/serving\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"USER_CONTAINER_NAME\",\n" +
                "              \"value\": \"user-container\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"ENABLE_VAR_LOG_COLLECTION\",\n" +
                "              \"value\": \"false\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"VAR_LOG_VOLUME_NAME\",\n" +
                "              \"value\": \"knative-var-log\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"INTERNAL_VOLUME_PATH\",\n" +
                "              \"value\": \"/var/knative-internal\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"SERVING_READINESS_PROBE\",\n" +
                "              \"value\": \"{\\\"tcpSocket\\\":{\\\"port\\\":8080,\\\"host\\\":\\\"127.0.0.1\\\"},\\\"successThreshold\\\":1}\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"ENABLE_PROFILING\",\n" +
                "              \"value\": \"false\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"SERVING_ENABLE_PROBE_REQUEST_LOG\",\n" +
                "              \"value\": \"false\"\n" +
                "            }\n" +
                "          ],\n" +
                "          \"image\": \"registry.redhat.io/openshift-serverless-1-tech-preview/serving-queue-rhel8@sha256:f9ea8bd70789e67ff00cc134cd966fda8d9e7a764926551d650acc71776db73c\",\n" +
                "          \"imagePullPolicy\": \"IfNotPresent\",\n" +
                "          \"name\": \"queue-proxy\",\n" +
                "          \"ports\": [\n" +
                "            {\n" +
                "              \"containerPort\": 8022,\n" +
                "              \"name\": \"http-queueadm\",\n" +
                "              \"protocol\": \"TCP\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"containerPort\": 9090,\n" +
                "              \"name\": \"queue-metrics\",\n" +
                "              \"protocol\": \"TCP\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"containerPort\": 9091,\n" +
                "              \"name\": \"http-usermetric\",\n" +
                "              \"protocol\": \"TCP\"\n" +
                "            },\n" +
                "            {\n" +
                "              \"containerPort\": 8012,\n" +
                "              \"name\": \"queue-port\",\n" +
                "              \"protocol\": \"TCP\"\n" +
                "            }\n" +
                "          ],\n" +
                "          \"readinessProbe\": {\n" +
                "            \"exec\": {\n" +
                "              \"command\": [\n" +
                "                \"/ko-app/queue\",\n" +
                "                \"-probe-period\",\n" +
                "                \"0\"\n" +
                "              ]\n" +
                "            },\n" +
                "            \"failureThreshold\": 3,\n" +
                "            \"periodSeconds\": 1,\n" +
                "            \"successThreshold\": 1,\n" +
                "            \"timeoutSeconds\": 10\n" +
                "          },\n" +
                "          \"resources\": {\n" +
                "            \"requests\": {\n" +
                "              \"cpu\": \"25m\"\n" +
                "            }\n" +
                "          },\n" +
                "          \"securityContext\": {\n" +
                "            \"allowPrivilegeEscalation\": false,\n" +
                "            \"capabilities\": {\n" +
                "              \"drop\": [\n" +
                "                \"KILL\",\n" +
                "                \"MKNOD\",\n" +
                "                \"SETGID\",\n" +
                "                \"SETUID\"\n" +
                "              ]\n" +
                "            },\n" +
                "            \"runAsUser\": 1000620000\n" +
                "          },\n" +
                "          \"terminationMessagePath\": \"/dev/termination-log\",\n" +
                "          \"terminationMessagePolicy\": \"File\",\n" +
                "          \"volumeMounts\": [\n" +
                "            {\n" +
                "              \"mountPath\": \"/var/run/secrets/kubernetes.io/serviceaccount\",\n" +
                "              \"name\": \"default-token-4fscf\",\n" +
                "              \"readOnly\": true\n" +
                "            }\n" +
                "          ]\n" +
                "        }\n" +
                "      ],\n" +
                "      \"dnsPolicy\": \"ClusterFirst\",\n" +
                "      \"enableServiceLinks\": true,\n" +
                "      \"imagePullSecrets\": [\n" +
                "        {\n" +
                "          \"name\": \"default-dockercfg-68wrb\"\n" +
                "        }\n" +
                "      ],\n" +
                "      \"nodeName\": \"f03-h25-000-r620.rdu2.scalelab.redhat.com\",\n" +
                "      \"priority\": 0,\n" +
                "      \"restartPolicy\": \"Always\",\n" +
                "      \"schedulerName\": \"default-scheduler\",\n" +
                "      \"securityContext\": {\n" +
                "        \"fsGroup\": 1000620000,\n" +
                "        \"seLinuxOptions\": {\n" +
                "          \"level\": \"s0:c25,c10\"\n" +
                "        }\n" +
                "      },\n" +
                "      \"serviceAccount\": \"default\",\n" +
                "      \"serviceAccountName\": \"default\",\n" +
                "      \"terminationGracePeriodSeconds\": 300,\n" +
                "      \"tolerations\": [\n" +
                "        {\n" +
                "          \"effect\": \"NoExecute\",\n" +
                "          \"key\": \"node.kubernetes.io/not-ready\",\n" +
                "          \"operator\": \"Exists\",\n" +
                "          \"tolerationSeconds\": 300\n" +
                "        },\n" +
                "        {\n" +
                "          \"effect\": \"NoExecute\",\n" +
                "          \"key\": \"node.kubernetes.io/unreachable\",\n" +
                "          \"operator\": \"Exists\",\n" +
                "          \"tolerationSeconds\": 300\n" +
                "        },\n" +
                "        {\n" +
                "          \"effect\": \"NoSchedule\",\n" +
                "          \"key\": \"node.kubernetes.io/memory-pressure\",\n" +
                "          \"operator\": \"Exists\"\n" +
                "        }\n" +
                "      ],\n" +
                "      \"volumes\": [\n" +
                "        {\n" +
                "          \"emptyDir\": {},\n" +
                "          \"name\": \"knative-var-log\"\n" +
                "        },\n" +
                "        {\n" +
                "          \"name\": \"default-token-4fscf\",\n" +
                "          \"secret\": {\n" +
                "            \"defaultMode\": 420,\n" +
                "            \"secretName\": \"default-token-4fscf\"\n" +
                "          }\n" +
                "        }\n" +
                "      ]\n" +
                "    },\n" +
                "    \"status\": {\n" +
                "      \"conditions\": [\n" +
                "        {\n" +
                "          \"lastProbeTime\": null,\n" +
                "          \"lastTransitionTime\": \"2020-03-30T15:26:00Z\",\n" +
                "          \"status\": \"True\",\n" +
                "          \"type\": \"Initialized\"\n" +
                "        },\n" +
                "        {\n" +
                "          \"lastProbeTime\": null,\n" +
                "          \"lastTransitionTime\": \"2020-03-30T15:26:04Z\",\n" +
                "          \"status\": \"True\",\n" +
                "          \"type\": \"Ready\"\n" +
                "        },\n" +
                "        {\n" +
                "          \"lastProbeTime\": null,\n" +
                "          \"lastTransitionTime\": \"2020-03-30T15:26:04Z\",\n" +
                "          \"status\": \"True\",\n" +
                "          \"type\": \"ContainersReady\"\n" +
                "        },\n" +
                "        {\n" +
                "          \"lastProbeTime\": null,\n" +
                "          \"lastTransitionTime\": \"2020-03-30T15:26:00Z\",\n" +
                "          \"status\": \"True\",\n" +
                "          \"type\": \"PodScheduled\"\n" +
                "        }\n" +
                "      ],\n" +
                "      \"containerStatuses\": [\n" +
                "        {\n" +
                "          \"containerID\": \"cri-o://9b51ebe63f345235477dd491cb1ca4545a96780001c44f6991b233876db024b4\",\n" +
                "          \"image\": \"registry.redhat.io/openshift-serverless-1-tech-preview/serving-queue-rhel8@sha256:f9ea8bd70789e67ff00cc134cd966fda8d9e7a764926551d650acc71776db73c\",\n" +
                "          \"imageID\": \"registry.redhat.io/openshift-serverless-1-tech-preview/serving-queue-rhel8@sha256:f9ea8bd70789e67ff00cc134cd966fda8d9e7a764926551d650acc71776db73c\",\n" +
                "          \"lastState\": {},\n" +
                "          \"name\": \"queue-proxy\",\n" +
                "          \"ready\": true,\n" +
                "          \"restartCount\": 0,\n" +
                "          \"started\": true,\n" +
                "          \"state\": {\n" +
                "            \"running\": {\n" +
                "              \"startedAt\": \"2020-03-30T15:26:03Z\"\n" +
                "            }\n" +
                "          }\n" +
                "        },\n" +
                "        {\n" +
                "          \"containerID\": \"cri-o://c636a91eec6cce7f7471586df6ce10e7aa50cf11e618a213277a5d1542d75cec\",\n" +
                "          \"image\": \"image-registry.openshift-image-registry.svc:5000/quarkus-serverless/getting-started@sha256:6cbb1db17921335db3b4d9f8a421b026c660bd32957abbdf15cc77e4387ee4c8\",\n" +
                "          \"imageID\": \"image-registry.openshift-image-registry.svc:5000/quarkus-serverless/getting-started@sha256:6cbb1db17921335db3b4d9f8a421b026c660bd32957abbdf15cc77e4387ee4c8\",\n" +
                "          \"lastState\": {},\n" +
                "          \"name\": \"user-container\",\n" +
                "          \"ready\": true,\n" +
                "          \"restartCount\": 0,\n" +
                "          \"started\": true,\n" +
                "          \"state\": {\n" +
                "            \"running\": {\n" +
                "              \"startedAt\": \"2020-03-30T15:26:03Z\"\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      ],\n" +
                "      \"hostIP\": \"172.16.0.12\",\n" +
                "      \"phase\": \"Running\",\n" +
                "      \"podIP\": \"10.130.5.48\",\n" +
                "      \"podIPs\": [\n" +
                "        {\n" +
                "          \"ip\": \"10.130.5.48\"\n" +
                "        }\n" +
                "      ],\n" +
                "      \"qosClass\": \"Burstable\",\n" +
                "      \"startTime\": \"2020-03-30T15:26:00Z\"\n" +
                "    }\n" +
                "  }\n" +
                "]\n";


        Object result = ForEach.split(json);
    }

    @Test
    public void split_quoted_comma() {
        List<Object> split = ForEach.split("['1,1', 2]");
        assertEquals("two entires", 2, split.size());
        assertEquals("1,1", split.get(0));
        assertEquals(2l, split.get(1));
    }

    @Test
    public void split_comma() {
        List<Object> split = ForEach.split("service1, service2, service3");
        assertEquals("should have 3 entires\n" + split.stream().map(o -> o.toString()).collect(Collectors.joining("\n")), 3, split.size());
    }

    @Test
    public void then_injects_with_children() {
        Cmd start = Cmd.NO_OP();
        start
                .then(
                        Cmd.forEach("FOO")
                                .then(Cmd.sh("1"))
                                .then(Cmd.sh("2")
                                        .then(Cmd.sh("2.1"))
                                        .then(Cmd.sh("2.2")
                                                .then(Cmd.sh("2.2.1"))
                                                .then(Cmd.sh("2.2.2")
                                                        .then(Cmd.sh("2.2.2.1"))
                                                        .then(Cmd.sh("2.2.2.2"))
                                                )
                                        )
                                )
                )
                .then(Cmd.sh("3"));

        Cmd forEach = start.getNext();

        Cmd one = forEach.getNext();
        Cmd two = one.getNext();

        Cmd twoTail = two.getTail();

        Assert.assertEquals("2.next should be 2.1", true, two.getNext().toString().contains("2.1"));
        Assert.assertEquals("2.tail should be 2.2.2.2", true, twoTail.toString().contains("2.2.2.2"));
        //This was the original bug in repeat-until & for-each when their last child was a regex (something that often skips)
        assertTrue("2.skip should be for-each", two.getSkip().toString().contains("for-each"));
        //Assert.assertTrue("2.1");
        Assert.assertEquals("for-each.skip should be 3", true, forEach.getSkip().toString().contains("3"));

    }


    @Test
    public void forEach_loopCount() {
        List<String> lines = new ArrayList<>();
        AtomicBoolean tail = new AtomicBoolean(false);
        Script runScript = new Script("run");
        runScript
                .then(Cmd.code(((input, state) -> Result.next("one\ntwo\nthree"))))
                .then(
                        Cmd.forEach("ARG")
                                .then(Cmd.code((input, state) -> {
                                    lines.add(input);
                                    return Result.next(input);
                                }))
                )
                .then(Cmd.code(((input, state) -> {
                    tail.set(true);
                    return Result.next(input);
                })));

        RunConfigBuilder builder = getBuilder();

        builder.addScript(runScript);
        builder.addHostAlias("local", getHost().toString());
        builder.addHostToRole("role", "local");
        builder.addRoleRun("role", "run", new HashMap<>());

        RunConfig config = builder.buildConfig(Parser.getInstance());
        assertFalse("unexpected errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());

        Dispatcher dispatcher = new Dispatcher();
        Run run = new Run(tmpDir.toString(), config, dispatcher);
        run.run();

        assertEquals("lines contains 3 entries", 3, lines.size());
        assertTrue("tail should be called", tail.get());
    }

    @Test
    public void forEach_ls_loop() {
        List<String> lines = new ArrayList<>();
        AtomicBoolean tail = new AtomicBoolean(false);
        Script runScript = new Script("run");
        runScript
                .then(Cmd.sh("rm -r /tmp/foo"))
                .then(Cmd.sh("mkdir /tmp/foo"))
                .then(Cmd.sh("echo \"one\" > /tmp/foo/one.txt"))
                .then(Cmd.sh("echo \"two\" > /tmp/foo/two.txt"))
                .then(Cmd.sh("echo \"three\" > /tmp/foo/three.txt"))
                .then(Cmd.sh("echo \"four\" > /tmp/foo/four.txt"))
                .then(Cmd.sh("ls -1 --color=none /tmp/foo"))
                .then(
                        Cmd.forEach("ARG")
                                .then(Cmd.code((input, state) -> {
                                    lines.add(input);
                                    return Result.next(input);
                                }))
                )
                .then(Cmd.code(((input, state) -> {
                    tail.set(true);
                    return Result.next(input);
                })));

        RunConfigBuilder builder = getBuilder();

        builder.addScript(runScript);
        builder.addHostAlias("local", getHost().toString());
        builder.addHostToRole("role", "local");
        builder.addRoleRun("role", "run", new HashMap<>());

        RunConfig config = builder.buildConfig(Parser.getInstance());
        assertFalse("unexpected errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());

        Dispatcher dispatcher = new Dispatcher();
        Run run = new Run(tmpDir.toString(), config, dispatcher);
        run.run();

        assertEquals("lines contains 3 entries:\n" + lines, 4, lines.size());
        assertTrue("tail should be called", tail.get());

    }

    @Test
    public void state_from_with() {
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("foreach",
            """
            scripts:
              foo:
              - log: FOO=${{FOO}}
              - for-each: SERVICE ${{FOO}}
                then:
                - read-state: SERVICE
            hosts:
              local: TARGET_HOST
            roles:
              doit:
                hosts: [local]
                run-scripts:
                - foo:
                    with:
                      FOO : server1,server2,server3
            """.replaceAll("TARGET_HOST",getHost().toString())
        ));

        RunConfig config = builder.buildConfig(parser);
        assertFalse("unexpected errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
        Cmd target = config.getScript("foo");
        while (target.getNext() != null && !(target instanceof ForEach)) {
            target = target.getNext();
        }
        List<String> splits = new ArrayList<>();
        if (target instanceof ForEach) {
            target.then(Cmd.code(((input, state) -> {
                splits.add(input);
                return Result.next(input);
            })));
        } else {
            fail("failed to find for-each in script foo");
        }


        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);

        doit.run();

        assertEquals("for-each should run 3 times entries:\n" + splits.stream().collect(Collectors.joining("\n")), 3, splits.size());
    }

    @Test
    public void yaml_state_quoted() {
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("foreach",
                """
                scripts:
                  foo:
                  - for-each: SERVICE ${{FOO}}
                    then:
                    - read-state: ${{SERVICE}}
                hosts:
                  local: TARGET_HOST
                roles:
                  doit:
                    hosts: [local]
                    run-scripts: [foo]
                states:
                  FOO: "'server1,server2,server3'"
                """.replaceAll("TARGET_HOST",getHost().toString())
        ));
        RunConfig config = builder.buildConfig(parser);
        Cmd target = config.getScript("foo");
        while (target.getNext() != null && !(target instanceof ForEach)) {
            target = target.getNext();
        }
        List<String> splits = new ArrayList<>();
        target.then(Cmd.code(((input, state) -> {
            splits.add(input);
            return Result.next(input);
        })));
        assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();
        assertEquals("for-each should not split quoted string:\n" + splits.stream().collect(Collectors.joining("\n")), 1, splits.size());

    }

    @Test
    public void yaml_state() {
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("foreach",
                """
                scripts:
                  foo:
                  - for-each: SERVICE ${{FOO}}
                    then:
                    - read-state: SERVICE
                hosts:
                  local: TARGET_HOST
                roles:
                  doit:
                    hosts: [local]
                    run-scripts: [foo]
                states:
                  FOO: server1,server2,server3
                """.replaceAll("TARGET_HOST",getHost().toString())
        ));
        RunConfig config = builder.buildConfig(parser);
        Cmd target = config.getScript("foo");
        while (target.getNext() != null && !(target instanceof ForEach)) {
            target = target.getNext();
        }
        List<String> splits = new ArrayList<>();
        target.then(Cmd.code(((input, state) -> {
            splits.add(input);
            return Result.next(input);
        })));
        assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);

        doit.run();

        assertEquals("for-each should run 3 times entries:\n" + splits.stream().collect(Collectors.joining("\n")), 3, splits.size());

    }

    @Test
    public void yaml_declared() {
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("foreach",
                """
                scripts:
                  foo:
                  - for-each: SERVICE 'service1, service2, service3'
                    then:
                    - read-state: SERVICE
                hosts:
                  local: TARGET_HOST
                roles:
                  doit:
                    hosts: [local]
                    run-scripts: [foo]
                """.replaceAll("TARGET_HOST",getHost().toString())
        ));
        RunConfig config = builder.buildConfig(parser);
        Cmd target = config.getScript("foo");
        while (target.getNext() != null && !(target instanceof ForEach)) {
            target = target.getNext();
        }
        List<String> splits = new ArrayList<>();
        target.then(Cmd.code(((input, state) -> {
            splits.add(input);
            return Result.next(input);
        })));
        assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);

        doit.run();

        assertEquals("for-each should run 3 times entries:\n" + splits.stream().collect(Collectors.joining("\n")), 3, splits.size());
    }

    @Test //hanging
    public void forEach_ls1_loop() {
        List<String> lines = new ArrayList<>();
        AtomicBoolean tail = new AtomicBoolean(false);
        Script runScript = new Script("run");
        runScript
                .then(Cmd.sh("rm -rf /tmp/foo"))
                .then(Cmd.sh("mkdir /tmp/foo"))
                .then(Cmd.sh("echo \"one\" > /tmp/foo/one.txt"))
                .then(Cmd.sh("echo \"two\" > /tmp/foo/two.txt"))
                .then(Cmd.sh("echo \"three\" > /tmp/foo/three.txt"))
                .then(Cmd.sh("echo \"four\" > /tmp/foo/four.txt"))
                .then(Cmd.sh("ls -1 --color=none /tmp/foo"))
                .then(
                        Cmd.forEach("ARG")
                                .then(Cmd.code((input, state) -> {
                                    lines.add(input);
                                    return Result.next(input);
                                }))
                )
                .then(Cmd.code(((input, state) -> {
                    tail.set(true);
                    return Result.next(input);
                })))
                .then(Cmd.sh("rm -rf /tmp/foo"))
        ;

        RunConfigBuilder builder = getBuilder();
        builder.addScript(runScript);
        builder.addHostAlias("local", getHost().toString());
        builder.addHostToRole("role", "local");
        builder.addRoleRun("role", "run", new HashMap<>());

        RunConfig config = builder.buildConfig(Parser.getInstance());
        assertFalse("unexpected errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());

        Dispatcher dispatcher = new Dispatcher();

        Run run = new Run(tmpDir.toString(), config, dispatcher);

        JsonServer jsonServer = new JsonServer(Vertx.vertx(),run,31337);
        jsonServer.start();

        run.run();

        jsonServer.stop();

        assertEquals("lines contains 3 entries:\n" + lines, 4, lines.size());
        assertTrue("tail should be called", tail.get());
    }
}
