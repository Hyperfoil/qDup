package io.hyperfoil.tools.qdup.lsp;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;

import java.util.*;

/**
 * Produces CompletionItems based on the cursor's YamlContext within a qDup document.
 */
public class CompletionProvider {

    private final CommandRegistry registry;
    private final CursorContextResolver contextResolver;
    private final Properties commandDocs;

    public CompletionProvider(CommandRegistry registry, CursorContextResolver contextResolver, Properties commandDocs) {
        this.registry = registry;
        this.contextResolver = contextResolver;
        this.commandDocs = commandDocs;
    }

    /**
     * Provides completion items for the given position in the document.
     */
    public List<CompletionItem> complete(QDupDocument document, int line, int character) {
        YamlContext context = contextResolver.resolve(document, line, character);
        return completeForContext(context, document, line);
    }

    /**
     * Provides completion items, with cross-file state variable support.
     * If the cursor is inside a ${{...}} pattern, suggests state variable names from all documents.
     */
    public List<CompletionItem> complete(QDupDocument document, int line, int character, Collection<QDupDocument> allDocs) {
        String currentLine = document.getLine(line);

        // Check if cursor is inside a ${{...}} pattern
        if (isInsideVariablePattern(currentLine, character)) {
            return completeStateVariables(document, allDocs);
        }

        return complete(document, line, character);
    }

    /**
     * Returns true if the cursor is inside a ${{...}} pattern on the given line.
     */
    static boolean isInsideVariablePattern(String line, int character) {
        return DefinitionProvider.extractVariableAt(line, character) != null
            || isAtEmptyVariablePattern(line, character);
    }

    /**
     * Checks if the cursor is at an empty ${{}} or ${{ with nothing typed yet.
     */
    private static boolean isAtEmptyVariablePattern(String line, int character) {
        if (line == null || character < 3) {
            return false;
        }
        // Search backward for "${{"
        for (int i = Math.min(character, line.length()) - 1; i >= 2; i--) {
            if (line.charAt(i) == '{' && line.charAt(i - 1) == '{' && line.charAt(i - 2) == '$') {
                // Found "${{"  — check we haven't passed a "}}" on the way
                return true;
            }
            if (i >= 1 && line.charAt(i) == '}' && line.charAt(i - 1) == '}') {
                return false;
            }
        }
        return false;
    }

    private List<CompletionItem> completeStateVariables(QDupDocument currentDoc, Collection<QDupDocument> allDocs) {
        Set<String> seen = new LinkedHashSet<>();
        List<CompletionItem> items = new ArrayList<>();

        // Collect from current document first
        addStateKeys(currentDoc, seen, items);

        // Then from other workspace documents
        if (allDocs != null) {
            for (QDupDocument other : allDocs) {
                if (!other.getUri().equals(currentDoc.getUri()) && other.isParseSuccessful()) {
                    addStateKeys(other, seen, items);
                }
            }
        }

        return items;
    }

    private void addStateKeys(QDupDocument doc, Set<String> seen, List<CompletionItem> items) {
        for (String key : doc.getStateKeys()) {
            if (seen.add(key)) {
                CompletionItem item = new CompletionItem(key);
                item.setKind(CompletionItemKind.Variable);
                item.setDetail("State variable");
                items.add(item);
            }
        }
    }

    /**
     * Produces completion items for the given context.
     */
    public List<CompletionItem> completeForContext(YamlContext context, QDupDocument document, int line) {
        List<CompletionItem> items = new ArrayList<>();

        switch (context) {
            case TOP_LEVEL_KEY:
                for (String key : registry.getTopLevelKeys()) {
                    items.add(createKeyCompletion(key, "Top-level qDup key", CompletionItemKind.Property));
                }
                break;

            case SCRIPT_COMMAND_KEY:
                for (String cmd : registry.getCommandNames()) {
                    String doc = commandDocs.getProperty(cmd, "qDup command");
                    CompletionItem item = new CompletionItem(cmd);
                    item.setKind(CompletionItemKind.Function);
                    item.setDetail(doc);
                    if (registry.isNoArgCommand(cmd)) {
                        item.setInsertText(cmd);
                    } else {
                        item.setInsertText(cmd + ": ");
                    }
                    items.add(item);
                }
                break;

            case COMMAND_MODIFIER_KEY:
                for (String mod : registry.getModifierKeys()) {
                    String doc = getModifierDoc(mod);
                    items.add(createKeyCompletion(mod, doc, CompletionItemKind.Keyword));
                }
                // Add "else" which is not in COMMAND_KEYS but is commonly used
                items.add(createKeyCompletion("else", "Commands to run when the parent command does not match", CompletionItemKind.Keyword));
                break;

            case COMMAND_PARAM_KEY:
                String commandName = contextResolver.findCommandAtLine(document, line);
                if (commandName != null) {
                    List<String> keys = registry.getExpectedKeys(commandName);
                    for (String key : keys) {
                        items.add(createKeyCompletion(key, "Parameter for " + commandName, CompletionItemKind.Property));
                    }
                }
                // Also suggest modifiers since they can appear at command level
                for (String mod : registry.getModifierKeys()) {
                    items.add(createKeyCompletion(mod, getModifierDoc(mod), CompletionItemKind.Keyword));
                }
                break;

            case HOST_CONFIG_KEY:
                for (String key : registry.getHostConfigKeys()) {
                    items.add(createKeyCompletion(key, "Host configuration key", CompletionItemKind.Property));
                }
                break;

            case ROLE_KEY:
                for (String key : registry.getRoleKeys()) {
                    items.add(createKeyCompletion(key, "Role configuration key", CompletionItemKind.Property));
                }
                break;

            case ROLE_SCRIPT_REF:
                Set<String> scriptNames = document.getScriptNames();
                for (String name : scriptNames) {
                    CompletionItem item = new CompletionItem(name);
                    item.setKind(CompletionItemKind.Reference);
                    item.setDetail("Script: " + name);
                    items.add(item);
                }
                break;

            case STATE_VARIABLE_REF:
                Set<String> stateKeys = document.getStateKeys();
                for (String key : stateKeys) {
                    CompletionItem item = new CompletionItem(key);
                    item.setKind(CompletionItemKind.Variable);
                    item.setDetail("State variable");
                    items.add(item);
                }
                break;

            default:
                break;
        }

        return items;
    }

    private CompletionItem createKeyCompletion(String key, String detail, CompletionItemKind kind) {
        CompletionItem item = new CompletionItem(key);
        item.setKind(kind);
        item.setDetail(detail);
        item.setInsertText(key + ": ");
        return item;
    }

    private String getModifierDoc(String modifier) {
        return switch (modifier) {
            case "then" -> "Commands to run when the parent command succeeds";
            case "with" -> "Set state variables for this command and its children";
            case "watch" -> "Commands to run concurrently while this command executes";
            case "timer" -> "Commands to run after a specified timeout";
            case "on-signal" -> "Commands to run when a specific signal is received";
            case "silent" -> "Suppress command output in the log";
            case "prefix" -> "Override the state variable prefix pattern";
            case "suffix" -> "Override the state variable suffix pattern";
            case "separator" -> "Override the state variable separator";
            case "js-prefix" -> "Override the JavaScript evaluation prefix";
            case "idle-timer" -> "Set or disable the idle timer for this command";
            case "state-scan" -> "Enable or disable state variable scanning";
            default -> "Command modifier";
        };
    }
}
