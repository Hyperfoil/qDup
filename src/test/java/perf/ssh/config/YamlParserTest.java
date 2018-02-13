package perf.ssh.config;

import org.junit.Assert;
import org.junit.Test;
import perf.yaup.json.Json;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static perf.ssh.config.YamlParser.*;

public class YamlParserTest {



    private static Json EMPTY_ARRAY = new Json();
    private static InputStream stream(String input){
        return new ByteArrayInputStream(input.getBytes());
    }

    /*
     * A CHILD should be an array where each entry is an array of objects
     */
    private boolean childCheck(Json json){
        Queue<Json> toCheck = new LinkedList<>();
        toCheck.add(json);
        while(!toCheck.isEmpty()){
            Json checkMe = toCheck.remove();

            if(checkMe.isArray()){
                fail("CHILD should not check arrays\n"+checkMe.toString(2));
                return false; // error, arrays should not be added
            }else{
                if(checkMe.has(CHILD)){
                    if(checkMe.get(CHILD) instanceof Json){
                        Json childJson = checkMe.getJson(CHILD);
                        if(childJson.isArray()){
                            for(int i=0; i<childJson.size(); i++){
                                Object entryObject = childJson.get(i);
                                if(entryObject instanceof Json){
                                    Json entryJson = (Json) entryObject;
                                    if(entryJson.isArray()){
                                        for(int e=0; e<entryJson.size(); e++){
                                            Object entryChild = entryJson.get(e);
                                            if(entryChild instanceof Json){
                                                toCheck.add((Json)entryChild);
                                            }else{
                                                fail("CHILD entry should only contain objects");
                                                return false; // CHILD entry should only contain objects
                                            }
                                        }
                                    }else{
                                        fail("CHILD should only contain arrays");
                                        return false;//CHILD should only contain arrays
                                    }
                                }else{
                                    fail("CHILD should only contain json");
                                    return false;//CHILD should only contain json
                                }
                            }
                        }else{
                            fail("CHILD should be an array");
                            return false;//CHILD should be an array
                        }
                    }else{
                        fail("CHILD should be a json");
                        return false;//CHILD should be a json
                    }
                }
            }
        }
        return true;//all the children check out
    }

    /*
     * Check each entry of the root json for CHILD format
     */
    private void validateParse(YamlParser parser){
        Json json = parser.getJson();

        if(parser.hasErrors()){
            fail(parser.getErrors().stream().collect( Collectors.joining( "\n" ) ));
        }

        if(json.isArray()){
            for(int i=0; i<json.size(); i++){
                Json yamlJson = json.getJson(i,new Json());
                yamlJson.forEach(obj->{
                    if(obj instanceof Json){
                        assertTrue("CHILD should be [[{}]...] but was:\n"+((Json)obj).toString(),childCheck((Json)obj));
                    }else{
                        Assert.fail("json entry was not a json "+obj);
                    }
                });

            }
        }else{
            fail("YamlParser.getJson() should return an array");
        }


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
            "    - sh: [first, second, third]\n"+
            "      - watch:\n"+
            "      - regex: \".*?\"\n"+
            "        - abort: fail\n"+
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
            "  ALL:\n"+
            "    setup-scripts:\n"+
            "     - firstScript\n"+
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

        validateParse(parser);

    }

    @Test
    public void multiPartValueString(){
        YamlParser parser = new YamlParser();
        parser.load("supportedSyntax",stream(""+
                "foo: \"biz \\\"buz:fuz\\\" :#boom\" 2ndValue#COMMENT\n"+
                "bar: bar\n"+
                ""

            )
        );
        validateParse(parser);


        Json json = parser.getJson("supportedSyntax");
        assertTrue("json should be an array",json.isArray());
        assertEquals("json should have 2 entires",2,json.size());
        Json foo = json.getJson(0);
        Json second = json.getJson(1);

        assertEquals("foo should have a COMMENT",true,foo.has(COMMENT));
        assertTrue("foo VALUE should include 2ndValue",foo.getString(VALUE).contains("2ndValue"));

    }

