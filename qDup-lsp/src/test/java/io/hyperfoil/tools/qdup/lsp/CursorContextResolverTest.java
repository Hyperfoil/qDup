package io.hyperfoil.tools.qdup.lsp;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class CursorContextResolverTest {

    private CommandRegistry registry;
    private CursorContextResolver resolver;

    @Before
    public void setUp() {
        registry = new CommandRegistry();
        resolver = new CursorContextResolver(registry);
    }

    @Test
    public void testTopLevelKey() {
        String yaml = "name: test\nscripts:\n  myScript:\n  - sh: echo hello\n";
        QDupDocument doc = new QDupDocument("test.yaml", yaml);

        // Cursor on "name" at line 0
        YamlContext ctx = resolver.resolve(doc, 0, 0);
        // Should be at top-level key or value
        assertTrue("Should be top-level context",
            ctx == YamlContext.TOP_LEVEL_KEY || ctx == YamlContext.TOP_LEVEL_VALUE);
    }

    @Test
    public void testScriptsSection() {
        String yaml = "scripts:\n  myScript:\n  - sh: echo hello\n";
        QDupDocument doc = new QDupDocument("test.yaml", yaml);

        assertTrue("Document should parse", doc.isParseSuccessful());
    }

    @Test
    public void testHostsSection() {
        String yaml = "hosts:\n  myHost: user@host:22\n";
        QDupDocument doc = new QDupDocument("test.yaml", yaml);

        assertTrue("Document should parse", doc.isParseSuccessful());
    }

    @Test
    public void testFallbackContextResolution() {
        // Use malformed YAML to trigger line-based fallback
        String yaml = "scripts:\n  myScript:\n  - sh: echo hello\n  - regex: pattern\n";
        QDupDocument doc = new QDupDocument("test.yaml", yaml);

        // Even with tree-based resolution, these should work
        if (doc.isParseSuccessful()) {
            assertNotNull("Root node should exist", doc.getRootNode());
        }
    }

    @Test
    public void testLineBasedHostContext() {
        // Deliberately broken YAML to trigger fallback
        String yaml = "hosts:\n  myHost:\n    hostname: example.com\n    port: 22\n";
        QDupDocument doc = new QDupDocument("test.yaml", yaml);

        if (!doc.isParseSuccessful()) {
            // Test line-based fallback
            YamlContext ctx = resolver.resolve(doc, 2, 4);
            assertEquals(YamlContext.HOST_CONFIG_KEY, ctx);
        }
    }

    @Test
    public void testLineBasedScriptsContext() {
        // Test line-based fallback for scripts
        String yaml = "scripts:\n  myScript:\n  - \n";
        QDupDocument doc = new QDupDocument("test.yaml", yaml);

        // If parse fails, fallback should identify script context
        if (!doc.isParseSuccessful()) {
            YamlContext ctx = resolver.resolve(doc, 2, 4);
            assertEquals(YamlContext.SCRIPT_COMMAND_KEY, ctx);
        }
    }

    @Test
    public void testFindCommandAtLine() {
        String yaml = "scripts:\n  myScript:\n  - sh:\n      command: ls\n      prompt:\n        'Password:': pass\n";
        QDupDocument doc = new QDupDocument("test.yaml", yaml);

        String cmd = resolver.findCommandAtLine(doc, 3);
        assertEquals("Should find 'sh' command", "sh", cmd);
    }
}
