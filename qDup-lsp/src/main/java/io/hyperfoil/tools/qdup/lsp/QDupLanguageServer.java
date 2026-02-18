package io.hyperfoil.tools.qdup.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * qDup Language Server implementation.
 * Provides completion, hover, and diagnostics for qDup YAML files.
 */
public class QDupLanguageServer implements LanguageServer, LanguageClientAware {

    private static final Logger LOG = Logger.getLogger(QDupLanguageServer.class.getName());

    private final QDupTextDocumentService textDocumentService;
    private final QDupWorkspaceService workspaceService;
    private LanguageClient client;
    private int errorCode = 1;

    public QDupLanguageServer() {
        CommandRegistry registry = new CommandRegistry();
        Properties commandDocs = loadCommandDocs();
        this.textDocumentService = new QDupTextDocumentService(registry, commandDocs);
        this.workspaceService = new QDupWorkspaceService();
    }

    private Properties loadCommandDocs() {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("command-docs.properties")) {
            if (is != null) {
                props.load(is);
            } else {
                LOG.warning("command-docs.properties not found in classpath");
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to load command-docs.properties", e);
        }
        return props;
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        ServerCapabilities capabilities = new ServerCapabilities();

        // Text document sync - full document sync
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);

        // Completion support
        CompletionOptions completionOptions = new CompletionOptions();
        completionOptions.setTriggerCharacters(java.util.List.of(":", "-", " "));
        completionOptions.setResolveProvider(false);
        capabilities.setCompletionProvider(completionOptions);

        // Hover support
        capabilities.setHoverProvider(true);

        // Go-to-definition support
        capabilities.setDefinitionProvider(true);

        // Document symbols (outline) support
        capabilities.setDocumentSymbolProvider(true);

        InitializeResult result = new InitializeResult(capabilities);
        ServerInfo serverInfo = new ServerInfo("qDup Language Server", "0.11.1-SNAPSHOT");
        result.setServerInfo(serverInfo);

        // Scan workspace for cross-file go-to-definition
        String rootUri = params.getRootUri();
        if (rootUri == null && params.getWorkspaceFolders() != null && !params.getWorkspaceFolders().isEmpty()) {
            rootUri = params.getWorkspaceFolders().get(0).getUri();
        }
        if (rootUri != null) {
            textDocumentService.scanWorkspace(rootUri);
        }

        LOG.info("qDup Language Server initialized");
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        errorCode = 0;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        System.exit(errorCode);
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }

    @Override
    public void connect(LanguageClient client) {
        this.client = client;
        this.textDocumentService.setClient(client);
        LOG.info("Language client connected");
    }
}