    @Test
    public void supportedSyntax(){
        String test = ""+
            "#comment1\n"+
            "#comment2\n"+
            "0Level1 :\n"+
            "0Level2 : hasValue\n"+
            "  1 : normal\n"+
            "  1 : \"quoted[{]}\\\"Value\"\n"+
            "    - 1.1 : one\n"+
            "      1.1.a:Alpha\n"+
            "      1.1.b:Bravo\n"+
            "    - 1.2 : two\n"+
            "      1.2.y:Yankee\n"+
            "    - 1.2.z:Zulu\n"+
            "0Level3: #inlineComment\n"+
            "  1 : [first, second, \"quoted\\\" :,[{]}\", other, [ subOne, subTwo], {subKey: subValue}, zed]\n"+
            "  2 : bar\n"+
            "  2 : {a:Apple,b:Banana , c : carrot, d : \"quoted\\\" :,[{]}\",e : [one, two], f: { fff: bbb}}\n"+
            "";
        YamlParser parser = new YamlParser();
        parser.load("supportedSyntax",stream(test));

        Json json = parser.getJson("supportedSyntax");

        validateParse(parser);




    }

    @Test
    public void commandOptions(){
        YamlParser parser = new YamlParser();
        parser.load("comments",stream(""+
                "test:\n"+
                "  - sh : shellArguments WITH spaces | and pipes | ${{variables}} and whatnot\n"+
                "  - sh :\n"+
                "      watch:\n"+
                "        - regex: \".*?FOO\"\n"+
                "            - log: foo\n"+
                "  - argCmd:\n"+
                "      arg1: value1\n"+
                "      arg2: value2\n"+
                "  - parentCmd:\n"+
                "      - childCmd:\n"+
                "          - grandChildCmd:\n"+
                "              - greatGrandChildCmd:\n"+
                "      - 2ndChildCmd:\n"+
                ""
            )
        );
        validateParse(parser);

    }

    @Test
    public void testKeys(){
        String test=""+
            "KEY:VALUE\n"+
            "\"ke:y\":VALUE";
        YamlParser parser = new YamlParser();
        parser.load("comments",stream(test));

        Json json = parser.getJson();

        validateParse(parser);

    }

    @Test
    public void comments(){
        String test=""+
            "#full line\n"+
            "#second full line\n"+
            "a : a\n"+
            "  a.a : a.a #inline\n"+
            "  #aComment1\n"+
            "  a.b : \"a.a #notComment\"\n"+
            "#commentTreeBreak\n"+
            "  a.c : a.a #withQuote\"\n"+
            "  a.d : a.d \\#notComment\n"+
            "  #aComment2\n";
        YamlParser parser = new YamlParser();
        parser.load("comments",stream(test));

        Json json = parser.getJson();

        validateParse(parser);

    }


    @Test
    public void spaceNesting(){
        String test = ""+
                "#startComment\n"+
                "#2ndstartComment\n"+
                "a : a\n"+
                "  a.a : a.a\n"+
                "    a.a.a : a.a.a #commentAAA\n"+
                "      - a.a.a.a : a.a.a.a\n"+
                "        a.a.a.b : a.a.a.b\n"+
                "      - a.a.a.c : a.a.a.c\n"+
                "        a.a.a.d : a.a.a.d\n"+
                "  a.b : a.b\n";

        YamlParser parser = new YamlParser();
        parser.load("spaceNesting",stream(test));

        validateParse(parser);

        Json json = parser.getJson();






    }

    //Demonstrates a difference between how YamlParser and Yaml would parse input
    @Test
    public void listOfMaps(){
        String test = ""+
                "foo:\n"+
                "  - name: firstName\n"+
                "    label: firstLabel\n"+
                "  - name: secondName\n"+
                "    label: secondLabel\n";



        YamlParser parser = new YamlParser();
        parser.load("listOfMaps",stream(test));
        validateParse(parser);


        Json json = parser.getJson();



    }

