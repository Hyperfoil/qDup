package perf.qdup.config;

import org.junit.Assert;
import org.junit.Test;
import perf.qdup.SshTestBase;
import perf.yaup.json.Json;

import java.util.LinkedList;
import java.util.Queue;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static perf.qdup.config.YamlParser.*;

public class YamlParserTest extends SshTestBase {

    private static Json EMPTY_ARRAY = new Json();
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
            "name: syntax",
            "scripts:",
            "  firstScript:#this is my first script",
            "    - sh: inline shell arguments",
            "    - queue-download:",
            "      path: ./",
            "      destination: ./",
            "    - sh: top",
            "      - sh: second",
            "      - sh: third",
            "    - sh: [first, second, third]",
            "      - watch:",
            "      - regex: \".*?\"",
            "        - abort: fail",
            "      - sh: childCommand",
            "    - invoke: ${{scriptName}}",
            "  secondScript:#this is the otherScript",
            "    - sh: do this please",
            "    - abort: ha!",
            "hosts:",
            "  laptop: wreicher@laptop",
            "  server:",
            "     username: root",
            "     hostname: serverName",
            "     port: 22",
            "---",
            "roles:",
            "  ALL:",
            "    setup-scripts:",
            "     - firstScript",
            "    run-scripts",
            "     - secondScript",
            "    cleanup-scripts:",
            "     - ${{cleanupScript}}",
            "---",
            "states:",
            "  RUN:",
            "    FOO: bar",
            "  laptop:",
            "    FOO: biz",
            ""
            )
        );

        validateParse(parser);

    }

    @Test
    public void listCommandArgument(){
        YamlParser parser = new YamlParser();
        parser.load("listCommandArgument",stream(""+
                "foo: {",
                "  arg: \"value\"",
                "  options: [",
                "    \"first Option is crazy\"",
                "    \"second options is not bettter\"",
                "  ]",
                "}"
        ));
        validateParse(parser);
        System.out.println(parser.getJson("listCommandArgument").toString(2));
    }

    @Test
    public void multiPartValueString(){
        YamlParser parser = new YamlParser();
        parser.load("supportedSyntax",stream(""+
                "foo: \"biz \\\"buz:fuz\\\" :#boom\" 2ndValue#COMMENT",
                "bar: bar",
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
        YamlParser parser = new YamlParser();
        parser.load("supportedSyntax",stream(""+
                "#comment1",
                "#comment2",
                "0Level1 :",
                "0Level2 : hasValue",
                "  1 : normal",
                "  1 : \"quoted[{]}\\\"Value\"",
                "    - 1.1 : one",
                "      1.1.a:Alpha",
                "      1.1.b:Bravo",
                "    - 1.2 : two",
                "      1.2.y:Yankee",
                "    - 1.2.z:Zulu",
                "0Level3: #inlineComment",
                "  1 : [first, second, \"quoted\\\" :,[{]}\", other, [ subOne, subTwo], {subKey: subValue}, zed]",
                "  2 : bar",
                "  2 : {a:Apple,b:Banana , c : carrot, d : \"quoted\\\" :,[{]}\",e : [one, two], f: { fff: bbb}}",
                ""));

        Json json = parser.getJson("supportedSyntax");

        validateParse(parser);




    }

    @Test
    public void commandOptions(){
        YamlParser parser = new YamlParser();
        parser.load("comments",stream(""+
                "test:",
                "  - sh : shellArguments WITH spaces | and pipes | ${{variables}} and whatnot",
                "  - sh :",
                "      watch:",
                "        - regex: \".*?FOO\"",
                "            - log: foo",
                "  - argCmd:",
                "      arg1: value1",
                "      arg2: value2",
                "  - parentCmd:",
                "      - childCmd:",
                "          - grandChildCmd:",
                "              - greatGrandChildCmd:",
                "      - 2ndChildCmd:",
                ""
            )
        );
        validateParse(parser);

    }

    @Test
    public void testKeys(){
        YamlParser parser = new YamlParser();
        parser.load("comments",stream(""+
                "KEY:VALUE",
                "\"ke:y\":VALUE"));

        Json json = parser.getJson();

        validateParse(parser);

    }

    @Test
    public void comments(){
        YamlParser parser = new YamlParser();
        parser.load("comments",stream(""+
                "#full line",
                "#second full line",
                "a : a",
                "  a.a : a.a #inline",
                "  #aComment1",
                "  a.b : \"a.a #notComment\"",
                "#commentTreeBreak",
                "  a.c : a.a #withQuote\"",
                "  a.d : a.d \\#notComment",
                "  #aComment2"));

        Json json = parser.getJson();

        validateParse(parser);

    }


    @Test
    public void spaceNesting(){
        YamlParser parser = new YamlParser();
        parser.load("spaceNesting",stream(""+
                "#startComment",
                "#2ndstartComment",
                "a : a",
                "  a.a : a.a",
                "    a.a.a : a.a.a #commentAAA",
                "      - a.a.a.a : a.a.a.a",
                "        a.a.a.b : a.a.a.b",
                "      - a.a.a.c : a.a.a.c",
                "        a.a.a.d : a.a.a.d",
                "  a.b : a.b"));

        validateParse(parser);

        Json json = parser.getJson();






    }

    //Demonstrates a difference between how YamlParser and Yaml would parse input
    @Test
    public void listOfMaps(){
        YamlParser parser = new YamlParser();
        parser.load("listOfMaps",stream(""+
                "foo:",
                "  - name: firstName",
                "    label: firstLabel",
                "  - name: secondName",
                "    label: secondLabel"));
        validateParse(parser);


        Json json = parser.getJson();



    }

    // Tests for parsing the Yaml
    @Test
    public void nestedMap(){
        YamlParser parser = new YamlParser();
        parser.load("nestMap",stream(""+
        "foo:",
        " - foo.foo : ff",
        "   foo.bar : fb",
        " - foo.foo : ff",
        "   foo.bar : fb",
        "bar:",
        " - bar.foo : bf",
        "   bar.bar : bb"));



        validateParse(parser);

        Json json = parser.getJson();




    }


    @Test
    public void nesting(){
        YamlParser parser = new YamlParser();
        parser.load("nesting.yaml",stream(""+
            "foo: 1",
            "- foo.foo: 1.1",
            "  - foo.foo.foo: 1.1.1",
            "    - foo.foo.foo.foo : 1.1.1.1",
            "- foo.bar: 1.2",
            "  foo.biz: biz",
            ""
        ));

        Json json = parser.getJson();

        validateParse(parser);

    }
    @Test
    public void inlineList(){
        YamlParser parser = new YamlParser();
        parser.load("list.yaml",stream("---",
                "foo: [a, b , c  ,    d]",
                "bar: [ a , \"b[]][{}}{,\" , c,d ]",
                "biz: [ a [ a.a, a.b ], [b.a,b.b]]",
                ""
        ));
        validateParse(parser);
        Json json = parser.getJson("list.yaml");




    }
    @Test
    public void inlineMap(){
        YamlParser parser = new YamlParser();
        parser.load("map.yaml",stream("---",
                "top:",
                "  foo: {foo : 1 , foo.foo: 1 1 , foo.bar : \"1,][{}}{.2\" , foo.biz { f.b.a : one , f.b.a : two}, zed: end }",
                "  bar: biz",
                ""
        ));
        validateParse(parser);
        Json json = parser.getJson();




    }

    @Test
    public void variableKeyValue(){
        YamlParser parser = new YamlParser();
        parser.load("variableKeyValue",stream(""+
            "role:",
            "  - ${{keyNovalue}}",
            "  - ${{keyWithValue}} : ${{VALUE}} - ${{meToo}}",
            ""
        ));
        validateParse(parser);

    }

    // Tests for generating the RunConfig
    @Test
    public void variableScriptInRoles(){
        YamlParser parser = new YamlParser();
        parser.load("variableScriptInRole.yaml",
                stream("name: variableScriptInRole ",
                        "--- ",
                        "hosts:",
                        "  local: user@localhost",
                        "roles:",
                        "  test:",
                        "    run-scripts:",
                        "     - ${{runScript}}",
                        "    setup-scripts:",
                        "     - ${{setupScript}}",
                        "    cleanup-scripts:",
                        "     - ${{cleanupScript}}",
                        "    hosts:",
                        "      local",
                        "scripts:",
                        " - alpha: ",
                        "    - sh: alpha ",
                        " - bravo: ",
                        "    - sh: bravo ",
                        " - charlie: ",
                        "    - sh: charlie ",
                        "states:",
                        "  run:",
                        "    runScript: alpha",
                        "    setupScript: bravo",
                        "    cleanupScript: charlie"
                )
        );
        validateParse(parser);

    }
    @Test
    public void multipleHostsInRole(){
        YamlParser parser = new YamlParser();
        parser.load("multipleHosts.yaml",
                stream("name: multipleHosts ",
                        "---",
                        "hosts:",
                        "  client1: benchuser@benchclient1",
                        "  client2: benchuser@benchclient2",
                        "  client3: benchuser@benchclient3",
                        "  client4: benchuser@benchclient4",
                        "  server3:",
                        "    username: root",
                        "    hostname: benchserver3",
                        "  server4:",
                        "    username: benchuser",
                        "    hostname: benchserver4",
                        "    port: 22",
                        "",
                        "---",
                        "roles:",
                        "  ALL:",
                        "    setup-scripts:",
                        "     - sync-time",
                        "    run-scripts:",
                        "     - dstat",
                        "#  database:",
                        "#    hosts: server3",
                        "#    run-scripts: docker-oracle",
                        "  satellite:",
                        "    hosts:",
                        "      - client1",
                        "      - client2",
                        "      - client3",
                        "      - client4",
                        "    run-scripts:",
                        "      - satellite",
                        "  controller:",
                        "    hosts:",
                        "      - client1",
                        "    run-scripts:",
                        "      - controller",
                        "  server:",
                        "    hosts: server4",
                        "    run-scripts:",
                        "      - amq7")
        );

    }

    @Test
    public void emptyNestLine(){
        YamlParser parser = new YamlParser();
        parser.load("emptyNestLine",stream(""+
                "top : ",
                " - ",
                "   foo : fizz",
                "   bar : buzz",
                " - #commented",
                "   foo : fuzz#commentedToo",
                "   bar : bizz"
        ));


        validateParse(parser);


    }
    @Test
    public void multiLineInlineMap(){
        YamlParser parser = new YamlParser();
        parser.load("multiLineInlineMap",stream(""+
            "top : {",
            "    foo : fizz",
            "    bar : buzz}",
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
            stream("",
                "name: specjms ",
                "scripts: ",
                " - test: ",
                "    - sh: alpha ",
                "---",
                "states: ",
                "  run:",
                "    foo: bar"

            )
        );


    }

    @Test
    public void allStateOptions(){
        YamlParser parser = new YamlParser();
        parser.load("states.yaml",
                stream("",
                        "states:",
                        "  run:",
                        "    FOO: 42",
                        "",
                        "  host:",
                        "    local:",
                        "      BAR : home",
                        "      script:",
                        "        myScript:",
                        "          BUZ: bar")
        );

        validateParse(parser);

    }

    @Test
    public void commandNesting(){
        YamlParser parser = new YamlParser();

        parser.load("nmesting.yaml",
                stream(
                        "scripts:",
                                "  myScript:",
                                "    - sh: 1",
                                "    - - sh: 2",
                                "    - - - sh: 3",
                                "    - - sh: 4",
                                "    - - - sh: 5",
                                "    - sh: 6"
                )
        );

        validateParse(parser);
    }

}
