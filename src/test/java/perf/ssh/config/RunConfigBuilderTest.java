package perf.ssh.config;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class RunConfigBuilderTest {

    private static CmdBuilder cmdBuilder = CmdBuilder.getBuilder();
    private static InputStream stream(String input){
        return new ByteArrayInputStream(input.getBytes());
    }


    @Test
    public void testRoleExpession(){
        YamlParser parser = new YamlParser();
        parser.load("roleExpression",stream(""+
                "hosts:\n"+
                "  foo : user@foo\n"+
                "  bar : user@bar\n"+
                "  biz : user@biz\n"+
                "roles:\n"+
                "  foo:\n"+
                "    hosts: [foo]\n"+
                "  all\n"+
                "    hosts: [foo, bar, biz]\n"+
                "  NotFoo\n"+
                "    hosts: all !foo\n"+
                ""
        ));
        RunConfigBuilder builder = new RunConfigBuilder(cmdBuilder);

        builder.loadYaml(parser);

        builder.buildConfig();

    }

    @Test
    public void testSyntax(){
        YamlParser parser = new YamlParser();
        parser.load("supportedSyntax",stream(""+
                        "name: syntax\n"+
                        "scripts:\n"+
                        "  firstScript:#this is my first script\n"+
                        "    - sh: inline shell arguments\n"+
                        "    - queue-download:\n"+
                        "      path: ./\n"+
                        "      destination: ./\n"+
                        "    - sh: top\n"+
                        "      - sh: second\n"+
                        "      - sh: third\n"+
                        "    - sh: first second third\n"+
                        "      - watch:\n"+
                        "        - regex: \".*?\"\n"+
                        "          - abort: fail\n"+
                        "      - with:\n"+
                        "          FOO : buz\n"+
                        "      - sh: childCommand\n"+
                        "    - invoke: ${{scriptName}}\n"+
                        "  secondScript:#this is the otherScript\n"+
                        "    - sh: do this please\n"+
                        "    - abort: ha!\n"+
                        "hosts:\n"+
                        "  laptop: wreicher@laptop\n"+
                        "  server:\n"+
                        "     username: root\n"+
                        "     hostname: serverName\n"+
                        "     port: 22\n"+
                        "---\n"+
                        "roles:\n"+
                        "  foo:\n"+
                        "    hosts:\n"+
                        "     - laptop\n"+
                        "    setup-scripts:\n"+
                        "     - firstScript\n"+
                        "        - WITH: {foo:bar,biz:buz}\n"+
                        "     - firstScript\n"+
                        "        - WITH: {foo:yaba,biz:daba}\n"+
                        "    run-scripts\n"+
                        "     - secondScript\n"+
                        "    cleanup-scripts:\n"+
                        "     - ${{cleanupScript}}\n"+
                        "---\n"+
                        "states:\n"+
                        "  RUN:\n"+
                        "    FOO: bar\n"+
                        "  laptop:\n"+
                        "    FOO: biz\n"+
                        ""
                )
        );

        System.out.println(parser.getJson().toString(2));
        RunConfigBuilder builder = new RunConfigBuilder(cmdBuilder);
        builder.loadYaml(parser);
    }

}
