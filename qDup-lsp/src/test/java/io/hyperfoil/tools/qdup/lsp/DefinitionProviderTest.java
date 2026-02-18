package io.hyperfoil.tools.qdup.lsp;

import org.eclipse.lsp4j.Location;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class DefinitionProviderTest {

    private CommandRegistry registry;
    private CursorContextResolver contextResolver;
    private DefinitionProvider provider;

    @Before
    public void setUp() {
        registry = new CommandRegistry();
        contextResolver = new CursorContextResolver(registry);
        provider = new DefinitionProvider(contextResolver);
    }

    private static final String FULL_YAML = String.join("\n",
            "name: test",
            "scripts:",
            "  start-db:",
            "    - sh: echo starting db",
            "  start-app:",
            "    - sh: echo starting app",
            "    - script: start-db",
            "hosts:",
            "  local: me@localhost",
            "  remote: user@server:22",
            "roles:",
            "  test-role:",
            "    hosts:",
            "      - local",
            "    run-scripts:",
            "      - start-app",
            ""
    );

    @Test
    public void testDefinitionForScriptRefInRole() {
        // "      - start-app" is at line 15, cursor on "start-app"
        QDupDocument doc = new QDupDocument("test.yaml", FULL_YAML);
        Location loc = provider.definition(doc, 15, 10);

        assertNotNull("Should find definition for script ref 'start-app'", loc);
        assertEquals("test.yaml", loc.getUri());
        // "start-app:" is the key at line 4
        assertEquals(4, loc.getRange().getStart().getLine());
    }

    @Test
    public void testDefinitionForHostRefInRole() {
        // "      - local" is at line 13, cursor on "local"
        QDupDocument doc = new QDupDocument("test.yaml", FULL_YAML);
        Location loc = provider.definition(doc, 13, 10);

        assertNotNull("Should find definition for host ref 'local'", loc);
        assertEquals("test.yaml", loc.getUri());
        // "local:" is the key at line 8
        assertEquals(8, loc.getRange().getStart().getLine());
    }

    @Test
    public void testDefinitionForScriptCommand() {
        // "    - script: start-db" is at line 6, cursor on "start-db"
        QDupDocument doc = new QDupDocument("test.yaml", FULL_YAML);
        Location loc = provider.definition(doc, 6, 18);

        assertNotNull("Should find definition for 'script: start-db'", loc);
        assertEquals("test.yaml", loc.getUri());
        // "start-db:" is the key at line 2
        assertEquals(2, loc.getRange().getStart().getLine());
    }

    @Test
    public void testDefinitionReturnsNullForNonReference() {
        // "name: test" is at line 0, cursor on "test" - no definition
        QDupDocument doc = new QDupDocument("test.yaml", FULL_YAML);
        Location loc = provider.definition(doc, 0, 7);

        assertNull("Should return null for non-reference context", loc);
    }

    @Test
    public void testDefinitionReturnsNullForUndefinedScript() {
        String yaml = String.join("\n",
                "scripts:",
                "  myScript:",
                "    - sh: echo hello",
                "roles:",
                "  myRole:",
                "    run-scripts:",
                "      - nonExistent",
                ""
        );
        QDupDocument doc = new QDupDocument("test.yaml", yaml);
        Location loc = provider.definition(doc, 6, 10);

        assertNull("Should return null for undefined script reference", loc);
    }

    @Test
    public void testDefinitionReturnsNullForBrokenYaml() {
        String yaml = "scripts:\n  myScript:\n  - sh: {\n";
        QDupDocument doc = new QDupDocument("test.yaml", yaml);
        Location loc = provider.definition(doc, 1, 3);

        assertNull("Should return null when YAML parse failed", loc);
    }

    @Test
    public void testExtractWordAt() {
        assertEquals("start-db", DefinitionProvider.extractWordAt("    - script: start-db", 18));
        assertEquals("local", DefinitionProvider.extractWordAt("      - local", 10));
        assertEquals("script", DefinitionProvider.extractWordAt("    - script: start-db", 8));
        assertNull(DefinitionProvider.extractWordAt("", 0));
        assertNull(DefinitionProvider.extractWordAt("   ", 1));
    }

    // --- Cross-file definition tests ---

    private static final String MAIN_YAML = String.join("\n",
            "name: main",
            "roles:",
            "  test-role:",
            "    hosts:",
            "      - remote-server",
            "    run-scripts:",
            "      - start-heroes-db",
            ""
    );

    private static final String SCRIPTS_YAML = String.join("\n",
            "name: scripts",
            "scripts:",
            "  start-heroes-db:",
            "    - sh: echo starting db",
            "  stop-heroes-db:",
            "    - sh: echo stopping db",
            "hosts:",
            "  remote-server: user@server:22",
            ""
    );

    @Test
    public void testCrossFileScriptDefinition() {
        QDupDocument mainDoc = new QDupDocument("file:///main.qdup.yaml", MAIN_YAML);
        QDupDocument scriptsDoc = new QDupDocument("file:///scripts.qdup.yaml", SCRIPTS_YAML);

        // "      - start-heroes-db" is at line 6 in mainDoc, cursor on "start-heroes-db"
        Location loc = provider.definition(mainDoc, 6, 10, List.of(mainDoc, scriptsDoc));

        assertNotNull("Should find cross-file definition for script 'start-heroes-db'", loc);
        assertEquals("file:///scripts.qdup.yaml", loc.getUri());
        // "start-heroes-db:" is the key at line 2 in scriptsDoc
        assertEquals(2, loc.getRange().getStart().getLine());
    }

    @Test
    public void testCrossFileHostDefinition() {
        QDupDocument mainDoc = new QDupDocument("file:///main.qdup.yaml", MAIN_YAML);
        QDupDocument scriptsDoc = new QDupDocument("file:///scripts.qdup.yaml", SCRIPTS_YAML);

        // "      - remote-server" is at line 4 in mainDoc, cursor on "remote-server"
        Location loc = provider.definition(mainDoc, 4, 10, List.of(mainDoc, scriptsDoc));

        assertNotNull("Should find cross-file definition for host 'remote-server'", loc);
        assertEquals("file:///scripts.qdup.yaml", loc.getUri());
        // "remote-server:" is the key at line 7 in scriptsDoc
        assertEquals(7, loc.getRange().getStart().getLine());
    }

    // --- State variable definition tests ---

    private static final String STATE_YAML = String.join("\n",
            "name: state-test",
            "states:",
            "  FOO: bar",
            "  GREETING: Hello qDup!",
            "scripts:",
            "  myScript:",
            "    - sh: echo ${{FOO}}",
            "    - sh: echo ${{GREETING}}",
            ""
    );

    @Test
    public void testDefinitionForStateVariable() {
        QDupDocument doc = new QDupDocument("test.yaml", STATE_YAML);
        // Line 6: "    - sh: echo ${{FOO}}", cursor on "FOO" inside ${{FOO}}
        // "${{FOO}}" starts at column 18, "FOO" is at column 21
        Location loc = provider.definition(doc, 6, 21);

        assertNotNull("Should find definition for state variable 'FOO'", loc);
        assertEquals("test.yaml", loc.getUri());
        // "FOO:" is the key at line 2
        assertEquals(2, loc.getRange().getStart().getLine());
    }

    @Test
    public void testCrossFileStateVariableDefinition() {
        String scriptYaml = String.join("\n",
                "name: script-file",
                "scripts:",
                "  run:",
                "    - sh: echo ${{DB_HOST}}",
                ""
        );
        String stateYaml = String.join("\n",
                "name: state-file",
                "states:",
                "  DB_HOST: localhost",
                ""
        );
        QDupDocument scriptDoc = new QDupDocument("file:///script.yaml", scriptYaml);
        QDupDocument stateDoc = new QDupDocument("file:///state.yaml", stateYaml);

        // Line 3: "    - sh: echo ${{DB_HOST}}", cursor on "DB_HOST"
        Location loc = provider.definition(scriptDoc, 3, 22, List.of(scriptDoc, stateDoc));

        assertNotNull("Should find cross-file state variable definition", loc);
        assertEquals("file:///state.yaml", loc.getUri());
        assertEquals(2, loc.getRange().getStart().getLine());
    }

    @Test
    public void testExtractVariableAt() {
        assertEquals("FOO", DefinitionProvider.extractVariableAt("echo ${{FOO}}", 9));
        assertEquals("GREETING", DefinitionProvider.extractVariableAt("echo ${{GREETING}}", 12));
        assertEquals("FOO", DefinitionProvider.extractVariableAt("${{FOO:default}}", 5));
        assertNull(DefinitionProvider.extractVariableAt("echo hello", 5));
        assertNull(DefinitionProvider.extractVariableAt("echo ${{FOO}} ${{BAR}}", 14));
        assertEquals("BAR", DefinitionProvider.extractVariableAt("echo ${{FOO}} ${{BAR}}", 19));
        assertNull(DefinitionProvider.extractVariableAt("", 0));
        assertNull(DefinitionProvider.extractVariableAt(null, 0));
    }

    @Test
    public void testCurrentDocTakesPriority() {
        // Both docs define 'start-heroes-db', current doc should win
        String mainWithScript = String.join("\n",
                "name: main",
                "scripts:",
                "  start-heroes-db:",
                "    - sh: echo local version",
                "roles:",
                "  test-role:",
                "    run-scripts:",
                "      - start-heroes-db",
                ""
        );
        QDupDocument mainDoc = new QDupDocument("file:///main.qdup.yaml", mainWithScript);
        QDupDocument scriptsDoc = new QDupDocument("file:///scripts.qdup.yaml", SCRIPTS_YAML);

        // "      - start-heroes-db" is at line 7 in mainDoc
        Location loc = provider.definition(mainDoc, 7, 10, List.of(mainDoc, scriptsDoc));

        assertNotNull("Should find definition in current doc", loc);
        assertEquals("file:///main.qdup.yaml", loc.getUri());
        // "start-heroes-db:" is the key at line 2 in mainDoc
        assertEquals(2, loc.getRange().getStart().getLine());
    }
}
