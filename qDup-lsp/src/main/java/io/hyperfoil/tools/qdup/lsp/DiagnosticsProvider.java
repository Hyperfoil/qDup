package io.hyperfoil.tools.qdup.lsp;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.yaml.snakeyaml.nodes.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates a qDup YAML document and produces LSP Diagnostics.
 */
public class DiagnosticsProvider {

    private final CommandRegistry registry;

    public DiagnosticsProvider(CommandRegistry registry) {
        this.registry = registry;
    }

    /**
     * Validates the document and returns a list of diagnostics.
     */
    public List<Diagnostic> diagnose(QDupDocument document) {
        return diagnose(document, Collections.emptyList());
    }

    /**
     * Validates the document and returns a list of diagnostics,
     * considering definitions and references from all workspace documents.
     */
    public List<Diagnostic> diagnose(QDupDocument document, Collection<QDupDocument> allDocs) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        if (!document.isParseSuccessful()) {
            diagnostics.add(createDiagnostic(
                0, 0, 0, 1,
                "YAML syntax error: unable to parse document",
                DiagnosticSeverity.Error
            ));
            return diagnostics;
        }

        Node root = document.getRootNode();
        if (!(root instanceof MappingNode)) {
            diagnostics.add(createDiagnostic(
                0, 0, 0, 1,
                "qDup document must be a YAML mapping at the top level",
                DiagnosticSeverity.Error
            ));
            return diagnostics;
        }

        MappingNode rootMapping = (MappingNode) root;
        validateTopLevelKeys(rootMapping, diagnostics);
        validateScripts(rootMapping, diagnostics);
        validateHosts(rootMapping, diagnostics);
        validateRoles(rootMapping, document, allDocs, diagnostics);
        checkUnusedScripts(document, allDocs, diagnostics);
        checkUnusedHosts(document, allDocs, diagnostics);

