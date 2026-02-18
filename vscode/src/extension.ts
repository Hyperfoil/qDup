import * as path from 'path';
import * as fs from 'fs';
import { workspace, ExtensionContext, window, commands } from 'vscode';
import {
    LanguageClient,
    LanguageClientOptions,
    ServerOptions,
} from 'vscode-languageclient/node';
import { execFileSync } from 'child_process';

let client: LanguageClient | undefined;

export async function activate(context: ExtensionContext): Promise<void> {
    const serverOptions = await resolveServerOptions(context);
    if (!serverOptions) {
        return;
    }

    const clientOptions: LanguageClientOptions = {
        documentSelector: [{ language: 'qdup-yaml' }],
        synchronize: {
            fileEvents: workspace.createFileSystemWatcher('**/*.qdup.{yaml,yml}'),
        },
    };

    client = new LanguageClient(
        'qdup-lsp',
        'qDup Language Server',
        serverOptions,
        clientOptions
    );

    client.start();

    const restartCmd = commands.registerCommand('qdup.restartServer', async () => {
        if (client) {
            await client.restart();
        }
    });

    context.subscriptions.push(client, restartCmd);
}

export async function deactivate(): Promise<void> {
    if (client) {
        await client.stop();
        client = undefined;
    }
}

async function resolveServerOptions(context: ExtensionContext): Promise<ServerOptions | undefined> {
    const config = workspace.getConfiguration('qdup.lsp');

    // 1. User-configured JAR path
    const jarPath = config.get<string>('jarPath', '');
    if (jarPath && fs.existsSync(jarPath)) {
        const java = findJava();
        return { command: java, args: ['-jar', jarPath] };
    }

    // 2. Bundled JAR in extension's server/ directory
    const bundledJar = path.join(context.extensionPath, 'server', 'qDup-lsp.jar');
    if (fs.existsSync(bundledJar)) {
        const java = findJava();
        return { command: java, args: ['-jar', bundledJar] };
    }

    // 3. JBang with bundled script
    const jbangScript = path.join(context.extensionPath, 'server', 'qdup-lsp.java');
    if (fs.existsSync(jbangScript)) {
        const jbangPath = config.get<string>('jbangPath', 'jbang');
        if (isExecutableOnPath(jbangPath!)) {
            return { command: jbangPath!, args: [jbangScript] };
        }
    }

    // Nothing found — show error
    window.showErrorMessage(
        'qDup LSP server not found. Set "qdup.lsp.jarPath" in settings or install JBang.',
        'Open Settings'
    ).then(selection => {
        if (selection === 'Open Settings') {
            commands.executeCommand('workbench.action.openSettings', 'qdup.lsp');
        }
    });

    return undefined;
}

function findJava(): string {
    const config = workspace.getConfiguration('qdup.lsp');

    // 1. User-configured JAVA_HOME
    const configJavaHome = config.get<string>('javaHome', '');
    if (configJavaHome) {
        const javaBin = path.join(configJavaHome, 'bin', 'java');
        if (fs.existsSync(javaBin)) {
            return javaBin;
        }
    }

    // 2. JAVA_HOME environment variable
    const envJavaHome = process.env['JAVA_HOME'];
    if (envJavaHome) {
        const javaBin = path.join(envJavaHome, 'bin', 'java');
        if (fs.existsSync(javaBin)) {
            return javaBin;
        }
    }

    // 3. Fall back to java on PATH
    return 'java';
}

function isExecutableOnPath(name: string): boolean {
    try {
        const cmd = process.platform === 'win32' ? 'where' : 'which';
        execFileSync(cmd, [name], { stdio: 'ignore' });
        return true;
    } catch {
        return false;
    }
}
