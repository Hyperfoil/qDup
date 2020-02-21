package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.cmd.Result;
import io.hyperfoil.tools.qdup.cmd.Script;
import io.hyperfoil.tools.qdup.config.CmdBuilder;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import org.junit.Test;
import io.hyperfoil.tools.qdup.SshTestBase;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class XmlCmdTest extends SshTestBase {

    //TODO add a test to ensure State and local variables resolve

    @Test
    public void xpathTest(){
        StringBuilder first = new StringBuilder();
        StringBuilder second = new StringBuilder();
        StringBuilder third = new StringBuilder();
        File fooXml = new File("/tmp/foo.xml");
        if(fooXml.exists()){
            fooXml.delete();
        }
        RunConfigBuilder builder = getBuilder();
        Script runScript = new Script("run-xml");
        runScript.then(Cmd.sh("echo '<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo><bar message=\"uno\"></bar><biz>buz</biz></foo>' > /tmp/foo.xml"));
        runScript.then(
                Cmd.xml("/tmp/foo.xml>/foo/biz")///text()
                        .then(Cmd.code((input,state)->{
                            first.append(input.trim());
                            return Result.next(input);
                        }))
        );
        runScript.then(
                Cmd.xml("/tmp/foo.xml>/foo/biz == biz")
        );
        runScript.then(
                Cmd.xml("/tmp/foo.xml>/foo/biz")///text()
                        .then(Cmd.code((input,state)->{
                            second.append(input.trim());
                            return Result.next(input);
                        }))
        );
        runScript.then( //TODO this does not finish the write before the next Cmd runs (sometimes)
                Cmd.xml("/tmp/foo.xml>/foo/bar/@message == two")
        );
        runScript.then(
                Cmd.xml("/tmp/foo.xml>/foo/bar/@message")
                        .then(Cmd.code((input,state)->{
                            third.append(input.trim());
                            return Result.next(input);
                        }))
        );
        builder.addScript(runScript);
        builder.addHostAlias("local",getHost().toString());
        builder.addHostToRole("role","local");
        builder.addRoleRun("role","run-xml",new HashMap<>());

        RunConfig config = builder.buildConfig();
        Dispatcher dispatcher = new Dispatcher();
        Run run = new Run("/tmp",config,dispatcher);
        run.run();
        assertEquals("/tmp/foo.xml>/foo/biz/text() should be buz","buz",first.toString());
        assertEquals("/tmp/foo.xml>/foo/biz/text() should be biz after xml","biz",second.toString());
        assertEquals("/tmp/foo.xml>/foo/bar/@message should be two afer xml","two",third.toString());
        File tmpXml = new File("/tmp/foo.xml");

        try {
            String content = new String(Files.readAllBytes(tmpXml.toPath()));
            assertFalse("content should not contain uno",content.contains("uno"));
            assertFalse("content should not contain buz",content.contains("buz"));
            assertTrue("content should not contain biz",content.contains("biz"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        tmpXml.delete();
    }


    @Test
    public void setState(){
        StringBuilder first = new StringBuilder();
        RunConfigBuilder builder = getBuilder();
        Script runScript = new Script("run-xml");
        runScript.then(Cmd.sh("echo '<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo><bar message=\"uno\"></bar><biz>buz</biz></foo>' > /tmp/foo.xml"));
        runScript.then(
                Cmd.xml("/tmp/foo.xml>/foo/biz "+XmlCmd.SET_STATE_KEY+" BIZ")///text()
                        .then(Cmd.code((input,state)->{
                            Object biz = state.get("BIZ");
                            first.append(biz);
                            return Result.next(input);
                        }))
        );
        builder.addScript(runScript);
        builder.addHostAlias("local",getHost().toString());
        builder.addHostToRole("role","local");
        builder.addRoleRun("role","run-xml",new HashMap<>());

        RunConfig config = builder.buildConfig();
        Dispatcher dispatcher = new Dispatcher();
        Run run = new Run("/tmp",config,dispatcher);
        run.run();
        dispatcher.shutdown();
        File tmpXml = new File("/tmp/foo.xml");

        tmpXml.delete();


        assertEquals("/foo/biz message set to BIZ","buz",first.toString());
    }
}
