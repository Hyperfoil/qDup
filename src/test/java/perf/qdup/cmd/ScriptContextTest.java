package perf.qdup.cmd;

import org.junit.Test;
import perf.qdup.Profiles;
import perf.qdup.Run;
import perf.qdup.SshTestBase;
import perf.qdup.State;
import perf.qdup.config.CmdBuilder;
import perf.qdup.config.RunConfig;
import perf.qdup.config.RunConfigBuilder;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ScriptContextTest extends SshTestBase {

    private class UpdateCmd extends Cmd {
        private final int count;
        UpdateCmd(int count){
            this.count = count;
        }

        @Override
        public void run(String input, Context context) {
            for(int i=0; i<count; i++){
                context.update(""+i);
            }
            context.next(""+count);
        }

        @Override
        public Cmd copy() {
            return new UpdateCmd(this.count);
        }
    }

    private ScriptContext getContext(Cmd command){

        RunConfigBuilder builder = new RunConfigBuilder(CmdBuilder.getBuilder());
        RunConfig runConfig = builder.buildConfig();
        Run run = new Run(
                "/tmp/",
            runConfig,
            new Dispatcher()

        );

        ScriptContext context = new ScriptContext(
            getSession(),
            new State(""),
            run,
            new Profiles().get("ScriptContextTest"),
            command
        );
        return context;
    }

    @Test
    public void invoke_single_watcher(){
        Cmd toRun = new UpdateCmd(5);
        List<String> lines = new LinkedList<>();
        toRun.watch(Cmd.code((input, state) -> {
            lines.add(input);
            return Result.next(input);
        }));
        ScriptContext context = getContext(toRun);
        context.run();
        try {
            //Wait for the executor to finish
            Thread.sleep(5_000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assertEquals("expect 5 lines of output:"+lines,5,lines.size());
    }
    @Test
    public void invoke_watcher_chain(){
        Cmd toRun = new UpdateCmd(5);
        AtomicBoolean sawTwo = new AtomicBoolean(false);
        AtomicBoolean calledChild = new AtomicBoolean(false);
        toRun.watch(Cmd.code((input,state)->{
            if("2".equals(input)){
                sawTwo.set(true);
                return Result.next(input);
            }else{
                return Result.skip(input);
            }
        }).then(Cmd.code((input,state)->{
            calledChild.set(true);
            return Result.next(input);
        })));

        ScriptContext context = getContext(toRun);

        context.run();

        assertTrue("expect to see 2",sawTwo.get());
        assertTrue("expect to invoke child of sawTwo",calledChild.get());
    }

    @Test
    public void invoke_multiple_timeout(){
        int timeout = 1_000;
        int mult = 5;
        Cmd toRun = Cmd.code((line,state)->{
            try {
                //Sleep in command to ensure timeout is not blocked by blocking tasks
                Thread.sleep(mult*timeout);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return Result.next(line);
        });
        AtomicLong timeA = new AtomicLong(0);
        AtomicBoolean calledA = new AtomicBoolean(false);
        toRun.addTimer(timeout, Cmd.code((line,state)->{
            calledA.set(true);
            timeA.set(System.currentTimeMillis());
            return Result.next(line);
        }));
        AtomicLong timeB = new AtomicLong(0);
        AtomicBoolean calledB = new AtomicBoolean(false);
        toRun.addTimer(2*timeout, Cmd.code((line,state)->{
            calledB.set(true);
            timeB.set(System.currentTimeMillis());
            return Result.next(line);
        }));

        ScriptContext context = getContext(toRun);

        context.run();
        assertTrue("A timer should be called",calledA.get());
        assertTrue("B timer should be called",calledB.get());
        assertTrue("A should be called before B but A="+timeA.get()+" and B="+timeB.get(),timeA.get() < timeB.get());

    }
    @Test
    public void invoke_multiple_watcher(){
        int count=5;
        Cmd toRun = new UpdateCmd(count);
        List<String> lines = new LinkedList<>();
        toRun.watch(Cmd.code((input, state) -> {
            lines.add("first="+input);
            return Result.skip(input);
            //skip should not prevent other watchers
        }));
        toRun.watch(Cmd.code((input, state) -> {
            lines.add("second="+input);
            return Result.skip(input);
        }));
        toRun.watch(Cmd.code((input, state) -> {
            lines.add("third="+input);
            return Result.next(input);
        }));
        ScriptContext context = getContext(toRun);
        context.run();
        assertEquals("expect 3 watchers to see all "+count+" lines",3*count,lines.size());
        List<String> prefixes = Arrays.asList("first","second","third");
        for(int i=0; i<3*count; i++){
            String prefix = prefixes.get(i%3);
            assertTrue("expect "+i+" output to come from "+prefix,lines.get(i).startsWith(prefix));
        }
    }

    @Test
    public void observer_update(){
        int count=5;
        Cmd toRun = new UpdateCmd(count);
        ScriptContext context = getContext(toRun);
        List<String> updates = new LinkedList<>();
        context.setObserver(new ContextObserver() {
            @Override
            public void onUpdate(ScriptContext context, Cmd command, String output) {
                updates.add(output);
            }
        });
        context.run();
        assertEquals("expect "+count+" updates",count,updates.size());
    }
}
