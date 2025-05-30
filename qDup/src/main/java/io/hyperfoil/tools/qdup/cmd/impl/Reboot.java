package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;
import io.hyperfoil.tools.qdup.shell.AbstractShell;

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
    public void run(String input, Context context) {
        AbstractShell session = context.getShell();
        if(target!=null && !target.isEmpty()){

            //TODO check if reboot is not necessary?
            logger.infof("%s reboot to %s",session.getHost().getHostName(),target);

            //requires root...
            String whoami = session.shSync("whoami");
            if(!"root".equals(whoami)){
                logger.infof("%s su ",session.getHost().getHostName());
                Map<String,String> prompts = new HashMap<>();
                prompts.put("Password: ",password);
                session.shSync("su",prompts);

            }
            String grubFile = session.shSync("ls /etc/grub*.cfg");//get the grub cfg file
            String kernelString = session.shSync("awk -F\\' '$1==\"menuentry \" {print $2}' "+grubFile);
            List<String> kernels = Arrays.asList(kernelString.trim().split("\r?\n"));
            logger.infof("%s kernels:\n  %s",session.getHost().getHostName(),
                    IntStream.range(0,kernels.size()).mapToObj(i->{
                        return i+" : "+kernels.get(i);
                    }).collect(Collectors.joining("\n  "))
            );
            int targetIndex=-1;

            if(target.matches("\\d+")){
                logger.infof("%s target kernel index",session.getHost().getHostName());
                int index = Integer.parseInt(target);
                if(index>=kernels.size()){
                    logger.errorf("%s Reboot found %s kernel(s), target = %s",session.getHost().getHostName(),kernels.size(),targetIndex);
                    //TODO invoke the next command or abort?
                    context.abort(false);
                }else{
                    targetIndex = index;
                }
            }else{
                logger.info("{} target kernel version");

                int index = IntStream.range(0, kernels.size())
                    .filter(i -> {
                        return kernels.get(i).contains(this.target);
                    }).findFirst().orElse(-1);

                logger.infof("%s kernel %s at index=%s",session.getHost().getHostName(),this.target,index);

                if(index<0){
                    logger.errorf("%s failed to find kernel = {}",session.getHost().getHostName(),this.target);
                    context.abort(false);
                }else{
                    targetIndex = index;
                }
            }

            if(targetIndex>=0){
                logger.infof("%s grub2-reboot %s",session.getHost().getHostName(),targetIndex);
                session.sh("grub2-reboot "+targetIndex);
            }else{
                //should not be able to get here
            }
        }//end of setting the target
        logger.infof("%s reboot",session.getHost().getHostName());
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
            logger.infof("%s retry @ %s for %s", Instant.now().toString(),interval);
            session.connect();
            //session.connect(interval,"",false);//TODO setupEnv
            currentMillis = System.currentTimeMillis();
        } while (!session.isOpen() && currentMillis - startMillis < this.timeout);
        if(!session.isOpen()){
            logger.errorf("%s failed to reconnect after reboot",session.getHost().getHostName());
            context.abort(false);
        }else {
            logger.infof("%s reconnected",session.getHost().getHostName());
            //TODO context.setStartEnv();
        }
        context.next(input);
    }

    @Override
    public Cmd copy() {
        return new Reboot(this.timeout,this.target,this.password);
    }
    
}
