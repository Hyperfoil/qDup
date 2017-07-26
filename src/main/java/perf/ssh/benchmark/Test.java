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

        run.getRepo().getScript("jstack")
                .then(Cmd.waitFor("SERVER_STARTED"))
                .then(Cmd.sh("export JSTACK_PID=$(jps -v | grep \"jboss-modules.jar\" | cut -d \" \" -f1)")
                        .then(Cmd.sh("echo ${JSTACK_PID}")
                                .then(Cmd.code((input,state)->{state.setScript("JSTACK_PID",input.trim()); return Result.next(input);}))
                        )
                        .then(Cmd.log("JSTACK_PID=[${{JSTACK_PID}}]"))
                )
                .then(Cmd.queueDownload("/tmp/${{JSTACK_PID}}.jstack.*"))
                .then(Cmd.repeatUntil("SERVER_STOPPED")
                        .then(Cmd.sh("jstack ${{JSTACK_PID}} > /tmp/jstack.${{JSTACK_PID}}.`date +%Y%m%d_%H%M%S`.txt"))
                        .then(Cmd.sleep(3_000))
                )
        ;


        run.getRepo().getScript("eap")
                .then(Cmd.sh("cd ${{EAP_HOME}}"))
                .then(Cmd.sh("rm /tmp/eap7.standalone.console.log"))
                .then(Cmd.sh("rm ./standalone/log/*"))
                .then(Cmd.queueDownload("/tmp/eap7.standalone.console.log"))
                .then(Cmd.queueDownload("${{EAP_HOME}}/standalone/log/*"))
                .then(Cmd.queueDownload("${{EAP_HOME}}/bin/standalone.sh"))
                .then(Cmd.queueDownload("${{EAP_HOME}}/bin/standalone.conf"))
                .then(Cmd.queueDownload("${{EAP_HOME}}/standalone/configuration/${{STANDALONE_XML}}"))
                .then(Cmd.signal("SERVER_STARTING"))
                .then(Cmd.sh("./bin/standalone.sh -c ${{STANDALONE_XML}} ${{STANDALONE_SH_ARGS}} > /tmp/eap7.standalone.console.log 2>/dev/null & ")
                        .then(Cmd.echo())
                )
                .then(Cmd.sh("export SERVER_PID=$(jps | grep \"jboss-modules.jar\" | cut -d \" \" -f1)")
                        .then(Cmd.sh("echo ${SERVER_PID}")
                                .then(Cmd.code((input,state)->{
                                    state.setRun("SERVER_PID",input.trim());
                                    return Result.next(input);
                                }))
                        )
                )
                .then(Cmd.sleep(1_000))//because otherwise we try to tail standalone/log/server.log before it exists
                .then(Cmd.sh("tail -f ./standalone/log/server.log")
                        .watch(Cmd.regex(".*? WFLYSRV0025: (?<eapVersion>.*?) started in (?<eapStartTime>\\d+)ms.*")
                                .then(Cmd.ctrlC())//end the tail
                                .then(Cmd.log("eap ${{eapVersion}} started in "+AsciiArt.ANSI_GREEN+"${{eapStartTime}}"+AsciiArt.ANSI_RESET))
                                .then(Cmd.signal("SERVER_STARTED"))
                        )
                        .watch(Cmd.regex(".*? WFLYSRV0026: .*")
                                .then(Cmd.ctrlC())
                                .then(Cmd.abort("eap failed to start cleanly"))
                        )
                        .watch(Cmd.regex(".*?FATAL.*")
                                .then(Cmd.log(AsciiArt.ANSI_RED+"FATAL"+AsciiArt.ANSI_RESET))
                                .then(Cmd.echo())
                                .then(Cmd.ctrlC())
                        )
                )
                .then(Cmd.sh("grep --color=none \"javaOpts\\|JAVA_OPTS\" /tmp/eap7.standalone.console.log")
                        .then(Cmd.regex(".*? -Xloggc:(?<gcFile>\\S+).*")
                                .then(Cmd.code((input,state)->{
                                            String gcFile = state.get("gcFile");
                                            if(gcFile!=null && gcFile.indexOf("%")>-1) {
                                                state.setScript("gcFile",gcFile.substring(0,gcFile.indexOf("%")));
                                                return Result.next(input);
                                            }else{
                                                return Result.skip(input);
                                            }
                                        })
                                                .then(Cmd.sh("lsof -p ${SERVER_PID} | grep --color=none \"${{gcFile}}\"")
                                                        //added [\r\n]+ because otherwise rsync appends \#015 to the file name
                                                        .then(Cmd.regex(".*? (?<gcFile>/.+?)[\r\n]+")
                                                                .then(Cmd.log(" updated gcFile=${{gcFile}}"))
                                                        )
                                                )
                                )
                                .then(Cmd.log("gcFile=${{gcFile}}"))
                                .then(Cmd.queueDownload("${{gcFile}}"))
                        )
                        .then(Cmd.regex(".*? -XX:StartFlightRecording.*?filename=(?<jfrFile>[^\\s,]+).*")
                                .then(Cmd.queueDownload("${{jfrFile}}"))
                        )

                )
                .then(Cmd.log("SERVER_STARTED"))
                .then(Cmd.sleep(20_000))
                .then(Cmd.sh("kill ${SERVER_PID}"))
                .then(Cmd.sleep(4_000))
//            .then(Cmd.sh("tail -f ./standalone/log/server.log")
//                .watch(Cmd.regex(".*? WFLYSRV0050 .*")//wait for server stopped
//                    .then(Cmd.ctrlC())
//                )
//            )
                .then(Cmd.signal("SERVER_STOPPED"));


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
                );

        System.out.println(run.getRepo().getScript("eap").tree(2));



        run.getRepo().getScript("done")
            .then(Cmd.sleep(10_000))
            .then(Cmd.signal("DONE"));

        run.getRole("test").add(local).addRunScript(run.getRepo().getScript("jstack"));
        run.getRole("test").addRunScript(run.getRepo().getScript("eap"));

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
