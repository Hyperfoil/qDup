package io.hyperfoil.tools.qdup.lsp;

import org.eclipse.lsp4j.CompletionItem;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class CompletionProviderTest {

    private CommandRegistry registry;
    private CursorContextResolver contextResolver;
    private CompletionProvider provider;
    private Properties commandDocs;

    @Before
    public void setUp() {
        registry = new CommandRegistry();
        contextResolver = new CursorContextResolver(registry);
        commandDocs = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("command-docs.properties")) {
            if (is != null) {
                commandDocs.load(is);
            }
        } catch (IOException e) {
            // Use empty docs
        }
        provider = new CompletionProvider(registry, contextResolver, commandDocs);
    }

    @Test
    public void testTopLevelKeyCompletions() {
        QDupDocument doc = new QDupDocument("test.yaml", "");
        List<CompletionItem> items = provider.completeForContext(YamlContext.TOP_LEVEL_KEY, doc, 0);

        Set<String> labels = items.stream().map(CompletionItem::getLabel).collect(Collectors.toSet());
        assertTrue("Should include 'scripts'", labels.contains("scripts"));
        assertTrue("Should include 'hosts'", labels.contains("hosts"));
        assertTrue("Should include 'roles'", labels.contains("roles"));
        assertTrue("Should include 'name'", labels.contains("name"));
        assertTrue("Should include 'states'", labels.contains("states"));
        assertTrue("Should include 'globals'", labels.contains("globals"));
    }

    @Test
    public void testCommandKeyCompletions() {
        QDupDocument doc = new QDupDocument("test.yaml", "scripts:\n  myScript:\n  - ");
        List<CompletionItem> items = provider.completeForContext(YamlContext.SCRIPT_COMMAND_KEY, doc, 2);

        Set<String> labels = items.stream().map(CompletionItem::getLabel).collect(Collectors.toSet());
        assertTrue("Should include 'sh'", labels.contains("sh"));
        assertTrue("Should include 'regex'", labels.contains("regex"));
        assertTrue("Should include 'set-state'", labels.contains("set-state"));
        assertTrue("Should include 'ctrlC'", labels.contains("ctrlC"));
        assertTrue("Should include 'log'", labels.contains("log"));
    }

    @Test
    public void testModifierKeyCompletions() {
        QDupDocument doc = new QDupDocument("test.yaml", "");
        List<CompletionItem> items = provider.completeForContext(YamlContext.COMMAND_MODIFIER_KEY, doc, 0);

        Set<String> labels = items.stream().map(CompletionItem::getLabel).collect(Collectors.toSet());
        assertTrue("Should include 'then'", labels.contains("then"));
        assertTrue("Should include 'with'", labels.contains("with"));
        assertTrue("Should include 'watch'", labels.contains("watch"));
        assertTrue("Should include 'timer'", labels.contains("timer"));
        assertTrue("Should include 'else'", labels.contains("else"));
    }

    @Test
    public void testHostConfigKeyCompletions() {
        QDupDocument doc = new QDupDocument("test.yaml", "");
        List<CompletionItem> items = provider.completeForContext(YamlContext.HOST_CONFIG_KEY, doc, 0);

        Set<String> labels = items.stream().map(CompletionItem::getLabel).collect(Collectors.toSet());
        assertTrue("Should include 'hostname'", labels.contains("hostname"));
        assertTrue("Should include 'username'", labels.contains("username"));
        assertTrue("Should include 'port'", labels.contains("port"));
        assertTrue("Should include 'identity'", labels.contains("identity"));
    }

    @Test
    public void testRoleKeyCompletions() {
        QDupDocument doc = new QDupDocument("test.yaml", "");
        List<CompletionItem> items = provider.completeForContext(YamlContext.ROLE_KEY, doc, 0);

        Set<String> labels = items.stream().map(CompletionItem::getLabel).collect(Collectors.toSet());
        assertTrue("Should include 'hosts'", labels.contains("hosts"));
        assertTrue("Should include 'run-scripts'", labels.contains("run-scripts"));
        assertTrue("Should include 'setup-scripts'", labels.contains("setup-scripts"));
        assertTrue("Should include 'cleanup-scripts'", labels.contains("cleanup-scripts"));
    }

    @Test
    public void testRoleScriptRefCompletions() {
        String yaml = "scripts:\n  buildApp:\n  - sh: mvn package\n  deployApp:\n  - sh: deploy.sh\nroles:\n  builder:\n    run-scripts:\n    - ";
        QDupDocument doc = new QDupDocument("test.yaml", yaml);
        List<CompletionItem> items = provider.completeForContext(YamlContext.ROLE_SCRIPT_REF, doc, 8);

        Set<String> labels = items.stream().map(CompletionItem::getLabel).collect(Collectors.toSet());
        assertTrue("Should include 'buildApp'", labels.contains("buildApp"));
        assertTrue("Should include 'deployApp'", labels.contains("deployApp"));
    }

    @Test
    public void testCompletionInsideVariablePattern() {
        String yaml = String.join("\n",
                "name: test",
                "states:",
                "  WF_HOME: /opt/wildfly",
                "  USER: admin",
                "scripts:",
                "  deploy:",
                "    - sh: cd ${{",
                ""
        );
        QDupDocument doc = new QDupDocument("test.yaml", yaml);
        // Line 6: "    - sh: cd ${{", cursor at end (column 19)
        List<CompletionItem> items = provider.complete(doc, 6, 19, java.util.List.of(doc));

        Set<String> labels = items.stream().map(CompletionItem::getLabel).collect(Collectors.toSet());
        assertTrue("Should include 'WF_HOME'", labels.contains("WF_HOME"));
        assertTrue("Should include 'USER'", labels.contains("USER"));
        // Verify they are Variable kind
        for (CompletionItem item : items) {
            assertEquals(org.eclipse.lsp4j.CompletionItemKind.Variable, item.getKind());
        }
    }

    @Test
    public void testCompletionInsideVariablePatternCrossFile() {
        String scriptYaml = String.join("\n",
                "scripts:",
                "  run:",
                "    - sh: echo ${{",
                ""
        );
        String stateYaml = String.join("\n",
                "states:",
                "  DB_PORT: 5432",
                ""
        );
        QDupDocument scriptDoc = new QDupDocument("file:///script.yaml", scriptYaml);
        QDupDocument stateDoc = new QDupDocument("file:///state.yaml", stateYaml);

        List<CompletionItem> items = provider.complete(scriptDoc, 2, 22, java.util.List.of(scriptDoc, stateDoc));

        Set<String> labels = items.stream().map(CompletionItem::getLabel).collect(Collectors.toSet());
        assertTrue("Should include cross-file state variable 'DB_PORT'", labels.contains("DB_PORT"));
    }

    @Test
    public void testNoArgCommandInsertText() {
        QDupDocument doc = new QDupDocument("test.yaml", "");
        List<CompletionItem> items = provider.completeForContext(YamlContext.SCRIPT_COMMAND_KEY, doc, 0);

        // Find ctrlC completion
        CompletionItem ctrlC = items.stream()
            .filter(i -> "ctrlC".equals(i.getLabel()))
            .findFirst()
            .orElse(null);
        assertNotNull("Should have ctrlC completion", ctrlC);
        assertEquals("No-arg command should not have ': ' suffix", "ctrlC", ctrlC.getInsertText());

        // Find sh completion
        CompletionItem sh = items.stream()
            .filter(i -> "sh".equals(i.getLabel()))
            .findFirst()
            .orElse(null);
        assertNotNull("Should have sh completion", sh);
        assertEquals("Regular command should have ': ' suffix", "sh: ", sh.getInsertText());
    }
}
