package perf.ssh.benchmark;

import perf.ssh.Host;
import perf.ssh.Run;
import perf.ssh.State;
import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.CommandDispatcher;
import perf.ssh.cmd.Result;
import perf.util.AsciiArt;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by wreicher
 */
public class Test {


    public static void main(String[] args) {
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();

        final AtomicInteger factoryCounter = new AtomicInteger(0);
        final AtomicInteger scheduledCounter = new AtomicInteger(0);

        ThreadFactory factory = r -> new Thread(r,"PT-"+factoryCounter.getAndIncrement());
        ThreadPoolExecutor executor = new ThreadPoolExecutor(8,24,30, TimeUnit.MINUTES,workQueue,factory);
        ScheduledThreadPoolExecutor scheduled = new ScheduledThreadPoolExecutor(24, runnable -> new Thread(runnable,"scheduled-"+scheduledCounter.getAndIncrement()));
        CommandDispatcher dispatcher = new CommandDispatcher(executor,scheduled);

        dispatcher.addObserver(new CommandDispatcher.Observer() {
            @Override
            public void onStart(Cmd command) {}

            @Override
            public void onNext(Cmd command,String output){}
            @Override
            public void onSkip(Cmd command,String input){}


            @Override
            public void onStop(Cmd command) {}

            @Override
            public void onStart() {
                System.out.println(AsciiArt.ANSI_DARK_GREY+"starting "+AsciiArt.ANSI_RESET);
            }

            @Override
            public void onStop() {
                System.out.println(AsciiArt.ANSI_DARK_GREY+"stopping "+AsciiArt.ANSI_RESET);
            }
        });

            Run run = new Run("test","/tmp/test-delayed-"+System.currentTimeMillis(),dispatcher);

            State runState = run.getState();

            runState.setRun("EAP_HOME","/home/wreicher/runtime/jboss-eap-7.1.0.DR17-quickstart");
            //runState.setRun("EAP_HOME","/home/benchuser/runtime/jboss-eap-7.1.0.ER1-jdbc");
            //runState.setRun("EAP_HOME","/home/benchuser/runtime/jboss-eap-7.x.patched");
            runState.setRun("ENABLE_JFR","false");
            runState.setRun("JFR_SETTINGS","lowOverhead");




            //run.getState().setRun("STANDALONE_SH_ARGS","-c standalone-full-ha-queues.xml -b 0.0.0.0");
            runState.setRun("STANDALONE_XML","standalone.xml");
            //runState.setRun("STANDALONE_XML","standalone-full-ha-jdbc-store.xml");
            runState.setRun("STANDALONE_SH_ARGS","-b 0.0.0.0");

            Host local = new Host("wreicher","laptop");
            Host server4 = new Host("benchuser","benchserver4");


            run.getRepo().getScript("vmstat")
                    .then(Cmd.sh("tail -f /tmp/foo.txt")
                        .watch(Cmd.echo())
                        .watch(Cmd.regex(".*end.*")
                            .then(Cmd.ctrlC()))
                    )
                    .then(Cmd.echo());


            run.getRepo().getScript("repeat")
                    .then(Cmd.repeatUntil("DONE")
                        .then(Cmd.sleep(2_000))
                        .then(Cmd.log("repeated"))
                    ).then(Cmd.log("done repeating"));

            run.getRepo().getScript("done")
                    .then(Cmd.sleep(10_000))
                    .then(Cmd.signal("DONE"));

            run.getRole("test").add(local).addRunScript(run.getRepo().getScript("repeat"));
            run.getRole("test").addRunScript(run.getRepo().getScript("done"));

            System.out.println("run.run");
            run.run();
            dispatcher.shutdown();//because the run doesn't own the dispatcher atm
            System.out.println("run.ran :)");


        System.out.println(AsciiArt.ANSI_GREEN+"executor.SHUTDOWNNOW"+AsciiArt.ANSI_RESET);
        List<Runnable> runnables = executor.shutdownNow();
        for(Runnable runnable : runnables){
            System.out.println(runnable.getClass());
        }
        scheduled.shutdownNow();

        System.out.println("run finished");
        System.out.println(dispatcher.getActiveCount());

    }
}
