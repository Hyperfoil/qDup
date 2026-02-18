package io.hyperfoil.tools.qdup.lsp;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class DiagnosticsProviderTest {

    private CommandRegistry registry;
    private DiagnosticsProvider provider;

    @Before
    public void setUp() {
        registry = new CommandRegistry();
        provider = new DiagnosticsProvider(registry);
    }

    @Test
    public void testValidDocument() {
        String yaml = String.join("\n",
            "name: test",
            "scripts:",
            "  myScript:",
            "  - sh: echo hello",
            "  - log: done",
            "hosts:",
            "  myHost: user@host:22",
            "roles:",
            "  myRole:",
            "    hosts:",
            "    - myHost",
            "    run-scripts:",
            "    - myScript",
            ""
        );
        QDupDocument doc = new QDupDocument("test.yaml", yaml);
        List<Diagnostic> diags = provider.diagnose(doc);

        // A valid document should have no errors
        List<Diagnostic> errors = diags.stream()
            .filter(d -> d.getSeverity() == DiagnosticSeverity.Error)
            .collect(Collectors.toList());
        assertTrue("Valid document should have no errors, but got: " +
            errors.stream().map(Diagnostic::getMessage).collect(Collectors.joining(", ")),
            errors.isEmpty());
    }

    @Test
    public void testUnknownTopLevelKey() {
        String yaml = "name: test\ninvalidKey: something\nscripts:\n  myScript:\n  - sh: echo\n";
        QDupDocument doc = new QDupDocument("test.yaml", yaml);
        List<Diagnostic> diags = provider.diagnose(doc);

        boolean hasUnknownKey = diags.stream()
            .anyMatch(d -> d.getMessage().contains("Unknown top-level key") && d.getMessage().contains("invalidKey"));
        assertTrue("Should detect unknown top-level key 'invalidKey'", hasUnknownKey);
    }

    @Test
    public void testUnknownCommand() {
        String yaml = "scripts:\n  myScript:\n  - invalidCmd: something\n";
        QDupDocument doc = new QDupDocument("test.yaml", yaml);
        List<Diagnostic> diags = provider.diagnose(doc);

        boolean hasUnknownCmd = diags.stream()
            .anyMatch(d -> d.getMessage().contains("Unknown command") && d.getMessage().contains("invalidCmd"));
        assertTrue("Should detect unknown command 'invalidCmd'", hasUnknownCmd);
    }

    @Test
    public void testUnknownHostKey() {
        String yaml = "hosts:\n  myHost:\n    hostname: example.com\n    invalidKey: something\n";
        QDupDocument doc = new QDupDocument("test.yaml", yaml);
        List<Diagnostic> diags = provider.diagnose(doc);

        boolean hasUnknownHostKey = diags.stream()
            .anyMatch(d -> d.getMessage().contains("Unknown host configuration key") && d.getMessage().contains("invalidKey"));
        assertTrue("Should detect unknown host config key", hasUnknownHostKey);
    }

    @Test
    public void testUnknownRoleKey() {
        String yaml = "roles:\n  myRole:\n    invalidKey: something\n";
        QDupDocument doc = new QDupDocument("test.yaml", yaml);
        List<Diagnostic> diags = provider.diagnose(doc);

        boolean hasUnknownRoleKey = diags.stream()
            .anyMatch(d -> d.getMessage().contains("Unknown role key") && d.getMessage().contains("invalidKey"));
        assertTrue("Should detect unknown role key", hasUnknownRoleKey);
    }

    @Test
    public void testUndefinedScriptReference() {
        String yaml = String.join("\n",
            "scripts:",
            "  myScript:",
            "  - sh: echo hello",
            "roles:",
            "  myRole:",
            "    hosts:",
            "    - someHost",
            "    run-scripts:",
            "    - nonExistentScript",
            ""
        );
        QDupDocument doc = new QDupDocument("test.yaml", yaml);
        List<Diagnostic> diags = provider.diagnose(doc);

        boolean hasUndefinedScript = diags.stream()
            .anyMatch(d -> d.getMessage().contains("Undefined script reference") && d.getMessage().contains("nonExistentScript"));
        assertTrue("Should detect undefined script reference", hasUndefinedScript);
    }

    @Test
    public void testUndefinedHostReference() {
        String yaml = String.join("\n",
            "scripts:",
            "  myScript:",
            "  - sh: echo hello",
            "hosts:",
            "  realHost: user@host:22",
            "roles:",
            "  myRole:",
            "    hosts:",
            "    - fakeHost",
            "    run-scripts:",
            "    - myScript",
            ""
        );
        QDupDocument doc = new QDupDocument("test.yaml", yaml);
        List<Diagnostic> diags = provider.diagnose(doc);

        boolean hasUndefinedHost = diags.stream()
            .anyMatch(d -> d.getMessage().contains("Undefined host reference") && d.getMessage().contains("fakeHost"));
        assertTrue("Should detect undefined host reference", hasUndefinedHost);
    }

    @Test
    public void testUnusedScript() {
        String yaml = String.join("\n",
            "scripts:",
            "  usedScript:",
            "  - sh: echo used",
            "  unusedScript:",
            "  - sh: echo unused",
            "hosts:",
            "  myHost: user@host:22",
            "roles:",
            "  myRole:",
            "    hosts:",
            "    - myHost",
            "    run-scripts:",
            "    - usedScript",
            ""
        );
        QDupDocument doc = new QDupDocument("test.yaml", yaml);
        List<Diagnostic> diags = provider.diagnose(doc);

        boolean hasUnused = diags.stream()
            .anyMatch(d -> d.getSeverity() == DiagnosticSeverity.Information
                && d.getMessage().contains("unusedScript")
                && d.getMessage().contains("not referenced"));
        assertTrue("Should detect unused script", hasUnused);
    }

    @Test
    public void testUnusedHost() {
        String yaml = String.join("\n",
            "scripts:",
            "  myScript:",
            "  - sh: echo hello",
            "hosts:",
            "  usedHost: user@host:22",
            "  unusedHost: user@other:22",
            "roles:",
            "  myRole:",
            "    hosts:",
            "    - usedHost",
            "    run-scripts:",
            "    - myScript",
            ""
        );
        QDupDocument doc = new QDupDocument("test.yaml", yaml);
        List<Diagnostic> diags = provider.diagnose(doc);

        boolean hasUnused = diags.stream()
            .anyMatch(d -> d.getSeverity() == DiagnosticSeverity.Information
                && d.getMessage().contains("unusedHost")
                && d.getMessage().contains("not referenced"));
        assertTrue("Should detect unused host", hasUnused);
    }

    @Test
    public void testBrokenYamlDiagnostic() {
        String yaml = "scripts:\n  myScript:\n  - sh: {\n";
        QDupDocument doc = new QDupDocument("test.yaml", yaml);
        List<Diagnostic> diags = provider.diagnose(doc);

        if (!doc.isParseSuccessful()) {
            boolean hasSyntaxError = diags.stream()
                .anyMatch(d -> d.getSeverity() == DiagnosticSeverity.Error
                    && d.getMessage().contains("YAML syntax error"));
            assertTrue("Should report YAML syntax error", hasSyntaxError);
        }
    }

    @Test
    public void testValidCommandsNotFlagged() {
        String yaml = String.join("\n",
            "scripts:",
            "  myScript:",
            "  - sh: echo hello",
            "  - regex: pattern",
            "    then:",
            "    - log: matched",
            "  - set-state: myVar",
            "  - sleep: 1s",
            ""
        );
        QDupDocument doc = new QDupDocument("test.yaml", yaml);
        List<Diagnostic> diags = provider.diagnose(doc);

        List<Diagnostic> errors = diags.stream()
            .filter(d -> d.getSeverity() == DiagnosticSeverity.Error)
            .collect(Collectors.toList());
        assertTrue("Valid commands should not produce errors, but got: " +
            errors.stream().map(Diagnostic::getMessage).collect(Collectors.joining(", ")),
            errors.isEmpty());
    }

    @Test
    public void testNoArgCommandAsScalar() {
        String yaml = "scripts:\n  myScript:\n  - ctrlC\n  - done\n  - echo\n";
        QDupDocument doc = new QDupDocument("test.yaml", yaml);
        List<Diagnostic> diags = provider.diagnose(doc);

        List<Diagnostic> errors = diags.stream()
            .filter(d -> d.getSeverity() == DiagnosticSeverity.Error)
            .collect(Collectors.toList());
        assertTrue("No-arg commands as scalars should not produce errors, got: " +
            errors.stream().map(Diagnostic::getMessage).collect(Collectors.joining(", ")),
            errors.isEmpty());
    }
}