    // Tests for parsing the Yaml
    @Test
    public void nestedMap(){
        YamlParser parser = new YamlParser();
        parser.load("nestMap",stream(""+
        "foo:\n"+
        " - foo.foo : ff\n"+
        "   foo.bar : fb\n"+
        " - foo.foo : ff\n"+
        "   foo.bar : fb\n"+
        "bar:\n"+
        " - bar.foo : bf\n"+
        "   bar.bar : bb\n"));



        validateParse(parser);

        Json json = parser.getJson();




    }


    @Test
    public void nesting(){
        YamlParser parser = new YamlParser();
        parser.load("nesting.yaml",stream(""+
            "foo: 1\n"+
            "- foo.foo: 1.1\n"+
            "  - foo.foo.foo: 1.1.1\n"+
            "    - foo.foo.foo.foo : 1.1.1.1\n"+
            "- foo.bar: 1.2\n"+
            "  foo.biz: biz\n"+
            ""
        ));

        Json json = parser.getJson();

        validateParse(parser);

    }
    @Test
    public void inlineList(){
        YamlParser parser = new YamlParser();
        parser.load("list.yaml",stream("---\n"+
                "foo: [a, b , c  ,    d]\n"+
                "bar: [ a , \"b[]][{}}{,\" , c,d ]\n"+
                "biz: [ a [ a.a, a.b ], [b.a,b.b]]\n"+
                ""
        ));
        validateParse(parser);
        Json json = parser.getJson("list.yaml");




    }
    @Test
    public void inlineMap(){
        YamlParser parser = new YamlParser();
        parser.load("map.yaml",stream("---\n"+
                "top:\n"+
                "  foo: {foo : 1 , foo.foo: 1 1 , foo.bar : \"1,][{}}{.2\" , foo.biz { f.b.a : one , f.b.a : two}, zed: end }\n"+
                "  bar: biz\n"+
                ""
        ));
        validateParse(parser);
        Json json = parser.getJson();




    }

    @Test
    public void variableKeyValue(){
        YamlParser parser = new YamlParser();
        parser.load("variableKeyValue",stream(""+
            "role:\n"+
            "  - ${{keyNovalue}}\n"+
            "  - ${{keyWithValue}} : ${{VALUE}} - ${{meToo}}\n"+
            ""
        ));
        validateParse(parser);

    }

    // Tests for generating the RunConfig
    @Test
    public void variableScriptInRoles(){
        YamlParser parser = new YamlParser();
        parser.load("variableScriptInRole.yaml",
                stream("name: variableScriptInRole \n"+
                        "--- \n"+
                        "hosts:\n"+
                        "  local: user@localhost\n"+
                        "roles:\n"+
                        "  test:\n"+
                        "    run-scripts:\n"+
                        "     - ${{runScript}}\n"+
                        "    setup-scripts:\n"+
                        "     - ${{setupScript}}\n"+
                        "    cleanup-scripts:\n"+
                        "     - ${{cleanupScript}}\n"+
                        "    hosts:\n"+
                        "      local\n"+
                        "scripts:\n"+
                        " - alpha: \n"+
                        "    - sh: alpha \n"+
                        " - bravo: \n"+
                        "    - sh: bravo \n"+
                        " - charlie: \n"+
                        "    - sh: charlie \n"+
                        "states:\n" +
                        "  run:\n"+
                        "    runScript: alpha\n"+
                        "    setupScript: bravo\n"+
                        "    cleanupScript: charlie"
                )
        );
        validateParse(parser);

    }
    @Test
    public void multipleHostsInRole(){
        YamlParser parser = new YamlParser();
        parser.load("multipleHosts.yaml",
                stream("name: multipleHosts \n"+
                        "---\n" +
                        "hosts:\n" +
                        "  client1: benchuser@benchclient1\n" +
                        "  client2: benchuser@benchclient2\n" +
                        "  client3: benchuser@benchclient3\n" +
                        "  client4: benchuser@benchclient4\n" +
                        "  server3:\n" +
                        "    username: root\n" +
                        "    hostname: benchserver3\n" +
                        "  server4:\n" +
                        "    username: benchuser\n" +
                        "    hostname: benchserver4\n" +
                        "    port: 22\n" +
                        "\n" +
                        "---\n"+
                        "roles:\n" +
                        "  ALL:\n" +
                        "    setup-scripts:\n" +
                        "     - sync-time\n" +
                        "    run-scripts:\n" +
                        "     - dstat\n" +
                        "#  database:\n" +
                        "#    hosts: server3\n" +
                        "#    run-scripts: docker-oracle\n" +
                        "  satellite:\n" +
                        "    hosts:\n" +
                        "      - client1\n" +
                        "      - client2\n" +
                        "      - client3\n" +
                        "      - client4\n" +
                        "    run-scripts:\n" +
                        "      - satellite\n" +
                        "  controller:\n" +
                        "    hosts:\n" +
                        "      - client1\n" +
                        "    run-scripts:\n" +
                        "      - controller\n" +
                        "  server:\n" +
                        "    hosts: server4\n" +
                        "    run-scripts:\n" +
                        "      - amq7\n")
        );

    }

