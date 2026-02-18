package io.hyperfoil.tools.qdup.lsp;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point for the qDup Language Server.
 * Launches the LSP server using stdin/stdout JSON-RPC communication.
 */
public class QDupLspLauncher {

    private static final Logger LOG = Logger.getLogger(QDupLspLauncher.class.getName());

    public static void main(String[] args) {
        LOG.info("Starting qDup Language Server...");

        InputStream in = System.in;
        OutputStream out = System.out;

        // Redirect stderr for logging so it doesn't interfere with JSON-RPC on stdout
        System.setOut(System.err);

        QDupLanguageServer server = new QDupLanguageServer();

        Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, in, out);
        LanguageClient client = launcher.getRemoteProxy();
        server.connect(client);

        Future<?> startListening = launcher.startListening();

        LOG.info("qDup Language Server is listening");

        try {
            startListening.get();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "qDup Language Server error", e);
        }
    }
}
