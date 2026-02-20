package io.hyperfoil.tools.qdup.lsp;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;

public class DocumentSymbolProviderTest {

    private DocumentSymbolProvider provider;

    @Before
    public void setUp() {
        provider = new DocumentSymbolProvider();
    }

    private static final String FULL_YAML = String.join("\n",
            "name: test",
            "scripts:",
            "  start-db:",
            "    - sh: echo starting db",
            "  start-app:",
            "    - sh: echo starting app",
            "hosts:",
            "  local: me@localhost",
            "  remote: user@server:22",
            "roles:",
            "  test-role:",
            "    hosts:",
            "      - local",
            "    run-scripts:",
            "      - start-app",
            "states:",
            "  DB_PORT: 5432",
            "  APP_NAME: myapp",
            ""
    );

    @Test
    public void testTopLevelSections() {
        QDupDocument doc = new QDupDocument("test.yaml", FULL_YAML);
        List<DocumentSymbol> symbols = provider.documentSymbols(doc);

        assertFalse("Should have symbols", symbols.isEmpty());

        // Should have name, scripts, hosts, roles, states
        assertTrue("Should have 'name' symbol", findSymbol(symbols, "name").isPresent());
        assertTrue("Should have 'scripts' symbol", findSymbol(symbols, "scripts").isPresent());
        assertTrue("Should have 'hosts' symbol", findSymbol(symbols, "hosts").isPresent());
        assertTrue("Should have 'roles' symbol", findSymbol(symbols, "roles").isPresent());
        assertTrue("Should have 'states' symbol", findSymbol(symbols, "states").isPresent());
    }

    @Test
    public void testScriptsSection() {
        QDupDocument doc = new QDupDocument("test.yaml", FULL_YAML);
        List<DocumentSymbol> symbols = provider.documentSymbols(doc);

        DocumentSymbol scripts = findSymbol(symbols, "scripts").orElse(null);
        assertNotNull(scripts);
        assertEquals(SymbolKind.Namespace, scripts.getKind());

        List<DocumentSymbol> children = scripts.getChildren();
        assertNotNull(children);
        assertEquals(2, children.size());

        assertTrue("Should have 'start-db' script", findSymbol(children, "start-db").isPresent());
        assertTrue("Should have 'start-app' script", findSymbol(children, "start-app").isPresent());

        // Script children should be Function kind
        assertEquals(SymbolKind.Function, findSymbol(children, "start-db").get().getKind());
    }

    @Test
    public void testHostsSection() {
        QDupDocument doc = new QDupDocument("test.yaml", FULL_YAML);
        List<DocumentSymbol> symbols = provider.documentSymbols(doc);

        DocumentSymbol hosts = findSymbol(symbols, "hosts").orElse(null);
        assertNotNull(hosts);
        assertEquals(SymbolKind.Namespace, hosts.getKind());

        List<DocumentSymbol> children = hosts.getChildren();
        assertNotNull(children);
        assertEquals(2, children.size());

        assertTrue("Should have 'local' host", findSymbol(children, "local").isPresent());
        assertTrue("Should have 'remote' host", findSymbol(children, "remote").isPresent());

        assertEquals(SymbolKind.Property, findSymbol(children, "local").get().getKind());
    }

    @Test
    public void testRolesSection() {
        QDupDocument doc = new QDupDocument("test.yaml", FULL_YAML);
        List<DocumentSymbol> symbols = provider.documentSymbols(doc);

        DocumentSymbol roles = findSymbol(symbols, "roles").orElse(null);
        assertNotNull(roles);
        assertEquals(SymbolKind.Namespace, roles.getKind());

        List<DocumentSymbol> roleChildren = roles.getChildren();
        assertNotNull(roleChildren);
        assertEquals(1, roleChildren.size());

        DocumentSymbol testRole = roleChildren.get(0);
        assertEquals("test-role", testRole.getName());
        assertEquals(SymbolKind.Class, testRole.getKind());

        // Role should have hosts and run-scripts as grandchildren
        List<DocumentSymbol> roleProps = testRole.getChildren();
        assertNotNull(roleProps);
        assertEquals(2, roleProps.size());

        assertTrue("Should have 'hosts' property", findSymbol(roleProps, "hosts").isPresent());
        assertTrue("Should have 'run-scripts' property", findSymbol(roleProps, "run-scripts").isPresent());
        assertEquals(SymbolKind.Property, findSymbol(roleProps, "hosts").get().getKind());
    }

    @Test
    public void testStatesSection() {
        QDupDocument doc = new QDupDocument("test.yaml", FULL_YAML);
        List<DocumentSymbol> symbols = provider.documentSymbols(doc);

        DocumentSymbol states = findSymbol(symbols, "states").orElse(null);
        assertNotNull(states);
        assertEquals(SymbolKind.Namespace, states.getKind());

        List<DocumentSymbol> children = states.getChildren();
        assertNotNull(children);
        assertEquals(2, children.size());

        assertTrue("Should have 'DB_PORT' state", findSymbol(children, "DB_PORT").isPresent());
        assertTrue("Should have 'APP_NAME' state", findSymbol(children, "APP_NAME").isPresent());

        assertEquals(SymbolKind.Variable, findSymbol(children, "DB_PORT").get().getKind());
    }

    @Test
    public void testSymbolRangesAreValid() {
        QDupDocument doc = new QDupDocument("test.yaml", FULL_YAML);
        List<DocumentSymbol> symbols = provider.documentSymbols(doc);

        for (DocumentSymbol sym : symbols) {
            assertNotNull("Range should not be null for " + sym.getName(), sym.getRange());
            assertNotNull("Selection range should not be null for " + sym.getName(), sym.getSelectionRange());
            assertTrue("Start line should be >= 0 for " + sym.getName(),
                    sym.getRange().getStart().getLine() >= 0);
        }
    }

    @Test
    public void testEmptyDocument() {
        QDupDocument doc = new QDupDocument("test.yaml", "");
        List<DocumentSymbol> symbols = provider.documentSymbols(doc);
        assertTrue("Empty document should have no symbols", symbols.isEmpty());
    }

    @Test
    public void testBrokenYaml() {
        QDupDocument doc = new QDupDocument("test.yaml", "scripts:\n  - sh: {\n");
        List<DocumentSymbol> symbols = provider.documentSymbols(doc);
        assertTrue("Broken YAML should return empty symbols", symbols.isEmpty());
    }

    private Optional<DocumentSymbol> findSymbol(List<DocumentSymbol> symbols, String name) {
        return symbols.stream().filter(s -> name.equals(s.getName())).findFirst();
    }
}
