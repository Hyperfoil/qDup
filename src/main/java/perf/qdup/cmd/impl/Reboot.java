package perf.qdup.cmd.impl;

import perf.qdup.Run;
import perf.qdup.SshSession;
import perf.qdup.cmd.*;
import perf.qdup.config.CmdBuilder;
import perf.qdup.config.RunConfig;
import perf.qdup.config.RunConfigBuilder;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Reboot extends Cmd {


    private long timeout;
    private String target;
    private String password;


    public Reboot(String timeout,String target,String password){
        this(Sleep.parseToMs(timeout),target,password);
    }
    public Reboot(long timeout,String target,String password){
        this.timeout = timeout;
        this.target = target;
        this.password = password;
    }

    @Override
    public void run(String input, Context context, CommandResult result) {
        SshSession session = context.getSession();
        session.clearCommand();

        if(target!=null && !target.isEmpty()){

            //TODO check if reboot is not necessary?
            logger.info("{} reboot to {}",session.getHost().getHostName(),target);

            //requires root...
            session.sh("whoami");
            String whoami = session.getOutput();
            if(!"root".equals(whoami)){
                logger.info("{} su ",session.getHost().getHostName());
                Map<String,String> prompts = new HashMap<>();
                prompts.put("Password: ",password);
                session.sh("su",prompts);
                session.getOutput();//to wait for su to finish
            }

            session.sh("awk -F\\' '$1==\"menuentry \" {print $2}' /etc/grub2-efi.cfg");
            List<String> kernels = Arrays.asList(session.getOutput().trim().split("\r?\n"));
            logger.info("{} kernels:\n  {}",session.getHost().getHostName(),
                    IntStream.range(0,kernels.size()).mapToObj(i->{
                        return i+" : "+kernels.get(i);
                    }).collect(Collectors.joining("\n  "))
            );
            int targetIndex=-1;

            if(target.matches("\\d+")){
                logger.info("{} target kernel index",session.getHost().getHostName());
                int index = Integer.parseInt(target);
                if(targetIndex>=kernels.size()){
                    logger.error("{} Reboot found {} kernel(s), target = {}",session.getHost().getHostName(),kernels.size(),targetIndex);
                    //TODO invoke the next command or abort?
                    context.abort();
                }else{
                    targetIndex = index;
                }
            }else{
                logger.info("{} target kernel version");

                int index = IntStream.range(0, kernels.size())
                    .filter(i -> {
                        return kernels.get(i).contains(this.target);
                    }).findFirst().orElse(-1);

                logger.info("{} kernel {} at index={}",session.getHost().getHostName(),this.target,index);

                if(index<0){
                    logger.error("{} failed to find kernel = {}",session.getHost().getHostName(),this.target);
                    context.abort();
                }else{
                    targetIndex = index;
                }
            }

            if(targetIndex>=0){
                logger.info("{} grub2-reboot {}",session.getHost().getHostName(),targetIndex);
                session.sh("grub2-reboot "+targetIndex);
            }else{
                //should not be able to get here
            }
        }//end of setting the target
        logger.info("{} reboot",session.getHost().getHostName());
        session.reboot();

        long startMillis = System.currentTimeMillis();
        long currentMillis = System.currentTimeMillis();
        long interval = this.timeout/5;
        do {
            try {
                TimeUnit.MILLISECONDS.sleep(interval);
            } catch (InterruptedException e) {
                //e.printStackTrace(); //TODO what to do with interrupted Reboot?
            }
            System.out.println("session.isOpen ? "+session.isOpen());
            logger.info("{} retry @ {} for {}", Instant.now().toString(),interval);
            session.connect(interval);
            System.out.println("session.isOpen ? "+session.isOpen());
            currentMillis = System.currentTimeMillis();
        } while (!session.isOpen() && currentMillis - startMillis < this.timeout);
        if(!session.isOpen()){
            logger.error("{} failed to reconnect after reboot",session.getHost().getHostName());
            context.abort();
        }else {
            logger.info("{} reconnected",session.getHost().getHostName());
            context.setStartEnv();
        }
        result.next(this,input);
    }

    @Override
    public Cmd copy() {
        return new Reboot(this.timeout,this.target,this.password);
    }
    
}
