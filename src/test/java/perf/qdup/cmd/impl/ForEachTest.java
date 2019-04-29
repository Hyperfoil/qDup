package perf.qdup.cmd.impl;

import org.junit.Assert;
import org.junit.Test;
import perf.qdup.Run;
import perf.qdup.SshTestBase;
import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Dispatcher;
import perf.qdup.cmd.Result;
import perf.qdup.cmd.Script;
import perf.qdup.cmd.SpyContext;
import perf.qdup.config.CmdBuilder;
import perf.qdup.config.RunConfig;
import perf.qdup.config.RunConfigBuilder;
import perf.qdup.config.waml.WamlParser;
import perf.qdup.config.yaml.Parser;
import perf.yaup.Sets;
import perf.yaup.json.Json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class ForEachTest extends SshTestBase {

    @Test
    public void inject_then(){
        Script first = new Script("first");
        first.then(
            Cmd.forEach("FOO")
                .then(Cmd.sh("1"))
                .then(Cmd.sh("2"))
        );
        Script second = new Script("second");
        second.then(Cmd.sh("foo"));

        Cmd copy = first.deepCopy();
        second.injectThen(copy,null);

        Cmd firstCopy = second.getNext();

        assertTrue("first next should be for-each",firstCopy.getNext().toString().contains("for-each"));
        assertTrue("first skip is foo",firstCopy.getSkip().toString().contains("foo"));
        Cmd forEach = firstCopy.getNext();
        assertTrue("for-each skip is foo",forEach.getSkip().toString().contains("foo"));
    }

    //created for https://github.com/RedHatPerf/qDup/issues/8
    @Test
    public void getSkip_nullWhenLast(){
        Cmd forEach = Cmd.forEach("FOO")
            .then(Cmd.sh("1"))
            .then(Cmd.sh("2"));
        assertNull("forEach skip should be null\n"+forEach.tree(2,true),forEach.getSkip());
    }
    @Test
    public void getSkip_notNullIfNotLast(){
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

        assertEquals("for-each skip should be child\n"+forEach.tree(2,true),child,forEach.getSkip());
    }
    @Test
    public void run_defined_spaces(){
        Cmd forEach = Cmd.forEach("FOO","1 2");
        SpyContext context = new SpyContext();

        context.clear();
        forEach.run("",context);
        assertEquals("next","1",context.getNext());
        assertNull("skip not called",context.getSkip());

        context.clear();
        forEach.run("",context);
        assertEquals("next","2",context.getNext());
        assertNull("skip not called",context.getSkip());
        context.clear();
        forEach.run("",context);
        assertNull("next should be null",context.getNext());
        assertEquals("skip should be empty","",context.getSkip());
    }

    @Test
    public void run_defined_newlines(){
        Cmd forEach = Cmd.forEach("FOO","1\n2");
        SpyContext context = new SpyContext();

        context.clear();
        forEach.run("",context);
        assertEquals("next","1",context.getNext());
        assertNull("skip not called",context.getSkip());

        context.clear();
        forEach.run("",context);
        assertEquals("next","2",context.getNext());
        assertNull("skip not called",context.getSkip());
        context.clear();
        forEach.run("",context);
        assertNull("next should be null",context.getNext());
        assertEquals("skip should be empty","",context.getSkip());
    }

    @Test
    public void getTail_noTail(){
        Cmd start = Cmd.sh("one");
        Cmd tail = start.getTail();
        assertEquals("tail without children is self",start,tail);
    }

    @Test
    public void split_json_array_strings(){
        List<Object> split = ForEach.split("[\"one\",\"two\",\"three\"]");
        assertEquals("split should have 3 entires\n"+split.stream().map(o->o.getClass()+" "+o.toString()).collect(Collectors.joining()),3,split.size());
    }
    @Test
    public void split_json_array_strings_singlequote(){
        List<Object> split = ForEach.split("['one','two','three']");
        assertEquals("split should have 3 entires\n"+split.stream().map(o->o.getClass()+" "+o.toString()).collect(Collectors.joining()),3,split.size());
    }
    @Test
    public void split_json_array_objects(){
        List<Object> split = ForEach.split("[{value:'one'},{value:'two'},{value:'three'}]");
        assertEquals("split should have 3 entires\n"+split.stream().map(o->o.getClass()+" "+o.toString()).collect(Collectors.joining()),3,split.size());
        assertTrue("split[0] should be json\n"+split.stream().map(o->o.getClass()+" "+o.toString()).collect(Collectors.joining()),split.get(0) instanceof Json);
        assertTrue("split[1] should be json\n"+split.stream().map(o->o.getClass()+" "+o.toString()).collect(Collectors.joining()),split.get(1) instanceof Json);
        assertTrue("split[2] should be json\n"+split.stream().map(o->o.getClass()+" "+o.toString()).collect(Collectors.joining()),split.get(2) instanceof Json);
    }
    @Test
    public void split_json_object(){
        List<Object> split = ForEach.split("{one:'uno',two:'dos',three:'tres'}");
        assertEquals("split should have 3 entires\n"+split.stream().map(o->o.getClass()+" "+o.toString()).collect(Collectors.joining()),3,split.size());
        for(int i=0; i<3; i++){
                assertTrue("split["+i+"] should have key & value as keys",((Json)split.get(i)).keys().containsAll(Sets.of("key","value")));
            }
    }
    @Test
    public void split_newLine(){
        List<Object> split = ForEach.split("1\n2");
        assertEquals("two entires",2,split.size());
        assertEquals("1",split.get(0));
        assertEquals("2",split.get(1));
    }
    @Test
    public void split_space(){
        List<Object> split = ForEach.split("1 2");
        assertEquals("two entires",2,split.size());
        assertEquals("1",split.get(0));
        assertEquals("2",split.get(1));
    }
    @Test
    public void split_comma_space(){
        List<Object> split = ForEach.split("1 , 2");
        assertEquals("two entires",2,split.size());
        assertEquals("1",split.get(0));
        assertEquals("2",split.get(1));
    }
    @Test
    public void split_quoted_comma(){
        List<Object> split = ForEach.split("['1,1', 2]");
        assertEquals("two entires",2,split.size());
        assertEquals("1,1",split.get(0));
        assertEquals(2,split.get(1));
    }
    @Test
    public void split_comma(){
        List<Object> split = ForEach.split("service1, service2, service3");
        assertEquals("should have 3 entires\n"+split.stream().map(o->o.toString()).collect(Collectors.joining("\n")),3,split.size());
    }

    @Test
    public void then_injects_with_children(){
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

        Assert.assertEquals("2.next should be 2.1",true,two.getNext().toString().contains("2.1"));
        Assert.assertEquals("2.tail should be 2.2.2.2",true,twoTail.toString().contains("2.2.2.2"));
        //This was the original bug in repeat-until & for-each when their last child was a regex (something that often skips)
        assertTrue("2.skip should be for-each",two.getSkip().toString().contains("for-each"));
        //Assert.assertTrue("2.1");
        Assert.assertEquals("for-each.skip should be 3",true,forEach.getSkip().toString().contains("3"));

    }


    @Test
    public void forEach_loopCount(){
        List<String> lines = new ArrayList<>();
        AtomicBoolean tail = new AtomicBoolean(false);
        Script runScript = new Script("run");
        runScript
        .then(Cmd.code(((input, state) -> Result.next("one\ntwo\nthree"))))
        .then(
            Cmd.forEach("ARG")
                .then(Cmd.code((input,state)->{
                    lines.add(input);
                    return Result.next(input);
                }))
        )
        .then(Cmd.code(((input, state) -> {
            tail.set(true);
            return Result.next(input);
        })));

        RunConfigBuilder builder = new RunConfigBuilder(CmdBuilder.getBuilder());

        builder.addScript(runScript);
        builder.addHostAlias("local",getHost().toString());
        builder.addHostToRole("role","local");
        builder.addRoleRun("role","run",new HashMap<>());

        RunConfig config = builder.buildConfig();
        assertFalse("unexpected errors:\n"+config.getErrors().stream().collect(Collectors.joining("\n")),config.hasErrors());

        Dispatcher dispatcher = new Dispatcher();
        Run run = new Run("/tmp",config,dispatcher);
        run.run();

        assertEquals("lines contains 3 entries",3,lines.size());
        assertTrue("tail should be called",tail.get());
    }

    @Test
    public void forEach_ls_loop(){
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
                        .then(Cmd.code((input,state)->{
                            lines.add(input);
                            return Result.next(input);
                        }))
                )
                .then(Cmd.code(((input, state) -> {
                    tail.set(true);
                    return Result.next(input);
                })));

        RunConfigBuilder builder = new RunConfigBuilder(CmdBuilder.getBuilder());

        builder.addScript(runScript);
        builder.addHostAlias("local",getHost().toString());
        builder.addHostToRole("role","local");
        builder.addRoleRun("role","run",new HashMap<>());

        RunConfig config = builder.buildConfig();
        assertFalse("unexpected errors:\n"+config.getErrors().stream().collect(Collectors.joining("\n")),config.hasErrors());

        Dispatcher dispatcher = new Dispatcher();
        Run run = new Run("/tmp",config,dispatcher);
        run.run();

        assertEquals("lines contains 3 entries:\n"+lines,4,lines.size());
        assertTrue("tail should be called",tail.get());

    }

    @Test
    public void waml_state_from_with(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder(CmdBuilder.getBuilder());
        builder.loadYaml(parser.loadFile("foreach",stream(""+
              "scripts:",
           "  foo:",
           "  - for-each: SERVICE ${{FOO}}",
           "    - read-state: SERVICE",
           "hosts:",
           "  local:"+getHost(),
           "roles:",
           "  doit:",
           "    hosts: [local]",
           "    run-scripts: ",
           "    - foo: ",
           "        with:",
           "          FOO : server1,server2,server3"
        )));

        RunConfig config = builder.buildConfig();

        Cmd target = config.getScript("foo");
        while(target.getNext()!=null && !(target instanceof ForEach)){
            target = target.getNext();
        }
        List<String> splits = new ArrayList<>();
        if(target instanceof ForEach){
            target.then(Cmd.code(((input, state) -> {
                splits.add(input);
                return Result.next(input);
            })));
        }else {
            fail("failed to find for-each in script foo");
        }

        assertFalse("runConfig errors:\n"+config.getErrors().stream().collect(Collectors.joining("\n")),config.hasErrors());
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run("/tmp",config,dispatcher);

        doit.run();

        assertEquals("for-each should run 3 times entries:\n"+splits.stream().collect(Collectors.joining("\n")),3,splits.size());
    }

    @Test
    public void yaml_state_quoted(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder(CmdBuilder.getBuilder());
        builder.loadYaml(parser.loadFile("foreach",stream(""+
              "scripts:",
           "  foo:",
           "  - for-each: SERVICE ${{FOO}}",
           "    - read-state: SERVICE",
           "hosts:",
           "  local:"+getHost(),
           "roles:",
           "  doit:",
           "    hosts: [local]",
           "    run-scripts: [foo]",
           "states:",
           "  FOO: 'server1,server2,server3'"
        )));
        RunConfig config = builder.buildConfig();
        Cmd target = config.getScript("foo");
        while(target.getNext()!=null && !(target instanceof ForEach)){
            target = target.getNext();
        }
        List<String> splits = new ArrayList<>();
        target.then(Cmd.code(((input, state) -> {
            splits.add(input);
            return Result.next(input);
        })));
        assertFalse("runConfig errors:\n"+config.getErrors().stream().collect(Collectors.joining("\n")),config.hasErrors());
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run("/tmp",config,dispatcher);
        doit.run();
        assertEquals("for-each should not split quoted string:\n"+splits.stream().collect(Collectors.joining("\n")),1,splits.size());

    }
    @Test
    public void yaml_state(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder(CmdBuilder.getBuilder());
        builder.loadYaml(parser.loadFile("foreach",stream(""+
           "scripts:",
           "  foo:",
           "  - for-each: SERVICE ${{FOO}}",
           "    - read-state: SERVICE",
           "hosts:",
           "  local:"+getHost(),
           "roles:",
           "  doit:",
           "    hosts: [local]",
           "    run-scripts: [foo]",
           "states:",
           "  FOO: server1,server2,server3"
        )));
        RunConfig config = builder.buildConfig();
        Cmd target = config.getScript("foo");
        while(target.getNext()!=null && !(target instanceof ForEach)){
            target = target.getNext();
        }
        List<String> splits = new ArrayList<>();
        target.then(Cmd.code(((input, state) -> {
            splits.add(input);
            return Result.next(input);
        })));
        assertFalse("runConfig errors:\n"+config.getErrors().stream().collect(Collectors.joining("\n")),config.hasErrors());
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run("/tmp",config,dispatcher);

        doit.run();

        assertEquals("for-each should run 3 times entries:\n"+splits.stream().collect(Collectors.joining("\n")),3,splits.size());

    }

    @Test
    public void yaml_declared(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = new RunConfigBuilder(CmdBuilder.getBuilder());
        builder.loadYaml(parser.loadFile("foreach",stream(""+
              "scripts:",
           "  foo:",
           "  - for-each: SERVICE 'service1, service2, service3'",
           "    - read-state: SERVICE",
           "hosts:",
           "  local:"+getHost(),
           "roles:",
           "  doit:",
           "    hosts: [local]",
           "    run-scripts: [foo]"
        )));
        RunConfig config = builder.buildConfig();
        Cmd target = config.getScript("foo");
        while(target.getNext()!=null && !(target instanceof ForEach)){
            target = target.getNext();
        }
        List<String> splits = new ArrayList<>();
        target.then(Cmd.code(((input, state) -> {
            splits.add(input);
            return Result.next(input);
        })));
        assertFalse("runConfig errors:\n"+config.getErrors().stream().collect(Collectors.joining("\n")),config.hasErrors());
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run("/tmp",config,dispatcher);

        doit.run();

        assertEquals("for-each should run 3 times entries:\n"+splits.stream().collect(Collectors.joining("\n")),3,splits.size());
    }

    @Test
    public void forEach_ls1_loop(){
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
                        .then(Cmd.code((input,state)->{
                            lines.add(input);
                            return Result.next(input);
                        }))
                )
                .then(Cmd.code(((input, state) -> {
                    tail.set(true);
                    return Result.next(input);
                })))
                .then(Cmd.sh("rm -r /tmp/foo"))
        ;

        RunConfigBuilder builder = new RunConfigBuilder(CmdBuilder.getBuilder());

        builder.addScript(runScript);
        builder.addHostAlias("local",getHost().toString());
        builder.addHostToRole("role","local");
        builder.addRoleRun("role","run",new HashMap<>());

        RunConfig config = builder.buildConfig();
        assertFalse("unexpected errors:\n"+config.getErrors().stream().collect(Collectors.joining("\n")),config.hasErrors());

        Dispatcher dispatcher = new Dispatcher();
        Run run = new Run("/tmp",config,dispatcher);
        run.run();

        assertEquals("lines contains 3 entries:\n"+lines,4,lines.size());
        assertTrue("tail should be called",tail.get());
    }
}
