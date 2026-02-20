package io.hyperfoil.tools.qdup.lsp;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.*;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents a parsed qDup YAML document.
 * Maintains both the raw text and the SnakeYAML Node tree for structural analysis.
 */
public class QDupDocument {

    private static final Logger LOG = Logger.getLogger(QDupDocument.class.getName());

    private String uri;
    private String text;
    private String[] lines;
    private Node rootNode;
    private boolean parseSuccessful;

    public QDupDocument(String uri, String text) {
        this.uri = uri;
        setText(text);
    }

    public void setText(String text) {
        this.text = text;
        this.lines = text.split("\n", -1);
        parse();
    }

    private void parse() {
        try {
            LoaderOptions options = new LoaderOptions();
            options.setProcessComments(false);
            Yaml yaml = new Yaml(options);
            this.rootNode = yaml.compose(new java.io.StringReader(text));
            this.parseSuccessful = (rootNode != null);
        } catch (Exception e) {
            LOG.log(Level.FINE, "YAML parse failed for " + uri, e);
            this.rootNode = null;
            this.parseSuccessful = false;
        }
    }

    public String getUri() {
        return uri;
    }

    public String getText() {
        return text;
    }

    public String[] getLines() {
        return lines;
    }

    public String getLine(int lineIndex) {
        if (lineIndex >= 0 && lineIndex < lines.length) {
            return lines[lineIndex];
        }
        return "";
    }

    public Node getRootNode() {
        return rootNode;
    }

    public boolean isParseSuccessful() {
        return parseSuccessful;
    }

    /**
     * Extracts script names defined under the "scripts:" top-level key.
     */
    public Set<String> getScriptNames() {
        Set<String> names = new LinkedHashSet<>();
        if (rootNode instanceof MappingNode) {
            MappingNode root = (MappingNode) rootNode;
            for (NodeTuple tuple : root.getValue()) {
                String key = scalarValue(tuple.getKeyNode());
                if ("scripts".equals(key) && tuple.getValueNode() instanceof MappingNode) {
                    MappingNode scripts = (MappingNode) tuple.getValueNode();
                    for (NodeTuple scriptTuple : scripts.getValue()) {
                        String scriptName = scalarValue(scriptTuple.getKeyNode());
                        if (scriptName != null) {
                            names.add(scriptName);
                        }
                    }
                }
            }
        }
        return names;
    }

    /**
     * Extracts host names defined under the "hosts:" top-level key.
     */
    public Set<String> getHostNames() {
        Set<String> names = new LinkedHashSet<>();
        if (rootNode instanceof MappingNode) {
            MappingNode root = (MappingNode) rootNode;
            for (NodeTuple tuple : root.getValue()) {
                String key = scalarValue(tuple.getKeyNode());
                if ("hosts".equals(key) && tuple.getValueNode() instanceof MappingNode) {
                    MappingNode hosts = (MappingNode) tuple.getValueNode();
                    for (NodeTuple hostTuple : hosts.getValue()) {
                        String hostName = scalarValue(hostTuple.getKeyNode());
                        if (hostName != null) {
                            names.add(hostName);
                        }
                    }
                }
            }
        }
        return names;
    }

    /**
     * Extracts state keys defined under the "states:" top-level key.
     */
    public Set<String> getStateKeys() {
        Set<String> keys = new LinkedHashSet<>();
        if (rootNode instanceof MappingNode) {
            MappingNode root = (MappingNode) rootNode;
            for (NodeTuple tuple : root.getValue()) {
                String key = scalarValue(tuple.getKeyNode());
                if ("states".equals(key) && tuple.getValueNode() instanceof MappingNode) {
                    collectKeys((MappingNode) tuple.getValueNode(), "", keys);
                }
            }
        }
        return keys;
    }

    private void collectKeys(MappingNode node, String prefix, Set<String> keys) {
        for (NodeTuple tuple : node.getValue()) {
            String key = scalarValue(tuple.getKeyNode());
            if (key != null) {
                String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
                keys.add(fullKey);
                keys.add(key);
            }
        }
    }

    /**
     * Extracts host references from roles.
     */
    public Set<String> getReferencedHosts() {
        Set<String> refs = new LinkedHashSet<>();
        if (rootNode instanceof MappingNode) {
            MappingNode root = (MappingNode) rootNode;
            for (NodeTuple tuple : root.getValue()) {
                String key = scalarValue(tuple.getKeyNode());
                if ("roles".equals(key) && tuple.getValueNode() instanceof MappingNode) {
                    MappingNode roles = (MappingNode) tuple.getValueNode();
                    for (NodeTuple roleTuple : roles.getValue()) {
                        if (roleTuple.getValueNode() instanceof MappingNode) {
                            MappingNode role = (MappingNode) roleTuple.getValueNode();
                            for (NodeTuple roleProp : role.getValue()) {
                                String propKey = scalarValue(roleProp.getKeyNode());
                                if ("hosts".equals(propKey)) {
                                    collectSequenceValues(roleProp.getValueNode(), refs);
                                }
                            }
                        }
                    }
                }
            }
        }
        return refs;
    }

    /**
     * Extracts script references from roles.
     */
    public Set<String> getReferencedScripts() {
        Set<String> refs = new LinkedHashSet<>();
        if (rootNode instanceof MappingNode) {
            MappingNode root = (MappingNode) rootNode;
            for (NodeTuple tuple : root.getValue()) {
                String key = scalarValue(tuple.getKeyNode());
                if ("roles".equals(key) && tuple.getValueNode() instanceof MappingNode) {
                    MappingNode roles = (MappingNode) tuple.getValueNode();
                    for (NodeTuple roleTuple : roles.getValue()) {
                        if (roleTuple.getValueNode() instanceof MappingNode) {
                            MappingNode role = (MappingNode) roleTuple.getValueNode();
                            for (NodeTuple roleProp : role.getValue()) {
                                String propKey = scalarValue(roleProp.getKeyNode());
                                if (propKey != null && propKey.endsWith("-scripts")) {
                                    collectSequenceValues(roleProp.getValueNode(), refs);
                                }
                            }
                        }
                    }
                }
            }
        }
        return refs;
    }

