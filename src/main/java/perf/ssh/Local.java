package perf.ssh;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.CommandResult;
import perf.ssh.config.RunConfig;
import perf.ssh.config.RunConfigBuilder;
import perf.util.AsciiArt;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by wreicher
 * Performs actions on the local system (without an ssh connection)
 * Currently just used to upload / download files WITH the SUT
 * uses rsync instead of scp because it was much faster in our lab
 *
 */
// Has a lot of extra code for download queueing and progress updates which are not used at the moment
public class Local {

    final static Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    static class DownloadInfo {
        private long targetSize;
        private long startTime;
        private long previousTime;
        private long previousSize;
        private double rate;
        private double estimate;

        public DownloadInfo(long targetSize,long startTime){
            this.targetSize = targetSize;
            this.startTime = startTime;
            this.previousTime = startTime;
            this.previousSize = 0;
        }
        public long getTargetSize(){return targetSize;}
        public long getStartTime(){return startTime;}
        public long getPreviousTime(){return previousTime;}
        public long getPreviousSize(){return previousSize;}
        public double getRate(){return rate;}
        public double getEstimate(){return estimate;}

        public boolean isDone(){return previousSize>= targetSize;}
        public long duration(){return previousTime - startTime;}
        public double cumulativeRate(){
            double seconds = (previousTime*1.0 - startTime) / 1000;
            double sizeDifference = previousSize;
            return sizeDifference / seconds;
        }
        public void update(long timestamp,long size){
            double seconds = (timestamp*1.0 - previousTime) / 1000;
            double sizeDifference = size - previousSize;

            rate = sizeDifference / seconds;
            double bytesRemaining = targetSize - size;
            estimate = bytesRemaining/rate;

            this.previousTime = timestamp;
            this.previousSize = size;
        }
    }

    static class HostDownloader implements Runnable {

        String hostName;
        Queue<DownloadAction> downloadQueue;

        public HostDownloader(String hostName,Queue<DownloadAction> downloadQueue){
            this.hostName = hostName;
            this.downloadQueue = downloadQueue;
        }

        @Override
        public void run() {
            DownloadAction currentDownload = null;
            while( (currentDownload = downloadQueue.poll()) != null ){

            }
        }
    }

    static class RemoteFileInfo {
        private long size;
        private String name;
        public RemoteFileInfo(String name,long size){
            this.name = name;
            this.size = size;
        }
        public String getName(){return name;}
        public long getSize(){return size;}
    }
    static class DownloadAction {
        private String hostName;
        private String userName;
        private String path;
        private String destination;
        private Cmd command;
        private CommandResult result;
        public DownloadAction(String userName,String hostName,String path,String destination,Cmd command,CommandResult result){
            this.userName = userName;
            this.hostName = hostName;
            this.path = path;
            this.destination = destination;
            this.command = command;
            this.result = result;

        }
        public String getUserName(){return userName;}
        public String getHostName(){return hostName;}
        public String getPath(){return path;}
        public String getDestination(){return destination;}
        public CommandResult getResult(){return result;}
        public Cmd getCommand(){return command;}
    }

    private ThreadPoolExecutor executor;
    private Map<String,Queue<DownloadAction>> downloadQueue;
    private Set<String> activeHosts;

    private Map<File,Long> startTimes;
    private Map<File,Long> previousSize;
    private Map<File,Long> previousTimes;
    private Map<File,Long> targetSize;

    private String ssh;


    public Local(RunConfig config){
        if(config!=null && (config.hasCustomIdentity() || config.hasCustomKnownHosts() || config.hasCustomPassphrase())){
            this.ssh="/usr/bin/ssh ";
            if(config.hasCustomKnownHosts()){
                this.ssh+="-o UserKnownHostsFile="+config.getKnownHosts()+" ";
            }
            if(config.hasCustomIdentity()){
                this.ssh+="-i "+config.getIdentity()+" ";
            }
            if(config.hasCustomPassphrase()){
                storePassphrase(config.getIdentity(),config.getPassphrase());
            }
        }else{
            this.ssh = null;
        }
    }

