package io.hyperfoil.tools.qdup.lsp;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;

import java.util.Collection;

/**
 * Provides go-to-definition for qDup YAML documents.
 * Supports jumping from script/host references to their definitions.
 */
public class DefinitionProvider {

    private final CursorContextResolver contextResolver;

    public DefinitionProvider(CursorContextResolver contextResolver) {
        this.contextResolver = contextResolver;
    }

    /**
     * Returns the definition Location for the element at the given position, or null if none.
     */
    public Location definition(QDupDocument doc, int line, int character) {
        if (!doc.isParseSuccessful()) {
            return null;
        }

        String currentLine = doc.getLine(line);

        // Check for state variable pattern ${{...}}
        String varName = extractVariableAt(currentLine, character);
        if (varName != null) {
            Node stateNode = doc.findStateNode(varName);
            if (stateNode != null) {
                return nodeToLocation(doc.getUri(), stateNode);
            }
            return null;
        }

        String word = extractWordAt(currentLine, character);
        if (word == null || word.isEmpty()) {
            return null;
        }

        YamlContext context = contextResolver.resolve(doc, line, character);

        Node targetNode = null;

        switch (context) {
            case ROLE_SCRIPT_REF:
                targetNode = doc.findScriptNode(word);
                break;

            case SCRIPT_COMMAND_VALUE:
                // Check if the command key on this line is "script"
                String cmdName = contextResolver.findCommandAtLine(doc, line);
                if ("script".equals(cmdName)) {
                    targetNode = doc.findScriptNode(word);
                }
                break;

            case ROLE_HOST_REF:
                targetNode = doc.findHostNode(word);
                break;

            default:
                break;
        }

        if (targetNode == null) {
            return null;
        }

        return nodeToLocation(doc.getUri(), targetNode);
    }

    /**
     * Returns the definition Location searching across all workspace documents.
     * The current document is searched first; if not found, other documents are searched.
     */
    public Location definition(QDupDocument doc, int line, int character, Collection<QDupDocument> allDocs) {
        // Try current document first
        Location local = definition(doc, line, character);
        if (local != null) {
            return local;
        }

        if (allDocs == null || allDocs.isEmpty()) {
            return null;
        }

        if (!doc.isParseSuccessful()) {
            return null;
        }

        String currentLine = doc.getLine(line);

        // Check for state variable pattern ${{...}} across all docs
        String varName = extractVariableAt(currentLine, character);
        if (varName != null) {
            for (QDupDocument other : allDocs) {
                if (other.getUri().equals(doc.getUri()) || !other.isParseSuccessful()) {
                    continue;
                }
                Node stateNode = other.findStateNode(varName);
                if (stateNode != null) {
                    return nodeToLocation(other.getUri(), stateNode);
                }
            }
            return null;
        }

        String word = extractWordAt(currentLine, character);
        if (word == null || word.isEmpty()) {
            return null;
        }

        YamlContext context = contextResolver.resolve(doc, line, character);

        for (QDupDocument other : allDocs) {
            if (other.getUri().equals(doc.getUri()) || !other.isParseSuccessful()) {
                continue;
            }

            Node targetNode = null;

            switch (context) {
                case ROLE_SCRIPT_REF:
                    targetNode = other.findScriptNode(word);
                    break;

                case SCRIPT_COMMAND_VALUE:
                    String cmdName = contextResolver.findCommandAtLine(doc, line);
                    if ("script".equals(cmdName)) {
                        targetNode = other.findScriptNode(word);
                    }
                    break;

                case ROLE_HOST_REF:
                    targetNode = other.findHostNode(word);
                    break;

                default:
                    break;
            }

            if (targetNode != null) {
                return nodeToLocation(other.getUri(), targetNode);
            }
        }

        return null;
    }

    private Location nodeToLocation(String uri, Node node) {
        if (node.getStartMark() == null || node.getEndMark() == null) {
            return null;
        }
        int startLine = node.getStartMark().getLine();
        int startCol = node.getStartMark().getColumn();
        int endLine = node.getEndMark().getLine();
        int endCol = node.getEndMark().getColumn();

        Range range = new Range(
                new Position(startLine, startCol),
                new Position(endLine, endCol)
        );
        return new Location(uri, range);
    }

    /**
     * Extracts the state variable name if the cursor is inside a ${{...}} pattern.
     * Returns null if the cursor is not inside such a pattern.
     * Strips any default-value separator (e.g., "FOO:default" returns "FOO").
     */
    static String extractVariableAt(String line, int character) {
        if (line == null || character < 0 || character > line.length()) {
            return null;
        }

        // Search backward from cursor for "${{"
        int openIndex = -1;
        for (int i = Math.min(character, line.length()) - 1; i >= 2; i--) {
            if (line.charAt(i) == '{' && line.charAt(i - 1) == '{' && line.charAt(i - 2) == '$') {
                openIndex = i + 1; // position after "${{"
                break;
            }
            // If we hit "}}" while searching backward, we're outside a pattern
            if (i >= 1 && line.charAt(i) == '}' && line.charAt(i - 1) == '}') {
                return null;
            }
        }

        if (openIndex < 0) {
            return null;
        }

        // Search forward from the open for "}}" (or end of line)
        int closeIndex = line.length();
        for (int i = openIndex; i < line.length() - 1; i++) {
            if (line.charAt(i) == '}' && line.charAt(i + 1) == '}') {
                closeIndex = i;
                break;
            }
        }

        String varExpr = line.substring(openIndex, closeIndex).trim();
        if (varExpr.isEmpty()) {
            return null;
        }

        // Strip default-value separator (e.g., "FOO:default" -> "FOO")
        int colonIndex = varExpr.indexOf(':');
        if (colonIndex > 0) {
            varExpr = varExpr.substring(0, colonIndex);
        }

        return varExpr.trim();
    }

    /**
     * Extracts the word at the given character position in a line.
     */
    static String extractWordAt(String line, int character) {
        if (line == null || character < 0 || character > line.length()) {
            return null;
        }

        int start = character;
        int end = character;

        while (start > 0 && isWordChar(line.charAt(start - 1))) {
            start--;
        }
        while (end < line.length() && isWordChar(line.charAt(end))) {
            end++;
        }

        if (start == end) {
            return null;
        }

        return line.substring(start, end);
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '/' || c == '\\';
    }
}
