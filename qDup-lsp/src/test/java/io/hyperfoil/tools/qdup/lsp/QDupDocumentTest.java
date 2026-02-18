package io.hyperfoil.tools.qdup.lsp;

import org.junit.Test;
import org.yaml.snakeyaml.nodes.Node;

import java.util.Set;

import static org.junit.Assert.*;

public class QDupDocumentTest {

    @Test
    public void testParseValidDocument() {
        QDupDocument doc = new QDupDocument("test.yaml",
                "scripts:\n  myScript:\n  - sh: echo hello");
        assertTrue("Valid YAML should parse successfully", doc.isParseSuccessful());
        assertNotNull("Root node should not be null", doc.getRootNode());
    }

    @Test
    public void testParseInvalidYaml() {
        QDupDocument doc = new QDupDocument("test.yaml",
                "scripts:\n  - :\n  invalid: [");
        assertFalse("Invalid YAML should not parse successfully", doc.isParseSuccessful());
    }

    @Test
    public void testParseEmptyDocument() {
        QDupDocument doc = new QDupDocument("test.yaml", "");
        assertFalse("Empty document should not parse successfully", doc.isParseSuccessful());
    }

    @Test
    public void testGetLine() {
        QDupDocument doc = new QDupDocument("test.yaml", "line0\nline1\nline2");
        assertEquals("line0", doc.getLine(0));
        assertEquals("line1", doc.getLine(1));
        assertEquals("line2", doc.getLine(2));
    }

    @Test
    public void testGetLineOutOfBounds() {
        QDupDocument doc = new QDupDocument("test.yaml", "only line");
        assertEquals("", doc.getLine(-1));
        assertEquals("", doc.getLine(5));
    }

    @Test
    public void testGetScriptNames() {
        QDupDocument doc = new QDupDocument("test.yaml",
                "scripts:\n  setup:\n  - sh: echo setup\n  cleanup:\n  - sh: echo cleanup");
        Set<String> names = doc.getScriptNames();
        assertTrue("Should contain 'setup'", names.contains("setup"));
        assertTrue("Should contain 'cleanup'", names.contains("cleanup"));
        assertEquals(2, names.size());
    }

    @Test
    public void testGetScriptNamesNoScriptsSection() {
        QDupDocument doc = new QDupDocument("test.yaml", "hosts:\n  myHost: user@host");
        Set<String> names = doc.getScriptNames();
        assertTrue("Should be empty when no scripts section", names.isEmpty());
    }

    @Test
    public void testGetHostNames() {
        QDupDocument doc = new QDupDocument("test.yaml",
                "hosts:\n  server1:\n    hostname: host1\n  server2:\n    hostname: host2");
        Set<String> names = doc.getHostNames();
        assertTrue("Should contain 'server1'", names.contains("server1"));
        assertTrue("Should contain 'server2'", names.contains("server2"));
        assertEquals(2, names.size());
    }

    @Test
    public void testGetStateKeys() {
        QDupDocument doc = new QDupDocument("test.yaml",
                "states:\n  foo: bar\n  count: 5");
        Set<String> keys = doc.getStateKeys();
        assertTrue("Should contain 'foo'", keys.contains("foo"));
        assertTrue("Should contain 'count'", keys.contains("count"));
    }

    @Test
    public void testGetStateValue() {
        QDupDocument doc = new QDupDocument("test.yaml",
                "states:\n  myKey: myValue");
        assertEquals("myValue", doc.getStateValue("myKey"));
        assertNull(doc.getStateValue("nonexistent"));
    }

    @Test
    public void testGetNestedStateValue() {
        QDupDocument doc = new QDupDocument("test.yaml",
                "states:\n  server:\n    host: example.com\n    port: 8080");
        assertEquals("example.com", doc.getStateValue("server.host"));
        assertEquals("8080", doc.getStateValue("server.port"));
    }

    @Test
    public void testFindScriptNode() {
        QDupDocument doc = new QDupDocument("test.yaml",
                "scripts:\n  myScript:\n  - sh: echo hello");
        Node node = doc.findScriptNode("myScript");
        assertNotNull("Should find 'myScript' node", node);
        assertNull("Should not find nonexistent script", doc.findScriptNode("missing"));
    }

    @Test
    public void testFindHostNode() {
        QDupDocument doc = new QDupDocument("test.yaml",
                "hosts:\n  myHost:\n    hostname: example.com");
        Node node = doc.findHostNode("myHost");
        assertNotNull("Should find 'myHost' node", node);
        assertNull("Should not find nonexistent host", doc.findHostNode("missing"));
    }

    @Test
    public void testFindStateNode() {
        QDupDocument doc = new QDupDocument("test.yaml",
                "states:\n  myVar: hello");
        assertNotNull("Should find state node", doc.findStateNode("myVar"));
        assertNull("Should not find missing state", doc.findStateNode("missing"));
    }

    @Test
    public void testFindNestedStateNode() {
        QDupDocument doc = new QDupDocument("test.yaml",
                "states:\n  server:\n    host: example.com");
        assertNotNull("Should find nested state node", doc.findStateNode("server.host"));
    }

    @Test
    public void testFindStateNodeInGlobals() {
        QDupDocument doc = new QDupDocument("test.yaml",
                "globals:\n  globalVar: value");
        assertNotNull("Should find state in globals section", doc.findStateNode("globalVar"));
    }

    @Test
    public void testGetReferencedScripts() {
        QDupDocument doc = new QDupDocument("test.yaml",
                "roles:\n  myRole:\n    hosts:\n      - h1\n    run-scripts:\n      - scriptA\n      - scriptB");
        Set<String> refs = doc.getReferencedScripts();
        assertTrue("Should reference 'scriptA'", refs.contains("scriptA"));
        assertTrue("Should reference 'scriptB'", refs.contains("scriptB"));
    }

    @Test
    public void testGetReferencedHosts() {
        QDupDocument doc = new QDupDocument("test.yaml",
                "roles:\n  myRole:\n    hosts:\n      - hostA\n      - hostB\n    run-scripts:\n      - s1");
        Set<String> refs = doc.getReferencedHosts();
        assertTrue("Should reference 'hostA'", refs.contains("hostA"));
        assertTrue("Should reference 'hostB'", refs.contains("hostB"));
    }

    @Test
    public void testSetText() {
        QDupDocument doc = new QDupDocument("test.yaml", "scripts:");
        assertTrue(doc.isParseSuccessful());
        assertEquals("scripts:", doc.getLine(0));

        doc.setText("hosts:\n  h1: user@host");
        assertTrue(doc.isParseSuccessful());
        assertEquals("hosts:", doc.getLine(0));
    }

    @Test
    public void testFindStateNodeNullInput() {
        QDupDocument doc = new QDupDocument("test.yaml", "states:\n  foo: bar");
        assertNull(doc.findStateNode(null));
    }

    @Test
    public void testGetStateValueNullInput() {
        QDupDocument doc = new QDupDocument("test.yaml", "states:\n  foo: bar");
        assertNull(doc.getStateValue(null));
    }

    @Test
    public void testGetStateKeysNested() {
        QDupDocument doc = new QDupDocument("test.yaml",
                "states:\n  server:\n    host: example.com\n    port: 8080\n  flat: value");
        Set<String> keys = doc.getStateKeys();
        assertTrue("Should contain top-level 'server'", keys.contains("server"));
        assertTrue("Should contain top-level 'flat'", keys.contains("flat"));
        assertTrue("Should contain nested 'server.host'", keys.contains("server.host"));
        assertTrue("Should contain nested 'server.port'", keys.contains("server.port"));
    }
}