    public void upload(String path,String destination,Host host){
        if(path == null || path.isEmpty() || destination == null || destination.isEmpty()){

        }else{
            String hostName = host.getHostName();
            String userName = host.getUserName();
            logger.info("Local.upload({},{}@{}:{})",path,userName,hostName,destination);
            rsyncSend(userName, hostName, path, destination);

        }
    }
    private void storePassphrase(String identity, String passphrase){
        if(passphrase!= RunConfigBuilder.DEFAULT_PASSPHRASE){
            ProcessBuilder builder = new ProcessBuilder();
            builder.command("/usr/bin/ssh-add", identity);
            try {
                Process p =  builder.start();
                final InputStream inputStream = p.getInputStream();
                final OutputStream outputStream = p.getOutputStream();
                final InputStream errorStream = p.getErrorStream();

                outputStream.write(passphrase.getBytes());
                outputStream.flush();
                int result = p.waitFor();
                logger.debug("ssh-add.result = {}",result);
                String line = null;
                BufferedReader reader = null;
                reader = new BufferedReader(new InputStreamReader(errorStream));
                while( (line=reader.readLine())!=null){
                    logger.error("  E: {}",line);
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));
                while( (line=reader.readLine())!=null){
                    logger.trace("  I: {}",line);
                }

            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
    }
    public void download(String path,String destination, Host host){

        if(path == null || path.isEmpty() || destination == null || destination.isEmpty()){

        } else {
            String hostName = host.getHostName();
            String userName = host.getUserName();
            logger.info("Local.download({}@{}:{},{})",userName,hostName,path,destination);
            rsyncFetch(userName, hostName, path, destination);
        }
    }
    private void rsyncSend(String userName, String hostName, String path, String dest){
        ProcessBuilder builder = new ProcessBuilder();
        if(this.ssh==null) {
            builder.command("/usr/bin/rsync", "-avz", path, userName + "@" + hostName + ":" + dest);
        }else{
            builder.command("/usr/bin/rsync", "-avz", "-e",this.ssh,path, userName + "@" + hostName + ":" + dest);
        }
        logger.debug( "Running rsync command: " + builder.command().stream().collect( Collectors.joining(" ")));
        try {
            Process p =  builder.start();
            final InputStream inputStream = p.getInputStream();
            final OutputStream outputStream = p.getOutputStream();
            final InputStream errorStream = p.getErrorStream();

            int result = p.waitFor();
            logger.debug("rsyncSend.result = {}",result);
            String line = null;
            BufferedReader reader = null;
            reader = new BufferedReader(new InputStreamReader(errorStream));
            while( (line=reader.readLine())!=null){
                logger.error("  E: {}",line);
            }
            reader = new BufferedReader(new InputStreamReader(inputStream));
            while( (line=reader.readLine())!=null){
                logger.trace("  I: {}",line);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
    private void rsyncFetch(String userName, String hostName, String path, String dest){
        File destinationFile = new File(dest);
        if(!destinationFile.exists()){
            if(destinationFile.isDirectory()){
                logger.trace("creating {} for rsyncFetch {}",dest,path);
                destinationFile.mkdirs();
            }else{
                logger.trace("creating {} for rsyncFetch {}",destinationFile.getParentFile().getPath(),path);
                destinationFile.getParentFile().mkdirs();
            }
        }

        ProcessBuilder builder = new ProcessBuilder();
        if(this.ssh==null) {
            builder.command("/usr/bin/rsync", "-avz", userName + "@" + hostName + ":" + path, dest);
        }else{
            builder.command("/usr/bin/rsync", "-avz", "-e",this.ssh, userName + "@" + hostName + ":" + path, dest);
        }
        System.out.println(builder.command());
        try {
            Process p =  builder.start();
            final InputStream inputStream = p.getInputStream();
            final OutputStream outputStream = p.getOutputStream();
            final InputStream errorStream = p.getErrorStream();

            int result = p.waitFor();
            logger.debug("rsyncFetch.result = {}",result);
            String line = null;
            BufferedReader reader = null;
            reader = new BufferedReader(new InputStreamReader(errorStream));
            while( (line=reader.readLine())!=null){
                logger.trace("  E: {}",line);
            }
            reader = new BufferedReader(new InputStreamReader(inputStream));
            while( (line=reader.readLine())!=null){
                logger.trace("  I: {}",line);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
    public List<RemoteFileInfo> listRemoteFiles(String userName, String hostName, String path) {
        ArrayList<RemoteFileInfo> rtrn = new ArrayList<>();
        ProcessBuilder builder = new ProcessBuilder();
        builder.command("/usr/bin/ssh", userName + "@" + hostName, "ls -sLR -c1 "+path);
        try {

            Process p = builder.start();
            final InputStream inputStream = p.getInputStream();
            final OutputStream outputStream = p.getOutputStream();
            final InputStream errorStream = p.getErrorStream();

            int result = p.waitFor();
            String line = null;
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            Matcher pathMatcher = Pattern.compile("(?<basePath>[^:]+):").matcher("");
            Matcher fileMatcher = Pattern.compile("^(?<fileSize>\\d+) (?<fileName>.*)$").matcher("");
            String currentPath = "";
            while ( (line = reader.readLine()) != null ){
                if(pathMatcher.reset(line).matches()){
                    currentPath = pathMatcher.group("basePath");
                }
                if(fileMatcher.reset(line).matches()){
                    long fileSize = Long.parseLong(fileMatcher.group("fileSize"));
                    String fileName = fileMatcher.group("fileName");
                    rtrn.add(new RemoteFileInfo(currentPath+File.separator+fileName,fileSize));
                }
            }
            reader = new BufferedReader(new InputStreamReader(errorStream));
            while ( (line = reader.readLine()) != null ){
                logger.error("  E: {}",line);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return rtrn;
    }

    public static void main(String[] args) {

        ProcessBuilder builder = new ProcessBuilder();

        Local local = new Local(null);

        local.ssh="ssh -i /home/wreicher/.ssh/id_rsa -o UserKnownHostsFile=/home/wreicher/.ssh/known_hosts";

        local.rsyncFetch("benchuser","benchserver6","/tmp/foo","/tmp");

        System.exit(0);

        List<RemoteFileInfo> remoteFiles = local.listRemoteFiles("benchuser","benchserver6","/tmp/foo/*");

        System.exit(0);

        String newString = "test-"+System.currentTimeMillis();
        File newFile = new File("/tmp/"+newString+"/");
        if(!newFile.exists()){
            newFile.mkdirs();
        }
        builder.directory(newFile);

        //builder.command("/usr/bin/scp","-rvp","w520:/home/wreicher/perfWork/amq/jdbc/00282/client1/*","./");
        builder.command("/usr/bin/scp","-rvp","client1:/tmp/foo/*","./");

        //builder.command("/usr/bin/dstat","-Tcdngy","1");

        try {

            Process p = builder.start();
            final InputStream inputStream = p.getInputStream();
            final OutputStream outputStream = p.getOutputStream();
            final InputStream errorStream = p.getErrorStream();



            BiFunction<File,Long,Runnable> makeFileWatcher = (fileName,targetSize)-> new Runnable() {



                @Override
                public void run() {
                    try {
                        long prev = 0;
                        long newSize = 0;
                        long start = System.currentTimeMillis();
                        long ts = 0;
                        long sleepTime = 1000;
                        String targetSizeString = AsciiArt.printKMG(targetSize);
                        while ( (newSize = fileName.length()) < targetSize){
                            Thread.sleep(sleepTime);
                            ts = System.currentTimeMillis();
                            newSize = fileName.length();
                            double delta = newSize - prev;
                            prev = newSize;
                            System.out.println(fileName.getPath()+
                                " "+AsciiArt.printKMG(fileName.length())+" of "+targetSizeString+
                                " @ "+AsciiArt.ANSI_YELLOW+AsciiArt.printKMG(delta)+"/s "+AsciiArt.ANSI_RESET+
                                String.format("%.2f",(newSize*100.0/targetSize))+"%"+
                                " remaining "+String.format("%.2f",(ts-start)*1.0/sleepTime/((newSize*1.0/targetSize)))
                            );
                        }
                        System.out.println(AsciiArt.ANSI_CYAN+"Done watching "+fileName.getPath()+AsciiArt.ANSI_RESET+" @ "+((ts-start)*1.0/1000));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            };

            BiFunction<InputStream,String,Runnable> makeReader = (stream,label)->()->{
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(stream));
                String line = "";
                LinkedList<String> directories = new LinkedList<>();
                Matcher enterDirectory = Pattern.compile("Entering directory: \\w+ \\S+ (?<directory>\\w+)").matcher("");
                Matcher fileModes = Pattern.compile("Sending file modes: \\w+ (?<size>\\d+) (?<fileName>\\S+)").matcher("");
                try {
                    while ((line = reader.readLine())!= null) {
                        if( enterDirectory.reset(line).matches()){
                            String newDir = enterDirectory.group("directory");
                            File findDir = null;
                            while( !(findDir= new File(newFile.getPath()+"/"+String.join("/",directories)+"/"+newDir)).exists()){
                                System.out.println(label+" "+AsciiArt.ANSI_RED+"! "+findDir.getPath()+AsciiArt.ANSI_RESET);
                                String rem = directories.removeLast();
                                System.out.println(label+" removing "+rem);
                            }
                            System.out.println(label+" Entering "+ AsciiArt.ANSI_RED+newDir+AsciiArt.ANSI_RESET);
                            System.out.println(label+" found @ "+findDir.getPath());
                            directories.add(newDir);

                        }
                        if( fileModes.reset(line).matches()){
                            System.out.println(label+" "+AsciiArt.ANSI_CYAN+"!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"+AsciiArt.ANSI_RESET);
                            String fileName = fileModes.group("fileName");
                            long size = Long.parseLong(fileModes.group("count"),10);

                            File targetFile = null;//new File(newFile.getPath()+"/"+String.join("/",directories)+"/"+fileName);
                            while( !(targetFile= new File(newFile.getPath()+"/"+String.join("/",directories)+"/"+fileName)).exists()){
                                System.out.println(label+" "+AsciiArt.ANSI_RED+"! "+targetFile.getPath()+AsciiArt.ANSI_RESET);
                                String rem = directories.removeLast();
                                System.out.println(label+" removing "+rem);
                            }
                            if(!targetFile.exists()){
                                System.out.println(label+" "+AsciiArt.ANSI_RED+"!!! "+fileName+" does not exist @ "+targetFile.getPath()+AsciiArt.ANSI_RESET);
                            }else{
                                System.out.println(label+" "+AsciiArt.ANSI_CYAN+" Starting thread watcher for "+targetFile+" count="+size+AsciiArt.ANSI_RESET);
                                Thread watchFileThread = new Thread(makeFileWatcher.apply(targetFile,size));
                                watchFileThread.start();
                            }
                        }
                        System.out.println(label+" "+line);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            };
            Thread runInputStream = new Thread(makeReader.apply(inputStream,"input"));
            Thread runErrorStream = new Thread(makeReader.apply(errorStream, "error"));

            Thread sendSignal = new Thread(()->{
                System.out.println("see you in 5s");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                //System.out.println("yawn, ok, time to kill");
                //p.destroy();
            });
            runInputStream.start();
            runErrorStream.start();
            sendSignal.start();
            System.out.println("p.isAlive "+p.isAlive());
            int result = p.waitFor();
            System.out.println("p.isAlive "+p.isAlive());
            System.out.println("result: "+result);
            System.out.println("p.isAlive "+p.isAlive());
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            //p.waitFor
            e.printStackTrace();
        }

    }
}
