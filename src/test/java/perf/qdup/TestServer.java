package perf.qdup;


import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.shell.ProcessShellFactory;
import org.junit.rules.ExternalResource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Does not work with rsync yet :(
 * Probably because it doesn't use the existing hostkey?
 */
public class TestServer extends ExternalResource{

    private SshServer server;

    public TestServer(){

        server = SshServer.setUpDefaultServer();
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(new File("/tmp/hostkey.ser")));
        //server.setShellFactory(new ProcessShellFactory(new String[] { "/bin/bash", "-i", "-l" }));
        server.setPublickeyAuthenticator((username,key,session)->{

            return true;
        });
        server.setCommandFactory((command)-> {
            return new ProcessShellFactory(command.split(" ")).create();
        });
        server.setShellFactory(()->{
            //TODO does not support Ctrl-c, need a custom ShellProcess to do that
            ProcessShellFactory factory = new ProcessShellFactory(new String[] { "/bin/bash", "-i", "-l" });
            final Command cmd = factory.create();

            return new Command() {
                @Override
                public void setInputStream(InputStream in) {

                    cmd.setInputStream(in);
                }

                @Override
                public void setOutputStream(OutputStream out) {
                    cmd.setOutputStream(out);
                }

                @Override
                public void setErrorStream(OutputStream err) {
                    cmd.setErrorStream(err);
                }

                @Override
                public void setExitCallback(ExitCallback callback) {
                    cmd.setExitCallback(callback);
                }

                @Override
                public void start(Environment env) throws IOException {
                    env.addSignalListener((sig)->{
                        //TODO does not work :(
                    });
                    cmd.start(env);
                }
                @Override
                public void destroy() throws Exception {
                    cmd.destroy();
                }
            };
        });
    }

    public Host getHost(){
        return new Host("fakeUser","localhost",getPort());
    }

    protected void before(){
        try {
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        } finally{
        }
    }
    public int getPort(){return server.getPort();}
    public void after(){
        try {
            server.stop(true);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {

        }
    }
}
