package io.hyperfoil.tools.qdup.lsp;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.util.function.Consumer;

/**
 * Workspace service for the qDup LSP server.
 * Handles watched file changes to keep workspace documents in sync.
 */
public class QDupWorkspaceService implements WorkspaceService {

    private Consumer<DidChangeWatchedFilesParams> fileChangeHandler;

    public void setFileChangeHandler(Consumer<DidChangeWatchedFilesParams> handler) {
        this.fileChangeHandler = handler;
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        // No configuration changes to handle yet
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        if (fileChangeHandler != null) {
            fileChangeHandler.accept(params);
        }
    }
}
