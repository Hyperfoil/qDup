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
        ThreadFactory factory = r -> new Thread(r,"PT-"+factoryCounter.getAndIncrement());
        ThreadPoolExecutor executor = new ThreadPoolExecutor(8,24,30, TimeUnit.MINUTES,workQueue,factory);
        CommandDispatcher dispatcher = new CommandDispatcher(executor);

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

        for(int i=0; i<1; i++){
            Run run = new Run("test","/tmp/test-delayed-"+System.currentTimeMillis(),dispatcher);

            State runState = run.getState();

            //run.getState().setRun("EAP_HOME","/home/wreicher/runtime/jboss-eap-7.1.0.DR17-quickstart");
            //runState.setRun("EAP_HOME","/home/benchuser/runtime/jboss-eap-7.1.0.ER1-jdbc");
            runState.setRun("EAP_HOME","/home/benchuser/runtime/jboss-eap-7.x.patched");
            runState.setRun("ENABLE_JFR","true");
            runState.setRun("JFR_SETTINGS","lowOverhead");

            //run.getState().setRun("STANDALONE_SH_ARGS","-c standalone-full-ha-queues.xml -b 0.0.0.0");
            //runState.setRun("STANDALONE_XML","standalone.xml");
            runState.setRun("STANDALONE_XML","standalone-full-ha-jdbc-store.xml");
            runState.setRun("STANDALONE_SH_ARGS","-b 0.0.0.0");

            Host local = new Host("wreicher","w520");
            Host server4 = new Host("benchuser","benchserver4");

            run.getRepo().getScript("eap")
                    .then(Cmd.log("hi mom"))
                    .then(Cmd.sh("cd ${{EAP_HOME}}"))
                    .then(Cmd.sh("rm /tmp/eap7.standalone.console.log"))
                    .then(Cmd.sh("rm standalone/log/*"))
//                    .then(Cmd.xpath("${{EAP_HOME}}/standalone/configuration/${{STANDALONE_XML}}>/server/interfaces/interface[@name=\"public\"]/@value == foo"))
                    .then(Cmd.sh("sed -i '/enable_jfr=/c\\enable_jfr=${{ENABLE_JFR}}' ${{EAP_HOME}}/bin/standalone.conf"))
                    .then(Cmd.sh("sed -i '/jfr_settings=/c\\jfr_settings=\"${{JFR_SETTINGS}}\"' ${{EAP_HOME}}/bin/standalone.conf"))
                    .then(Cmd.queueDownload("${{EAP_HOME}}/standalone/configuration/${{STANDALONE_XML}}"))
                    .then(Cmd.queueDownload("/tmp/eap7.standalone.console.log"))
                    .then(Cmd.queueDownload("${{EAP_HOME}}/standalone/log/*"))
                    .then(Cmd.queueDownload("${{EAP_HOME}}/bin/standalone.sh"))
                    .then(Cmd.queueDownload("${{EAP_HOME}}/bin/standalone.conf"))
                    .then(Cmd.signal("SERVER_STARTING"))
                    .then(Cmd.sh("./bin/standalone.sh -c ${{STANDALONE_XML}} ${{STANDALONE_SH_ARGS}} > /tmp/eap7.standalone.console.log 2>/dev/null & export EAP_SCRIPT_PID=\"$!\""))
                    .then(Cmd.echo())
                    .then(Cmd.sh("export EAP_PID=$(jps | grep \"jboss-modules.jar\" | cut -d \" \" -f1)"))
                    .then(Cmd.sh("echo \"pid = ${EAP_PID}\"")
                            .then(Cmd.echo())
                    )
                    .then(Cmd.sleep(1_000))//because otherwise we try to tail standalone/log/server.log before it exists
                    .then(Cmd.sh("jps -lvm").then(Cmd.echo()))
                    .then(Cmd.sh("tail -f standalone/log/server.log")
                            .watch(Cmd.echo())
                            .watch(Cmd.regex(".*? WFLYSRV0025: (?<eapVersion>.*?) started in (?<eapStartTime>\\d+)ms.*")
                                .then(Cmd.ctrlC())//end the tail
                                .then(Cmd.log("eap ${{eapVersion}} started in ${{eapStartTime}}"))
                                .then(Cmd.signal("SERVER_STARTED"))
                            )
                            .watch(Cmd.regex(".*? WFLYSRV0026: .*")
                                    .then(Cmd.ctrlC())
                                    .then(Cmd.abort("eap failed to start cleanly"))
                            )
                            .watch(Cmd.regex(".*?FATAL.*")
                                .then(Cmd.ctrlC())
                            )
                    )
                    .then(Cmd.sh("jps").then(Cmd.echo()))
                    .then(Cmd.sh("grep --color=none \"javaOpts\\|JAVA_OPTS\" /tmp/eap7.standalone.console.log")
                            .then(Cmd.regex(".*? -XX:StartFlightRecording.*?filename=(?<eapJfrFile>[^\\s,]+).*")
                                    .then(Cmd.log("jfrFile = ${{eapJfrFile}}"))
                                    .then(Cmd.queueDownload("${{eapJfrFile}}"))
                            )
                            .then(Cmd.regex(".*? -Xloggc:(?<eapGcFile>\\S+).*")//TODO handle the case where gc file has % params
                                    .then(Cmd.code((input,state)->{
                                        String eapGcFile = state.get("eapGcFile");
                                        if(eapGcFile!=null && eapGcFile.indexOf("%")>-1) {
                                            state.setScript("eapGcFile",eapGcFile.substring(0,eapGcFile.indexOf("%")));
                                            return Result.next(input);
                                        }else{
                                            return Result.skip(input);
                                        }
                                    })
                                            .then(Cmd.sh("lsof -p ${EAP_PID} | grep --color=none \"${{eapGcFile}}\"")
                                                    .then(Cmd.regex(".*? (?<eapGcFile>/.+?)[\r\n]+")
                                                        .then(Cmd.echo())
                                                        .then(Cmd.log(" updated eapGCFile=${{eapGcFile}}"))

                                                    )
                                            )
                                    )
                                    .then(Cmd.log("eapGCFile ${{eapGcFile}}"))
                                    .then(Cmd.queueDownload("${{eapGcFile}}"))
                            )

                    )
                    .then(Cmd.sleep(1_000))
                    .then(Cmd.sh("kill ${EAP_PID}"))
                    .then(Cmd.sleep(5_000))
                    .then(Cmd.sh("tail -f ./standalone/log/server.log")
                            .watch(Cmd.regex(".*? WFLYSRV0050.*")//wait for server stopped
                                    .then(Cmd.ctrlC())
                            )
                    )
//                    .then(Cmd.signal("SERVER_STOPPED"))
                    .then(Cmd.signal("RUN_STOPPED"))
            ;




        run.getRepo().getScript("dstat")
                    .then(Cmd.sh("dstat -Tcdngy 1 > /tmp/dstat.log 2>/dev/null & export DSTAT_PID=\"$!\""))
                    .then(Cmd.sh("echo \"pid ${DSTAT_PID}\""))
                    .then(Cmd.waitFor("RUN_STOPPED"))
                    .then(Cmd.sh("kill ${DSTAT_PID}"))
                    .then(Cmd.download("/tmp/dstat.log"))
                    .then(Cmd.sh("rm /tmp/dstat.log"));

            run.getRepo().getScript("test")
                    .then(Cmd.sleep(10_000))
                    .then(Cmd.signal("RUN_STOPPED"));

            run.getRepo().getScript("local-setup")
                    //.then(Cmd.sh("sudo ntpdate -u clock.redhat.com"))
                    .then(Cmd.sh("pwd"))
            ;
            run.getRepo().getScript("test-lsof")
                    .then(Cmd.sh("lsof").
                            then(Cmd.echo())
                    );

            run.getRole("test").add(server4).addRunScript(run.getRepo().getScript("eap"));
            //run.allHosts().addRunScript(run.getRepo().getScript("dstat"));
            //run.allHosts().addSetupScript(run.getRepo().getScript("local-setup"));



            System.out.println("run.run");
            run.run();
            dispatcher.shutdown();//because the run doesn't own the dispatcher atm
            System.out.println("run.ran :)");

        }

        System.out.println(AsciiArt.ANSI_GREEN+"executor.SHUTDOWNNOW"+AsciiArt.ANSI_RESET);
        List<Runnable> runnables = executor.shutdownNow();
        for(Runnable runnable : runnables){
            System.out.println(runnable.getClass());
        }


        System.out.println("run finished");
        System.out.println(dispatcher.getActiveCount());

    }
}
