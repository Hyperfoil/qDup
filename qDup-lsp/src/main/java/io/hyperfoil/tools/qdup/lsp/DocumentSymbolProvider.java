package io.hyperfoil.tools.qdup.lsp;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.yaml.snakeyaml.nodes.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provides document symbols (outline) for qDup YAML documents.
 * Builds a hierarchical symbol tree with sections, scripts, hosts, roles, and states.
 */
public class DocumentSymbolProvider {

    /**
     * Returns a hierarchical list of DocumentSymbols for the given document.
     */
    public List<DocumentSymbol> documentSymbols(QDupDocument doc) {
        if (!doc.isParseSuccessful() || !(doc.getRootNode() instanceof MappingNode)) {
            return Collections.emptyList();
        }

        MappingNode root = (MappingNode) doc.getRootNode();
        List<DocumentSymbol> symbols = new ArrayList<>();

        for (NodeTuple tuple : root.getValue()) {
            String key = QDupDocument.scalarValue(tuple.getKeyNode());
            if (key == null) {
                continue;
            }

            Node keyNode = tuple.getKeyNode();
            Node valueNode = tuple.getValueNode();
            Range range = nodeRange(keyNode, valueNode);
            Range selectionRange = nodeRange(keyNode);

            switch (key) {
                case "scripts":
                    symbols.add(buildSectionSymbol(key, SymbolKind.Namespace, range, selectionRange,
                            valueNode, SymbolKind.Function));
                    break;

                case "hosts":
                    symbols.add(buildSectionSymbol(key, SymbolKind.Namespace, range, selectionRange,
                            valueNode, SymbolKind.Property));
                    break;

                case "roles":
                    symbols.add(buildRolesSymbol(key, range, selectionRange, valueNode));
                    break;

                case "states":
                case "globals":
                    symbols.add(buildSectionSymbol(key, SymbolKind.Namespace, range, selectionRange,
                            valueNode, SymbolKind.Variable));
                    break;

                default:
                    // Top-level keys like "name"
                    DocumentSymbol sym = new DocumentSymbol(key, SymbolKind.Property, range, selectionRange);
                    symbols.add(sym);
                    break;
            }
        }

        return symbols;
    }

    /**
     * Builds a section symbol with children from a MappingNode.
     */
    private DocumentSymbol buildSectionSymbol(String name, SymbolKind sectionKind,
                                               Range range, Range selectionRange,
                                               Node valueNode, SymbolKind childKind) {
        DocumentSymbol section = new DocumentSymbol(name, sectionKind, range, selectionRange);
        List<DocumentSymbol> children = new ArrayList<>();

        if (valueNode instanceof MappingNode) {
            MappingNode mapping = (MappingNode) valueNode;
            for (NodeTuple entry : mapping.getValue()) {
                String entryName = QDupDocument.scalarValue(entry.getKeyNode());
                if (entryName != null) {
                    Range entryRange = nodeRange(entry.getKeyNode(), entry.getValueNode());
                    Range entrySelRange = nodeRange(entry.getKeyNode());
                    children.add(new DocumentSymbol(entryName, childKind, entryRange, entrySelRange));
                }
            }
        }

        section.setChildren(children);
        return section;
    }

    /**
     * Builds the roles section symbol with role entries and their properties as grandchildren.
     */
    private DocumentSymbol buildRolesSymbol(String name, Range range, Range selectionRange, Node valueNode) {
        DocumentSymbol section = new DocumentSymbol(name, SymbolKind.Namespace, range, selectionRange);
        List<DocumentSymbol> roleChildren = new ArrayList<>();

        if (valueNode instanceof MappingNode) {
            MappingNode rolesMapping = (MappingNode) valueNode;
            for (NodeTuple roleTuple : rolesMapping.getValue()) {
                String roleName = QDupDocument.scalarValue(roleTuple.getKeyNode());
                if (roleName == null) {
                    continue;
                }

                Range roleRange = nodeRange(roleTuple.getKeyNode(), roleTuple.getValueNode());
                Range roleSelRange = nodeRange(roleTuple.getKeyNode());
                DocumentSymbol roleSym = new DocumentSymbol(roleName, SymbolKind.Class, roleRange, roleSelRange);

                // Add role properties as grandchildren
                List<DocumentSymbol> propChildren = new ArrayList<>();
                if (roleTuple.getValueNode() instanceof MappingNode) {
                    MappingNode roleMapping = (MappingNode) roleTuple.getValueNode();
                    for (NodeTuple propTuple : roleMapping.getValue()) {
                        String propName = QDupDocument.scalarValue(propTuple.getKeyNode());
                        if (propName != null) {
                            Range propRange = nodeRange(propTuple.getKeyNode(), propTuple.getValueNode());
                            Range propSelRange = nodeRange(propTuple.getKeyNode());
                            propChildren.add(new DocumentSymbol(propName, SymbolKind.Property, propRange, propSelRange));
                        }
                    }
                }
                roleSym.setChildren(propChildren);
                roleChildren.add(roleSym);
            }
        }

        section.setChildren(roleChildren);
        return section;
    }

    /**
     * Creates a Range spanning from keyNode start to valueNode end.
     */
    private Range nodeRange(Node keyNode, Node valueNode) {
        Position start = markToPosition(keyNode.getStartMark());
        Position end = markToPosition(valueNode.getEndMark());
        return new Range(start, end);
    }

    /**
     * Creates a Range covering just the given node.
     */
    private Range nodeRange(Node node) {
        Position start = markToPosition(node.getStartMark());
        Position end = markToPosition(node.getEndMark());
        return new Range(start, end);
    }

    private Position markToPosition(org.yaml.snakeyaml.error.Mark mark) {
        if (mark == null) {
            return new Position(0, 0);
        }
        return new Position(mark.getLine(), mark.getColumn());
    }
}