        return diagnostics;
    }

    private void validateTopLevelKeys(MappingNode root, List<Diagnostic> diagnostics) {
        Set<String> validKeys = registry.getTopLevelKeys();
        for (NodeTuple tuple : root.getValue()) {
            String key = QDupDocument.scalarValue(tuple.getKeyNode());
            if (key != null && !validKeys.contains(key)) {
                diagnostics.add(createDiagnosticFromNode(
                    tuple.getKeyNode(),
                    "Unknown top-level key: '" + key + "'. Valid keys: " + validKeys,
                    DiagnosticSeverity.Error
                ));
            }
        }
    }

    private void validateScripts(MappingNode root, List<Diagnostic> diagnostics) {
        for (NodeTuple tuple : root.getValue()) {
            String key = QDupDocument.scalarValue(tuple.getKeyNode());
            if ("scripts".equals(key) && tuple.getValueNode() instanceof MappingNode) {
                MappingNode scripts = (MappingNode) tuple.getValueNode();
                for (NodeTuple scriptTuple : scripts.getValue()) {
                    if (scriptTuple.getValueNode() instanceof SequenceNode) {
                        validateCommandSequence((SequenceNode) scriptTuple.getValueNode(), diagnostics);
                    }
                }
            }
        }
    }

    private void validateCommandSequence(SequenceNode sequence, List<Diagnostic> diagnostics) {
        for (Node item : sequence.getValue()) {
            if (item instanceof ScalarNode) {
                // Could be a no-arg command like "ctrlC" or "done"
                String value = ((ScalarNode) item).getValue();
                if (!registry.isNoArgCommand(value) && !registry.isCommand(value)) {
                    diagnostics.add(createDiagnosticFromNode(
                        item,
                        "Unknown command: '" + value + "'",
                        DiagnosticSeverity.Error
                    ));
                }
            } else if (item instanceof MappingNode) {
                validateCommandMapping((MappingNode) item, diagnostics);
            }
        }
    }

    private void validateCommandMapping(MappingNode commandNode, List<Diagnostic> diagnostics) {
        boolean foundCommand = false;
        for (NodeTuple tuple : commandNode.getValue()) {
            String key = QDupDocument.scalarValue(tuple.getKeyNode());
            if (key == null) continue;

            if (registry.isCommand(key)) {
                foundCommand = true;
            } else if (!registry.getModifierKeys().contains(key) && !"else".equals(key)) {
                diagnostics.add(createDiagnosticFromNode(
                    tuple.getKeyNode(),
                    "Unknown command or modifier: '" + key + "'",
                    DiagnosticSeverity.Error
                ));
            }

            // Validate nested command sequences in modifiers
            if ("then".equals(key) || "watch".equals(key) || "else".equals(key)) {
                if (tuple.getValueNode() instanceof SequenceNode) {
                    validateCommandSequence((SequenceNode) tuple.getValueNode(), diagnostics);
                }
            }
            if ("on-signal".equals(key) && tuple.getValueNode() instanceof MappingNode) {
                MappingNode signals = (MappingNode) tuple.getValueNode();
                for (NodeTuple signalTuple : signals.getValue()) {
                    if (signalTuple.getValueNode() instanceof SequenceNode) {
                        validateCommandSequence((SequenceNode) signalTuple.getValueNode(), diagnostics);
                    }
                }
            }
            if ("timer".equals(key) && tuple.getValueNode() instanceof MappingNode) {
                MappingNode timers = (MappingNode) tuple.getValueNode();
                for (NodeTuple timerTuple : timers.getValue()) {
                    if (timerTuple.getValueNode() instanceof SequenceNode) {
                        validateCommandSequence((SequenceNode) timerTuple.getValueNode(), diagnostics);
                    }
                }
            }
        }

        if (!foundCommand) {
            // Check if it's just modifiers without a command
            diagnostics.add(createDiagnosticFromNode(
                commandNode,
                "No recognized command found in mapping",
                DiagnosticSeverity.Warning
            ));
        }
    }

    private void validateHosts(MappingNode root, List<Diagnostic> diagnostics) {
        for (NodeTuple tuple : root.getValue()) {
            String key = QDupDocument.scalarValue(tuple.getKeyNode());
            if ("hosts".equals(key) && tuple.getValueNode() instanceof MappingNode) {
                MappingNode hosts = (MappingNode) tuple.getValueNode();
                for (NodeTuple hostTuple : hosts.getValue()) {
                    if (hostTuple.getValueNode() instanceof MappingNode) {
                        validateHostConfig((MappingNode) hostTuple.getValueNode(), diagnostics);
                    }
                    // Scalar values like "user@host:port" are valid too
                }
            }
        }
    }

    private void validateHostConfig(MappingNode hostNode, List<Diagnostic> diagnostics) {
        List<String> validKeys = registry.getHostConfigKeys();
        for (NodeTuple tuple : hostNode.getValue()) {
            String key = QDupDocument.scalarValue(tuple.getKeyNode());
            if (key != null && !validKeys.contains(key)) {
                diagnostics.add(createDiagnosticFromNode(
                    tuple.getKeyNode(),
                    "Unknown host configuration key: '" + key + "'",
                    DiagnosticSeverity.Error
                ));
            }
        }
    }

    private void validateRoles(MappingNode root, QDupDocument document, Collection<QDupDocument> allDocs, List<Diagnostic> diagnostics) {
        Set<String> definedScripts = new LinkedHashSet<>(document.getScriptNames());
        Set<String> definedHosts = new LinkedHashSet<>(document.getHostNames());
        for (QDupDocument other : allDocs) {
            if (!other.getUri().equals(document.getUri()) && other.isParseSuccessful()) {
                definedScripts.addAll(other.getScriptNames());
                definedHosts.addAll(other.getHostNames());
            }
        }

        for (NodeTuple tuple : root.getValue()) {
            String key = QDupDocument.scalarValue(tuple.getKeyNode());
            if ("roles".equals(key) && tuple.getValueNode() instanceof MappingNode) {
                MappingNode roles = (MappingNode) tuple.getValueNode();
                for (NodeTuple roleTuple : roles.getValue()) {
                    if (roleTuple.getValueNode() instanceof MappingNode) {
                        MappingNode role = (MappingNode) roleTuple.getValueNode();
                        validateRoleKeys(role, diagnostics);
                        validateRoleReferences(role, definedScripts, definedHosts, diagnostics);
                    }
                }
            }
        }
    }

    private void validateRoleKeys(MappingNode role, List<Diagnostic> diagnostics) {
        Set<String> validKeys = registry.getRoleKeys();
        for (NodeTuple tuple : role.getValue()) {
            String key = QDupDocument.scalarValue(tuple.getKeyNode());
            if (key != null && !validKeys.contains(key)) {
                diagnostics.add(createDiagnosticFromNode(
                    tuple.getKeyNode(),
                    "Unknown role key: '" + key + "'. Valid keys: " + validKeys,
                    DiagnosticSeverity.Error
                ));
            }
        }
    }

    private void validateRoleReferences(MappingNode role, Set<String> definedScripts, Set<String> definedHosts, List<Diagnostic> diagnostics) {
        for (NodeTuple tuple : role.getValue()) {
            String key = QDupDocument.scalarValue(tuple.getKeyNode());
            if (key == null) continue;

            if (key.endsWith("-scripts")) {
                validateReferences(tuple.getValueNode(), definedScripts, "script", diagnostics);
            } else if ("hosts".equals(key)) {
                validateReferences(tuple.getValueNode(), definedHosts, "host", diagnostics);
            }
        }
    }

    private void validateReferences(Node valueNode, Set<String> defined, String kind, List<Diagnostic> diagnostics) {
        if (valueNode instanceof SequenceNode) {
            for (Node item : ((SequenceNode) valueNode).getValue()) {
                String name = QDupDocument.scalarValue(item);
                if (name != null && !defined.contains(name)) {
                    diagnostics.add(createDiagnosticFromNode(
                        item,
                        "Undefined " + kind + " reference: '" + name + "'",
                        DiagnosticSeverity.Warning
                    ));
                }
            }
        } else if (valueNode instanceof ScalarNode) {
            String name = QDupDocument.scalarValue(valueNode);
            if (name != null && !defined.contains(name)) {
                diagnostics.add(createDiagnosticFromNode(
                    valueNode,
                    "Undefined " + kind + " reference: '" + name + "'",
                    DiagnosticSeverity.Warning
                ));
            }
        }
    }

    private void checkUnusedScripts(QDupDocument document, Collection<QDupDocument> allDocs, List<Diagnostic> diagnostics) {
        Set<String> defined = document.getScriptNames();
        Set<String> referenced = new LinkedHashSet<>(document.getReferencedScripts());
        for (QDupDocument other : allDocs) {
            if (!other.getUri().equals(document.getUri()) && other.isParseSuccessful()) {
                referenced.addAll(other.getReferencedScripts());
            }
        }
        for (String script : defined) {
            if (!referenced.contains(script)) {
                // Find the node for this script name to get position
                if (document.getRootNode() instanceof MappingNode) {
                    MappingNode root = (MappingNode) document.getRootNode();
                    for (NodeTuple tuple : root.getValue()) {
                        String key = QDupDocument.scalarValue(tuple.getKeyNode());
                        if ("scripts".equals(key) && tuple.getValueNode() instanceof MappingNode) {
                            MappingNode scripts = (MappingNode) tuple.getValueNode();
                            for (NodeTuple scriptTuple : scripts.getValue()) {
                                String name = QDupDocument.scalarValue(scriptTuple.getKeyNode());
                                if (script.equals(name)) {
                                    diagnostics.add(createDiagnosticFromNode(
                                        scriptTuple.getKeyNode(),
                                        "Script '" + script + "' is defined but not referenced in any role",
                                        DiagnosticSeverity.Information
                                    ));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void checkUnusedHosts(QDupDocument document, Collection<QDupDocument> allDocs, List<Diagnostic> diagnostics) {
        Set<String> defined = document.getHostNames();
        Set<String> referenced = new LinkedHashSet<>(document.getReferencedHosts());
        for (QDupDocument other : allDocs) {
            if (!other.getUri().equals(document.getUri()) && other.isParseSuccessful()) {
                referenced.addAll(other.getReferencedHosts());
            }
        }
        for (String host : defined) {
            if (!referenced.contains(host)) {
                if (document.getRootNode() instanceof MappingNode) {
                    MappingNode root = (MappingNode) document.getRootNode();
                    for (NodeTuple tuple : root.getValue()) {
                        String key = QDupDocument.scalarValue(tuple.getKeyNode());
                        if ("hosts".equals(key) && tuple.getValueNode() instanceof MappingNode) {
                            MappingNode hosts = (MappingNode) tuple.getValueNode();
                            for (NodeTuple hostTuple : hosts.getValue()) {
                                String name = QDupDocument.scalarValue(hostTuple.getKeyNode());
                                if (host.equals(name)) {
                                    diagnostics.add(createDiagnosticFromNode(
                                        hostTuple.getKeyNode(),
                                        "Host '" + host + "' is defined but not referenced in any role",
                                        DiagnosticSeverity.Information
                                    ));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private Diagnostic createDiagnostic(int startLine, int startChar, int endLine, int endChar, String message, DiagnosticSeverity severity) {
        Diagnostic diag = new Diagnostic();
        diag.setRange(new Range(new Position(startLine, startChar), new Position(endLine, endChar)));
        diag.setMessage(message);
        diag.setSeverity(severity);
        diag.setSource("qDup");
        return diag;
    }

    private Diagnostic createDiagnosticFromNode(Node node, String message, DiagnosticSeverity severity) {
        int startLine = node.getStartMark() != null ? node.getStartMark().getLine() : 0;
        int startCol = node.getStartMark() != null ? node.getStartMark().getColumn() : 0;
        int endLine = node.getEndMark() != null ? node.getEndMark().getLine() : startLine;
        int endCol = node.getEndMark() != null ? node.getEndMark().getColumn() : startCol + 1;
        return createDiagnostic(startLine, startCol, endLine, endCol, message, severity);
    }
}
