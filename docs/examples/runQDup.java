//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.hyperfoil.tools:qDup:0.6.2
//DEPS org.apache.commons:commons-lang3:3.12.0

import org.apache.commons.lang3.ArrayUtils;
import io.hyperfoil.tools.qdup.JarMain;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Files;
import java.net.InetAddress;

import java.io.IOException;

class runQDup {

    public static void main(String... args) throws Exception {

        String pwd = "", username = "", hostname = "";

        Path tempDirWithPrefix = null;

        try{
            pwd = System.getProperty("user.dir");
            username = System.getProperty("user.name");
            hostname = InetAddress.getLocalHost().getHostName();
            tempDirWithPrefix = Files.createTempDirectory("qDup");
        } catch (NullPointerException|IllegalArgumentException|UnsupportedOperationException|IOException|SecurityException exception){
            System.err.println("Could not determine system properties");
            System.exit(1);
        }

        String projectPath = "";

        for(File file = new File(pwd); file != null; file = file.getParentFile()){
            if( file.getName().equals("qDup")){
                projectPath  = file.toPath().toString();
                break;
            }
        }

        if(projectPath.equals("")) {
            System.err.println("Could not determine project directory");
            System.exit(1);
        }

        String qDupFilePath = null;
        try{
            qDupFilePath = System.getProperty("qDupScript");
            if(qDupFilePath.substring(0,1).equals(".")){
                qDupFilePath = pwd + qDupFilePath.substring(1,qDupFilePath.length());
            }
            File qDupFile = new File(qDupFilePath);
            if(!qDupFile.exists()){
                System.err.printf("File not found: %s\n", qDupFilePath);
                System.exit(1);
            } else {
                qDupFilePath = qDupFile.getPath();
            }
        } catch(SecurityException|NullPointerException|IllegalArgumentException exception){
        }



        String[] qDupBaseArgs = {"-B"
        , tempDirWithPrefix.toString()
        , qDupFilePath != null ? qDupFilePath : projectPath + "/docs/examples/helloWorld.yaml"
        , "-S"
        , "USER=" + username
        , "-S"
        , "HOST=" + hostname
        };

        String[] qDupArgs = ArrayUtils.addAll(qDupBaseArgs, args);

        JarMain.main(qDupArgs);
    }

}
