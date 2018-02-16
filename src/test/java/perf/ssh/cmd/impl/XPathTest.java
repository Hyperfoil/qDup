package perf.ssh.cmd.impl;

import org.junit.Test;
import perf.ssh.Run;
import perf.ssh.cmd.Cmd;
import perf.ssh.cmd.CommandDispatcher;
import perf.ssh.cmd.Result;
import perf.ssh.cmd.Script;
import perf.ssh.config.CmdBuilder;
import perf.ssh.config.RunConfig;
import perf.ssh.config.RunConfigBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;

public class XPathTest {

    @Test
    public void xpathTest(){
        StringBuilder first = new StringBuilder();
        StringBuilder second = new StringBuilder();
        StringBuilder third = new StringBuilder();

        RunConfigBuilder builder = new RunConfigBuilder(CmdBuilder.getBuilder());
        Script runScript = new Script("run-xpath");
        runScript.then(Cmd.sh("echo \\<foo\\>\\<bar value=\\\"one\\\"\\>\\</bar\\>\\<biz\\>buz\\</biz\\>\\</foo\\> > /tmp/foo.xml"));
        runScript.then(
                Cmd.xpath("/tmp/foo.xml>/foo/biz/text()")
                        .then(Cmd.code((input,state)->{
                            first.append(input.trim());
                            return Result.next(input);
                        }))
        );
        runScript.then(
                Cmd.xpath("/tmp/foo.xml>/foo/biz == biz")
        );
        runScript.then(
                Cmd.xpath("/tmp/foo.xml>/foo/biz/text()")
                        .then(Cmd.code((input,state)->{
                            second.append(input.trim());
                            return Result.next(input);
                        }))
        );
        runScript.then(
                Cmd.xpath("/tmp/foo.xml>/foo/bar/@value == two")
        );
        runScript.then(
                Cmd.xpath("/tmp/foo.xml>/foo/bar/@value")
                        .then(Cmd.code((input,state)->{
                            third.append(input.trim());
                            return Result.next(input);
                        }))
        );


        builder.addScript(runScript);
        builder.addHostAlias("local","wreicher@localhost:22");
        builder.addHostToRole("role","local");
        builder.addRoleRun("role","run-xpath",new HashMap<>());

        RunConfig config = builder.buildConfig();
        CommandDispatcher dispatcher = new CommandDispatcher();
        Run run = new Run("/tmp",config,dispatcher);
        run.run();

        System.out.println("first="+first.toString());
        System.out.println("second="+second.toString());
        System.out.println("third="+third.toString());

        File tmpXml = new File("/tmp/foo.xml");

        try {
            String content = new String(Files.readAllBytes(tmpXml.toPath()));
            System.out.println(content);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
