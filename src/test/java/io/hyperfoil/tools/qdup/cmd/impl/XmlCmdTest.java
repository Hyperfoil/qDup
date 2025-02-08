package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.Local;
import io.hyperfoil.tools.qdup.Run;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Dispatcher;
import io.hyperfoil.tools.qdup.cmd.Result;
import io.hyperfoil.tools.qdup.cmd.Script;
import io.hyperfoil.tools.qdup.config.RunConfig;
import io.hyperfoil.tools.qdup.config.RunConfigBuilder;
import io.hyperfoil.tools.qdup.config.yaml.Parser;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import io.hyperfoil.tools.qdup.SshTestBase;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class XmlCmdTest extends SshTestBase {

    //TODO add a test to ensure State and local variables resolve
    @Test
    public void single_string_constructor(){
        XmlCmd xmlCmd = new XmlCmd("./foo/bar/biz/web.xml>web-app ++ <distributable />");
        assertEquals("./foo/bar/biz/web.xml",xmlCmd.getPath());
        assertEquals(1,xmlCmd.getOperations().size());
    }

    @Test @Ignore
    public void add_child_web_xml(){
        Parser parser = Parser.getInstance();
        RunConfigBuilder builder = getBuilder();
        builder.loadYaml(parser.loadFile("",
                """
                scripts:
                  test-xml:
                  - sh: mkdir -p /tmp/foo/bar/biz
                  - sh: cd /tmp/
                  - sh: |
                      cat > /tmp/foo/bar/biz/web.xml << 'EOF'
                      <?xml version="1.0" encoding="UTF-8"?>
                      <web-app
                      xmlns:javaee="http://xmlns.jcp.org/xml/ns/javaee"
                      xmlns="http://xmlns.jcp.org/xml/ns/javaee"
                      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/javaee http://xmlns.jcp.org/xml/ns/javaee/web-app_3_1.xsd"
                      version="3.1">
                        <display-name>SpecjInsuranceAgentsJSF</display-name>
                        <env-entry>
                          <description>The host name or IP address of the Vehicle Service</description>
                          <env-entry-name>vehicle.service.host</env-entry-name>
                          <env-entry-type>java.lang.String</env-entry-type>
                          <env-entry-value>benchserver4G1</env-entry-value>
                        </env-entry>
                        <env-entry>
                          <description>The port number of the Vehicle Service</description>
                          <env-entry-name>vehicle.service.port</env-entry-name>
                          <env-entry-type>java.lang.Integer</env-entry-type>
                          <env-entry-value>8085</env-entry-value>
                        </env-entry>
                        <env-entry>
                          <description>The base path for the Vehicle Service</description>
                          <env-entry-name>vehicle.service.basePath</env-entry-name>
                          <env-entry-type>java.lang.String</env-entry-type>
                          <env-entry-value>vehicle/rest/service</env-entry-value>
                        </env-entry>
                        <env-entry>
                          <description>The protocol of the Vehicle Service</description>
                          <env-entry-name>vehicle.service.protocol</env-entry-name>
                          <env-entry-type>java.lang.String</env-entry-type>
                          <env-entry-value>http</env-entry-value>
                        </env-entry>
                      </web-app>
                      EOF
                  - xml: ./foo/bar/biz/web.xml>web-app ++ <distributable />
                  - sh: cat /tmp/foo/bar/biz/web.xml
                hosts:
                  local: TARGET_HOST
                roles:
                  doit:
                    hosts: [local]
                    setup-scripts: [test-xml]
                """.replaceAll("TARGET_HOST",getHost().toString())
        ));
        RunConfig config = builder.buildConfig(parser);
        assertFalse("runConfig errors:\n" + config.getErrorStrings().stream().collect(Collectors.joining("\n")), config.hasErrors());
        Dispatcher dispatcher = new Dispatcher();
        Run doit = new Run(tmpDir.toString(), config, dispatcher);
        doit.run();

    }
    @Test
    public void xpath_test(){
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

        RunConfig config = builder.buildConfig(Parser.getInstance());
        Dispatcher dispatcher = new Dispatcher();
        Run run = new Run(tmpDir.toString(),config,dispatcher);
        run.run();
        assertEquals("/tmp/foo.xml>/foo/biz/text() should be buz","buz",first.toString());
        assertEquals("/tmp/foo.xml>/foo/biz/text() should be biz after xml","biz",second.toString());
        assertEquals("/tmp/foo.xml>/foo/bar/@message should be two afer xml","two",third.toString());
        File tmpXml = new File("/tmp/foo.xml");

        try {
            new Local(getBuilder().buildConfig(Parser.getInstance()))
                    .download(tmpXml.getAbsolutePath(), tmpXml.getAbsolutePath(), getHost());
            String content = new String(Files.readAllBytes(tmpXml.toPath()));
            assertFalse("content should not contain uno",content.contains("uno"));
            assertFalse("content should not contain buz",content.contains("buz"));
            assertTrue("content should not contain biz",content.contains("biz"));
        } catch (IOException e) {
            e.printStackTrace();
            Assert.fail("Error reading file: ".concat(tmpXml.getAbsolutePath()));
        }
        tmpXml.delete();
    }


    @Test
    public void set_state_operation(){
        StringBuilder first = new StringBuilder();
        RunConfigBuilder builder = getBuilder();
        Script runScript = new Script("run-xml");
        runScript.then(Cmd.sh("rm -Rf /tmp/foo.xml"));
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

        RunConfig config = builder.buildConfig(Parser.getInstance());
        Dispatcher dispatcher = new Dispatcher();
        Run run = new Run(tmpDir.toString(),config,dispatcher);
        run.run();
        dispatcher.shutdown();

        assertEquals("/foo/biz message set to BIZ","buz",first.toString());
    }
}