    @Test
    public void emptyNestLine(){
        YamlParser parser = new YamlParser();
        parser.load("emptyNestLine",stream(""+
                "top : \n"+
                " - \n"+
                "   foo : fizz\n"+
                "   bar : buzz\n"+
                " - #commented\n"+
                "   foo : fuzz#commentedToo\n"+
                "   bar : bizz\n"
        ));


        validateParse(parser);


    }
    @Test
    public void multiLineInlineMap(){
        YamlParser parser = new YamlParser();
        parser.load("multiLineInlineMap",stream(""+
            "top : {\n"+
            "    foo : fizz\n"+
            "    bar : buzz}\n"+
            "  - entry : here"
        ));

        validateParse(parser);
        Json json = parser.getJson("multiLineInlineMap");
        assertEquals("should only contain one entry",1,json.size());
        json = json.getJson(0,EMPTY_ARRAY);
        assertEquals("top should have 2 children",2,json.getJson(CHILD,EMPTY_ARRAY).size());
        Json inline = json.getJson(CHILD,EMPTY_ARRAY).getJson(0,EMPTY_ARRAY);
        Json entry = json.getJson(CHILD,EMPTY_ARRAY).getJson(1,EMPTY_ARRAY);
        assertEquals("first entry should contain two pairs: \n"+inline.toString(2),2,inline.size());
        assertEquals("second entry should contain one pair: \n"+entry.toString(2),1,entry.size());



    }

    @Test
    public void multipleDocuments(){
        YamlParser parser = new YamlParser();
        parser.load("states.yaml",
            stream("\n"+
                "name: specjms \n" +
                "scripts: \n"+
                " - test: \n"+
                "    - sh: alpha \n"+
                "---\n"+
                "states: \n"+
                "  run:\n" +
                "    foo: bar\n"

            )
        );


    }

    @Test
    public void allStateOptions(){
        YamlParser parser = new YamlParser();
        parser.load("states.yaml",
                stream("\n"+
                        "states:\n" +
                        "  run:\n" +
                        "    FOO: 42\n" +
                        "\n" +
                        "  host:\n" +
                        "    local:\n" +
                        "      BAR : home\n" +
                        "      script:\n" +
                        "        myScript:\n" +
                        "          BUZ: bar")
        );

        validateParse(parser);

    }

    @Test
    public void commandNesting(){
        YamlParser parser = new YamlParser();

        parser.load("nmesting.yaml",
                stream(
                        "scripts:\n" +
                                "  myScript:\n" +
                                "    - sh: 1\n" +
                                "    - - sh: 2\n" +
                                "    - - - sh: 3\n" +
                                "    - - sh: 4\n" +
                                "    - - - sh: 5\n" +
                                "    - sh: 6\n"
                )
        );

        validateParse(parser);
    }

}