    private void collectSequenceValues(Node node, Set<String> values) {
        if (node instanceof SequenceNode) {
            for (Node item : ((SequenceNode) node).getValue()) {
                String val = scalarValue(item);
                if (val != null) {
                    values.add(val);
                }
            }
        } else if (node instanceof ScalarNode) {
            String val = scalarValue(node);
            if (val != null) {
                values.add(val);
            }
        }
    }

    /**
     * Finds the key Node for the named script under the "scripts:" top-level key.
     * Returns null if not found.
     */
    public Node findScriptNode(String name) {
        return findTopLevelEntryKeyNode("scripts", name);
    }

    /**
     * Finds the key Node for the named host under the "hosts:" top-level key.
     * Returns null if not found.
     */
    public Node findHostNode(String name) {
        return findTopLevelEntryKeyNode("hosts", name);
    }

    /**
     * Finds the key Node for the named state variable under "states:" or "globals:".
     * Searches flat keys first, then nested keys (e.g., "server.FOO").
     * Returns null if not found.
     */
    public Node findStateNode(String name) {
        if (name == null || rootNode == null || !(rootNode instanceof MappingNode)) {
            return null;
        }
        MappingNode root = (MappingNode) rootNode;
        for (NodeTuple tuple : root.getValue()) {
            String key = scalarValue(tuple.getKeyNode());
            if (("states".equals(key) || "globals".equals(key)) && tuple.getValueNode() instanceof MappingNode) {
                MappingNode section = (MappingNode) tuple.getValueNode();
                // Search flat keys first
                for (NodeTuple entryTuple : section.getValue()) {
                    String entryName = scalarValue(entryTuple.getKeyNode());
                    if (name.equals(entryName)) {
                        return entryTuple.getKeyNode();
                    }
                }
                // Search nested keys (e.g., name "server.FOO" -> parent "server", child "FOO")
                int dotIndex = name.indexOf('.');
                if (dotIndex > 0) {
                    String parent = name.substring(0, dotIndex);
                    String child = name.substring(dotIndex + 1);
                    for (NodeTuple entryTuple : section.getValue()) {
                        String entryName = scalarValue(entryTuple.getKeyNode());
                        if (parent.equals(entryName) && entryTuple.getValueNode() instanceof MappingNode) {
                            MappingNode nested = (MappingNode) entryTuple.getValueNode();
                            for (NodeTuple nestedTuple : nested.getValue()) {
                                String nestedName = scalarValue(nestedTuple.getKeyNode());
                                if (child.equals(nestedName)) {
                                    return nestedTuple.getKeyNode();
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns the scalar value of a state variable, or null if not found or not scalar.
     */
    public String getStateValue(String name) {
        if (name == null || rootNode == null || !(rootNode instanceof MappingNode)) {
            return null;
        }
        MappingNode root = (MappingNode) rootNode;
        for (NodeTuple tuple : root.getValue()) {
            String key = scalarValue(tuple.getKeyNode());
            if (("states".equals(key) || "globals".equals(key)) && tuple.getValueNode() instanceof MappingNode) {
                MappingNode section = (MappingNode) tuple.getValueNode();
                for (NodeTuple entryTuple : section.getValue()) {
                    String entryName = scalarValue(entryTuple.getKeyNode());
                    if (name.equals(entryName)) {
                        return scalarValue(entryTuple.getValueNode());
                    }
                }
                // Search nested keys
                int dotIndex = name.indexOf('.');
                if (dotIndex > 0) {
                    String parent = name.substring(0, dotIndex);
                    String child = name.substring(dotIndex + 1);
                    for (NodeTuple entryTuple : section.getValue()) {
                        String entryName = scalarValue(entryTuple.getKeyNode());
                        if (parent.equals(entryName) && entryTuple.getValueNode() instanceof MappingNode) {
                            MappingNode nested = (MappingNode) entryTuple.getValueNode();
                            for (NodeTuple nestedTuple : nested.getValue()) {
                                String nestedName = scalarValue(nestedTuple.getKeyNode());
                                if (child.equals(nestedName)) {
                                    return scalarValue(nestedTuple.getValueNode());
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Finds the key Node for a named entry under a given top-level section.
     */
    private Node findTopLevelEntryKeyNode(String section, String name) {
        if (name == null || rootNode == null || !(rootNode instanceof MappingNode)) {
            return null;
        }
        MappingNode root = (MappingNode) rootNode;
        for (NodeTuple tuple : root.getValue()) {
            String key = scalarValue(tuple.getKeyNode());
            if (section.equals(key) && tuple.getValueNode() instanceof MappingNode) {
                MappingNode sectionNode = (MappingNode) tuple.getValueNode();
                for (NodeTuple entryTuple : sectionNode.getValue()) {
                    String entryName = scalarValue(entryTuple.getKeyNode());
                    if (name.equals(entryName)) {
                        return entryTuple.getKeyNode();
                    }
                }
            }
        }
        return null;
    }

    static String scalarValue(Node node) {
        if (node instanceof ScalarNode) {
            return ((ScalarNode) node).getValue();
        }
        return null;
    }
}
