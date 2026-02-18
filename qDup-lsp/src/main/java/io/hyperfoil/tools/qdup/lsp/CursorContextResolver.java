package io.hyperfoil.tools.qdup.lsp;

import org.yaml.snakeyaml.nodes.*;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Determines the YamlContext for a given cursor position within a qDup YAML document.
 * Uses the SnakeYAML Node tree when available, with a line-based fallback.
 */
public class CursorContextResolver {

    private static final Logger LOG = Logger.getLogger(CursorContextResolver.class.getName());

    private final CommandRegistry registry;

    public CursorContextResolver(CommandRegistry registry) {
        this.registry = registry;
    }

    /**
     * Resolves the YAML context at the given line and character position.
     *
     * @param document the parsed document
     * @param line     0-based line number
     * @param character 0-based character offset
     * @return the resolved context
     */
    public YamlContext resolve(QDupDocument document, int line, int character) {
        if (document.isParseSuccessful() && document.getRootNode() != null) {
            return resolveFromTree(document, line, character);
        }
        return resolveFromLines(document, line, character);
    }

    /**
     * Resolves context by finding the path of keys in the YAML Node tree.
     */
    private YamlContext resolveFromTree(QDupDocument document, int line, int character) {
        Node root = document.getRootNode();
        if (!(root instanceof MappingNode)) {
            return YamlContext.UNKNOWN;
        }

        List<String> keyPath = new ArrayList<>();
        boolean isKeyPosition = determineKeyPath(root, line, character, keyPath);

        return contextFromKeyPath(keyPath, isKeyPosition, line, character, document);
    }

    /**
     * Walks the node tree to build the key path from root to the node containing the cursor.
     * Returns true if the cursor is at a key position, false if at a value position.
     */
    private boolean determineKeyPath(Node node, int line, int character, List<String> keyPath) {
        if (node instanceof MappingNode) {
            MappingNode mapping = (MappingNode) node;
            for (NodeTuple tuple : mapping.getValue()) {
                Node keyNode = tuple.getKeyNode();
                Node valueNode = tuple.getValueNode();

                // Check if cursor is on the key
                if (containsPosition(keyNode, line, character)) {
                    return true; // cursor is on a key
                }

                // Check if cursor is in the value
                if (containsPosition(valueNode, line, character)) {
                    String keyName = QDupDocument.scalarValue(keyNode);
                    if (keyName != null) {
                        keyPath.add(keyName);
                    }
                    return determineKeyPath(valueNode, line, character, keyPath);
                }

                // Check if cursor is between key and value on the same line
                if (keyNode.getStartMark() != null && keyNode.getStartMark().getLine() == line) {
                    String keyName = QDupDocument.scalarValue(keyNode);
                    if (keyName != null) {
                        int keyEnd = keyNode.getEndMark() != null ? keyNode.getEndMark().getColumn() : 0;
                        if (character > keyEnd) {
                            keyPath.add(keyName);
                            return false; // cursor is on value side
                        }
                    }
                }
            }
            // Cursor is inside the mapping but not on any specific tuple
            // This usually means a new key position
            return true;
        } else if (node instanceof SequenceNode) {
            SequenceNode sequence = (SequenceNode) node;
            for (Node item : sequence.getValue()) {
                if (containsPosition(item, line, character)) {
                    return determineKeyPath(item, line, character, keyPath);
                }
            }
            // Inside sequence but not matching any item - treating as command key context
            return true;
        }
        return false;
    }

    private boolean containsPosition(Node node, int line, int character) {
        if (node == null || node.getStartMark() == null || node.getEndMark() == null) {
            return false;
        }
        int startLine = node.getStartMark().getLine();
        int startCol = node.getStartMark().getColumn();
        int endLine = node.getEndMark().getLine();
        int endCol = node.getEndMark().getColumn();

        if (line < startLine || line > endLine) return false;
        if (line == startLine && character < startCol) return false;
        if (line == endLine && character > endCol) return false;
        return true;
    }

    /**
     * Determines context from the key path built during tree traversal.
     */
    private YamlContext contextFromKeyPath(List<String> keyPath, boolean isKeyPosition, int line, int character, QDupDocument document) {
        if (keyPath.isEmpty()) {
            return isKeyPosition ? YamlContext.TOP_LEVEL_KEY : YamlContext.TOP_LEVEL_VALUE;
        }

        String firstKey = keyPath.get(0);

        switch (firstKey) {
            case "scripts":
                if (keyPath.size() == 1) {
                    return isKeyPosition ? YamlContext.SCRIPT_NAME : YamlContext.SCRIPT_NAME;
                }
                if (keyPath.size() == 2) {
                    // Inside a specific script - this is the command list
                    return isKeyPosition ? YamlContext.SCRIPT_COMMAND_KEY : YamlContext.SCRIPT_COMMAND_VALUE;
                }
                if (keyPath.size() >= 3) {
                    String thirdKey = keyPath.get(2);
                    if (registry.isCommand(thirdKey)) {
                        if (keyPath.size() == 3 && !isKeyPosition) {
                            return YamlContext.SCRIPT_COMMAND_VALUE;
                        }
                        if (keyPath.size() >= 4 || isKeyPosition) {
                            return YamlContext.COMMAND_PARAM_KEY;
                        }
                    }
                    if (registry.getModifierKeys().contains(thirdKey) || "else".equals(thirdKey)) {
                        return isKeyPosition ? YamlContext.SCRIPT_COMMAND_KEY : YamlContext.SCRIPT_COMMAND_VALUE;
                    }
                    return isKeyPosition ? YamlContext.COMMAND_MODIFIER_KEY : YamlContext.SCRIPT_COMMAND_VALUE;
                }
                return YamlContext.SCRIPT_COMMAND_KEY;

            case "hosts":
                if (keyPath.size() == 1) {
                    return YamlContext.HOST_NAME;
                }
                return isKeyPosition ? YamlContext.HOST_CONFIG_KEY : YamlContext.UNKNOWN;

            case "roles":
                if (keyPath.size() == 1) {
                    return YamlContext.ROLE_NAME;
                }
                if (keyPath.size() == 2) {
                    return isKeyPosition ? YamlContext.ROLE_KEY : YamlContext.UNKNOWN;
                }
                if (keyPath.size() >= 3) {
                    String roleProp = keyPath.get(2);
                    if (roleProp.endsWith("-scripts")) {
                        return YamlContext.ROLE_SCRIPT_REF;
                    }
                    if ("hosts".equals(roleProp)) {
                        return YamlContext.ROLE_HOST_REF;
                    }
                }
                return YamlContext.ROLE_KEY;

            case "states":
            case "globals":
                return isKeyPosition ? YamlContext.STATE_VARIABLE_REF : YamlContext.UNKNOWN;

            case "name":
                return YamlContext.TOP_LEVEL_VALUE;

            default:
                return YamlContext.UNKNOWN;
        }
    }

