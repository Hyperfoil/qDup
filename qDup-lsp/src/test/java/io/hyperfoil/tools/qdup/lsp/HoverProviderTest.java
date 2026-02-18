package io.hyperfoil.tools.qdup.lsp;

import org.eclipse.lsp4j.Hover;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Properties;

import static org.junit.Assert.*;

public class HoverProviderTest {

    private CommandRegistry registry;
    private CursorContextResolver contextResolver;
    private HoverProvider provider;
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
        provider = new HoverProvider(registry, contextResolver, commandDocs);
    }

    @Test
    public void testHoverOnCommand() {
        QDupDocument doc = new QDupDocument("test.yaml",
                "scripts:\n  myScript:\n  - sh: echo hello");
        // hover over "sh" at line 2, character 4
        Hover hover = provider.hover(doc, 2, 4);
        assertNotNull("Should return hover for 'sh' command", hover);
        String content = hover.getContents().getRight().getValue();
        assertTrue("Hover should mention 'sh'", content.contains("**sh**"));
    }

    @Test
    public void testHoverOnNoArgCommand() {
        QDupDocument doc = new QDupDocument("test.yaml",
                "scripts:\n  myScript:\n  - ctrlC");
        Hover hover = provider.hover(doc, 2, 6);
        assertNotNull("Should return hover for 'ctrlC'", hover);
        String content = hover.getContents().getRight().getValue();
        assertTrue("Hover should mention 'ctrlC'", content.contains("**ctrlC**"));
    }

    @Test
    public void testHoverOnCommandParamFallsBackToModifier() {
        // When "silent" appears as a parameter under a command, COMMAND_PARAM_KEY context
        // falls back to modifier hover when no param doc is found
        String yaml = "scripts:\n  myScript:\n    - sh:\n        command: echo hello\n        silent: true";
        QDupDocument doc = new QDupDocument("test.yaml", yaml);
        Hover hover = provider.hover(doc, 4, 10);
        assertNotNull("Should return hover for 'silent' (modifier fallback)", hover);
        String content = hover.getContents().getRight().getValue();
        assertTrue("Hover should mention 'silent'", content.contains("**silent**"));
    }

    @Test
    public void testHoverOnElseModifier() {
        // "else" under a regex command at the same indent level
        String yaml = "scripts:\n  myScript:\n    - regex: \".*foo.*\"";
        QDupDocument doc = new QDupDocument("test.yaml", yaml);
        Hover hover = provider.hover(doc, 2, 8);
        assertNotNull("Should return hover for 'regex' command", hover);
        String content = hover.getContents().getRight().getValue();
        assertTrue("Hover should mention 'regex'", content.contains("**regex**"));
    }

    @Test
    public void testHoverOnTopLevelKey() {
        QDupDocument doc = new QDupDocument("test.yaml", "scripts:");
        Hover hover = provider.hover(doc, 0, 3);
        assertNotNull("Should return hover for 'scripts' top-level key", hover);
        String content = hover.getContents().getRight().getValue();
        assertTrue("Hover should mention 'scripts'", content.contains("**scripts**"));
    }

    @Test
    public void testHoverOnHostConfigKey() {
        QDupDocument doc = new QDupDocument("test.yaml",
                "hosts:\n  myHost:\n    username: root");
        Hover hover = provider.hover(doc, 2, 6);
        assertNotNull("Should return hover for 'username'", hover);
        String content = hover.getContents().getRight().getValue();
        assertTrue("Hover should mention 'username'", content.contains("**username**"));
    }

    @Test
    public void testHoverOnRoleKey() {
        QDupDocument doc = new QDupDocument("test.yaml",
                "roles:\n  myRole:\n    hosts:\n      - myHost\n    run-scripts:\n      - myScript");
        Hover hover = provider.hover(doc, 4, 6);
        assertNotNull("Should return hover for 'run-scripts'", hover);
        String content = hover.getContents().getRight().getValue();
        assertTrue("Hover should mention 'run-scripts'", content.contains("**run-scripts**"));
    }

    @Test
    public void testHoverOnStateVariable() {
        QDupDocument doc = new QDupDocument("test.yaml",
                "scripts:\n  myScript:\n  - sh: echo ${{myVar}}\nstates:\n  myVar: hello");
        Hover hover = provider.hover(doc, 2, 18, Collections.singleton(doc));
        assertNotNull("Should return hover for state variable", hover);
        String content = hover.getContents().getRight().getValue();
        assertTrue("Hover should contain variable name", content.contains("myVar"));
        assertTrue("Hover should contain value", content.contains("hello"));
    }

    @Test
    public void testHoverOnUndefinedStateVariable() {
        QDupDocument doc = new QDupDocument("test.yaml",
                "scripts:\n  myScript:\n  - sh: echo ${{unknown}}");
        Hover hover = provider.hover(doc, 2, 19, Collections.singleton(doc));
        assertNotNull("Should return hover for undefined variable", hover);
        String content = hover.getContents().getRight().getValue();
        assertTrue("Hover should indicate undefined", content.contains("undefined"));
    }

    @Test
    public void testHoverOnNonWord() {
        QDupDocument doc = new QDupDocument("test.yaml", "   ");
        Hover hover = provider.hover(doc, 0, 1);
        assertNull("Should return null for whitespace", hover);
    }

    @Test
    public void testHoverOnCommandParameter() {
        QDupDocument doc = new QDupDocument("test.yaml",
                "scripts:\n  myScript:\n  - sh:\n      command: echo hello");
        // hover over "command" at line 3
        Hover hover = provider.hover(doc, 3, 8);
        assertNotNull("Should return hover for 'command' parameter", hover);
        String content = hover.getContents().getRight().getValue();
        assertTrue("Hover should mention 'command'", content.contains("**command**"));
    }
}
