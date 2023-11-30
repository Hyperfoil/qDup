package io.hyperfoil.tools.qdup.shell;

import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.SecretFilter;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.stream.MultiStream;
import io.hyperfoil.tools.qdup.stream.SessionStreams;
import io.hyperfoil.tools.yaup.AsciiArt;
import io.hyperfoil.tools.yaup.PopulatePatternException;
import io.hyperfoil.tools.yaup.StringUtil;
import io.hyperfoil.tools.yaup.json.Json;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * This shell is a direct connection to a local shell process.
 * The process is started with a ProcessBuilder and a dedicated thread for reading the input stream and passing 
 * bytes to the associated SessionStream.
 */
public class LocalShell extends AbstractShell{

    private Thread readerThread;
    private Process shellProcess;

    private final AtomicInteger connectCounter = new AtomicInteger(0);

    public LocalShell(Host host, String setupCommand, ScheduledThreadPoolExecutor executor, SecretFilter filter, boolean trace) {
        super(host, setupCommand, executor, filter, trace);
    }

    @Override
    void updateSessionStream(SessionStreams sessionStreams){
        //nothing to do when the sessionstream changes
    }
    @Override
    PrintStream connectShell() {
        PrintStream rtrn = null;
        if(shellProcess!=null){//cleanup previous connection remnants
            close();
        }
        if(!getHost().hasConnectShell()){
            //an invalid host should not reach this point
        }else{
            Json json = new Json();
            json.set("host",getHost().toJson());
            List<String> newPopulated = Cmd.populateList(json,getHost().getConnectShell());
            if(Cmd.hasPatternReference(newPopulated,StringUtil.PATTERN_PREFIX)){
                //how do we fail
            }else{
                ProcessBuilder pb = new ProcessBuilder(newPopulated.toArray(new String[0]));
                pb.environment().put("TERM","xterm");
                pb.directory(new File(System.getProperty("user.home")));
                pb.redirectErrorStream(true);
                try {
                    shellProcess = pb.start();
                    rtrn = new PrintStream(shellProcess.getOutputStream());
                } catch (IOException e) {
                    logger.error(getName()+" failed to start shell process",e);
                    return null;
                }
                int count = connectCounter.getAndIncrement() + 1;
                readerThread = new Thread(()->{
                    InputStream in = shellProcess.getInputStream();
                    byte[] buff = new byte[10 * 1024];
                    //TODO this is not the best way to watch a buffer and pass to a stream
                    int len = 0;
                    try {
                        while ((len = in.read(buff, 0, buff.length-1)) >= 0 && connectCounter.get() == count) {
                            if( len>0 ) {
                                getSessionStreams().write(buff, 0, len);
                            }
                        }
                    } catch (IOException e) {
                        logger.error(getName()+" error reading from shell stream",e);
                    }
                    logger.info("{} reader thread is stopping",getName());
                });
                readerThread.setDaemon(false);
                readerThread.start();
            }
        }
        return rtrn;
    }

    @Override
    public void exec(String command, Consumer<String> callback) {
        try {
            Process p = Runtime.getRuntime().exec(command);
            //TODO do not spawn a new thread per call to exec
            Thread t = new Thread(()->{
                InputStream in = p.getInputStream();
                byte[] buff = new byte[10 * 1024];
                StringBuilder sb = new StringBuilder();
                //TODO this is not the best way to watch a buffer and pass to a stream
                int len = 0;
                try {
                    while ((len = in.read(buff, 0, buff.length-1)) >= 0) {
                        if( len>0 ) {
                            sb.append(new String(buff,0,len));
                        }
                    }
                    callback.accept(sb.toString());
                } catch (IOException e) {
                    logger.error(getName()+" error reading from shell stream",e);
                }
                logger.info("{} reader thread is stopping",getName());
            });
            t.setDaemon(false);
            t.start();
            //TODO read the output or setup something that will
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public boolean isOpen() {
        return readerThread!=null && shellProcess!=null && shellProcess.isAlive();
    }

    @Override
    public AbstractShell copy() {
        return new LocalShell(
                getHost(),
                setupCommand,
                executor,
                getFilter(),
                trace
        );
    }

    @Override
    public void close() {
        if(shellProcess!=null){
            long pid = shellProcess.pid();
            try{
                Runtime.getRuntime().exec("kill -9 " + pid);
                //Runtime.getRuntime().exec("bg "+chld.pid());
                shellProcess.waitFor(1, TimeUnit.SECONDS);
            } catch (IOException e) {
                logger.error(getName()+" error while trying to stop shell process",e);
            } catch (InterruptedException e) {
                logger.error(getName()+" error while waiting for shell to stop",e);
            }
        }
        if(readerThread!=null && readerThread.isAlive()){
            connectCounter.incrementAndGet();//tells the thread to stop
        }
    }
}
