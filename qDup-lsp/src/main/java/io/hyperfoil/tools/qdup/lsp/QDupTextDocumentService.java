package io.hyperfoil.tools.qdup.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Text document service for qDup LSP.
 * Handles document lifecycle events and provides completion, hover, and diagnostics.
 */
public class QDupTextDocumentService implements TextDocumentService {

    private static final Logger LOG = Logger.getLogger(QDupTextDocumentService.class.getName());

    private final Map<String, QDupDocument> documents = new ConcurrentHashMap<>();
    private final Map<String, QDupDocument> workspaceDocuments = new ConcurrentHashMap<>();
    private final CompletionProvider completionProvider;
    private final DiagnosticsProvider diagnosticsProvider;
    private final HoverProvider hoverProvider;
    private final DefinitionProvider definitionProvider;
    private final DocumentSymbolProvider documentSymbolProvider;
    private LanguageClient client;

    public QDupTextDocumentService(CommandRegistry registry, Properties commandDocs) {
        CursorContextResolver contextResolver = new CursorContextResolver(registry);
        this.completionProvider = new CompletionProvider(registry, contextResolver, commandDocs);
        this.diagnosticsProvider = new DiagnosticsProvider(registry);
        this.hoverProvider = new HoverProvider(registry, contextResolver, commandDocs);
        this.definitionProvider = new DefinitionProvider(contextResolver);
        this.documentSymbolProvider = new DocumentSymbolProvider();
    }

    public void setClient(LanguageClient client) {
        this.client = client;
    }

    /**
     * Scans the workspace directory for YAML files and parses them.
     */
    public void scanWorkspace(String rootUri) {
        if (rootUri == null) {
            return;
        }
        try {
            Path rootPath = Paths.get(URI.create(rootUri));
            try (Stream<Path> paths = Files.walk(rootPath)) {
                paths.filter(Files::isRegularFile)
                     .filter(p -> {
                         String name = p.getFileName().toString();
                         return name.endsWith(".yaml") || name.endsWith(".yml");
                     })
                     .forEach(p -> {
                         try {
                             String content = Files.readString(p);
                             String fileUri = p.toUri().toString();
                             QDupDocument doc = new QDupDocument(fileUri, content);
                             if (doc.isParseSuccessful()) {
                                 workspaceDocuments.put(fileUri, doc);
                             }
                         } catch (IOException e) {
                             LOG.log(Level.FINE, "Failed to read workspace file: " + p, e);
                         }
                     });
            }
            LOG.info("Scanned workspace: " + workspaceDocuments.size() + " YAML files found");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to scan workspace: " + rootUri, e);
        }
    }

    /**
     * Returns all known documents, with open documents taking priority over workspace-scanned ones.
     */
    public Collection<QDupDocument> getAllDocuments() {
        Map<String, QDupDocument> merged = new HashMap<>(workspaceDocuments);
        merged.putAll(documents); // open documents override workspace docs
        return merged.values();
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        String text = params.getTextDocument().getText();
        QDupDocument doc = new QDupDocument(uri, text);
        documents.put(uri, doc);
        publishDiagnostics(uri, doc);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        List<TextDocumentContentChangeEvent> changes = params.getContentChanges();
        if (!changes.isEmpty()) {
            // We use full document sync, so take the last change
            String text = changes.get(changes.size() - 1).getText();
            QDupDocument doc = new QDupDocument(uri, text);
            documents.put(uri, doc);
            publishDiagnostics(uri, doc);
        }
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        documents.remove(uri);
        // Clear diagnostics for closed document
        if (client != null) {
            client.publishDiagnostics(new PublishDiagnosticsParams(uri, Collections.emptyList()));
        }
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        // Re-validate on save
        String uri = params.getTextDocument().getUri();
        QDupDocument doc = documents.get(uri);
        if (doc != null) {
            publishDiagnostics(uri, doc);
        }
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String uri = params.getTextDocument().getUri();
            QDupDocument doc = documents.get(uri);
            if (doc == null) {
                return Either.forLeft(Collections.emptyList());
            }
            Position pos = params.getPosition();
            List<CompletionItem> items = completionProvider.complete(doc, pos.getLine(), pos.getCharacter(), getAllDocuments());
            return Either.forLeft(items);
        });
    }

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String uri = params.getTextDocument().getUri();
            QDupDocument doc = documents.get(uri);
            if (doc == null) {
                return null;
            }
            Position pos = params.getPosition();
            return hoverProvider.hover(doc, pos.getLine(), pos.getCharacter(), getAllDocuments());
        });
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String uri = params.getTextDocument().getUri();
            QDupDocument doc = documents.get(uri);
            if (doc == null) {
                return Either.forLeft(Collections.emptyList());
            }
            Position pos = params.getPosition();
            Location location = definitionProvider.definition(doc, pos.getLine(), pos.getCharacter(), getAllDocuments());
            if (location != null) {
                return Either.forLeft(List.of(location));
            }
            return Either.forLeft(Collections.emptyList());
        });
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String uri = params.getTextDocument().getUri();
            QDupDocument doc = documents.get(uri);
            if (doc == null) {
                return Collections.emptyList();
            }
            List<DocumentSymbol> symbols = documentSymbolProvider.documentSymbols(doc);
            List<Either<SymbolInformation, DocumentSymbol>> result = new ArrayList<>();
            for (DocumentSymbol sym : symbols) {
                result.add(Either.forRight(sym));
            }
            return result;
        });
    }

    private void publishDiagnostics(String uri, QDupDocument doc) {
        if (client != null) {
            List<Diagnostic> diagnostics = diagnosticsProvider.diagnose(doc, getAllDocuments());
            client.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics));
        }
    }
}