    /**
     * Fallback line-based context resolution for when YAML parsing fails.
     */
    private YamlContext resolveFromLines(QDupDocument document, int line, int character) {
        String currentLine = document.getLine(line).stripTrailing();
        int indent = getIndent(currentLine);
        String trimmed = currentLine.trim();

        // Find the nearest parent section by looking upward
        String parentSection = findParentSection(document, line);

        if (indent == 0) {
            // Top-level
            if (trimmed.endsWith(":") || trimmed.isEmpty()) {
                return YamlContext.TOP_LEVEL_KEY;
            }
            return YamlContext.TOP_LEVEL_KEY;
        }

        if ("scripts".equals(parentSection)) {
            if (indent == 2) {
                return YamlContext.SCRIPT_NAME;
            }
            // Inside a script - check if it's a list item (command)
            if (trimmed.startsWith("- ")) {
                return YamlContext.SCRIPT_COMMAND_KEY;
            }
            // Check if it's a modifier
            String key = trimmed.split(":")[0].trim().replace("- ", "");
            if (registry.getModifierKeys().contains(key) || "else".equals(key)) {
                return YamlContext.COMMAND_MODIFIER_KEY;
            }
            if (registry.isCommand(key)) {
                return YamlContext.SCRIPT_COMMAND_KEY;
            }
            return YamlContext.SCRIPT_COMMAND_KEY;
        }

        if ("hosts".equals(parentSection)) {
            if (indent == 2) {
                return YamlContext.HOST_NAME;
            }
            return YamlContext.HOST_CONFIG_KEY;
        }

        if ("roles".equals(parentSection)) {
            if (indent == 2) {
                return YamlContext.ROLE_NAME;
            }
            if (indent == 4) {
                String key = trimmed.split(":")[0].trim();
                if (key.endsWith("-scripts")) {
                    return YamlContext.ROLE_SCRIPT_REF;
                }
                return YamlContext.ROLE_KEY;
            }
            // Inside a role property
            String roleKey = findNearestKey(document, line, 4);
            if (roleKey != null && roleKey.endsWith("-scripts")) {
                return YamlContext.ROLE_SCRIPT_REF;
            }
            return YamlContext.ROLE_KEY;
        }

        if ("states".equals(parentSection) || "globals".equals(parentSection)) {
            return YamlContext.STATE_VARIABLE_REF;
        }

        return YamlContext.UNKNOWN;
    }

    private String findParentSection(QDupDocument document, int line) {
        for (int i = line; i >= 0; i--) {
            String l = document.getLine(i);
            int indent = getIndent(l);
            if (indent == 0 && !l.trim().isEmpty()) {
                String key = l.trim().split(":")[0].trim();
                return key;
            }
        }
        return null;
    }

    private String findNearestKey(QDupDocument document, int line, int targetIndent) {
        for (int i = line; i >= 0; i--) {
            String l = document.getLine(i);
            int indent = getIndent(l);
            if (indent == targetIndent && l.contains(":")) {
                return l.trim().split(":")[0].trim();
            }
            if (indent < targetIndent) {
                break;
            }
        }
        return null;
    }

    /**
     * Returns the command name at the given line, if any.
     * Useful for determining which command's parameters to suggest.
     */
    public String findCommandAtLine(QDupDocument document, int line) {
        // Look at the current line and parent lines
        for (int i = line; i >= 0; i--) {
            String l = document.getLine(i).trim();
            if (l.startsWith("- ")) {
                l = l.substring(2);
            }
            String key = l.split(":")[0].trim();
            if (registry.isCommand(key)) {
                return key;
            }
            // If we've reached a lower indent level, stop looking
            int currentIndent = getIndent(document.getLine(i));
            int searchIndent = getIndent(document.getLine(line));
            if (currentIndent < searchIndent) {
                if (registry.isCommand(key)) {
                    return key;
                }
                break;
            }
        }
        return null;
    }

    private int getIndent(String line) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') count++;
            else break;
        }
        return count;
    }
}
