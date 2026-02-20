# qDup Language Server

A Language Server Protocol (LSP) implementation for qDup YAML scripts, providing IDE support for command completion, diagnostics, and hover documentation.

## Building

```bash
# Build the fat JAR (includes all dependencies)
mvn -pl qDup-lsp package -DskipTests

# Run unit tests
mvn -pl qDup-lsp test
```

The fat JAR is produced at `qDup-lsp/target/qDup-lsp-0.11.1-SNAPSHOT.jar`.

## Running

The server communicates via stdin/stdout using the JSON-RPC protocol defined by LSP.

### Using JBang (recommended)

The easiest way to run the server is with [JBang](https://www.jbang.dev/). No build step required — JBang resolves the dependency and launches the server directly:

```bash
jbang qDup-lsp/qdup-lsp.java
```

Install JBang if you don't have it:

```bash
curl -Ls https://sh.jbang.dev | bash -s - app setup
```

For development against a local SNAPSHOT build, install the artifact to your local Maven repository first:

```bash
mvn -pl qDup-lsp install -DskipTests
jbang qDup-lsp/qdup-lsp.java
```

### Using the fat JAR

```bash
java -jar qDup-lsp/target/qDup-lsp-0.11.1-SNAPSHOT.jar
```

## Editor Setup

### Neovim (nvim-lspconfig)

Add a custom server configuration in your Neovim config. You can use either the JBang script or the fat JAR:

```lua
local lspconfig = require('lspconfig')
local configs = require('lspconfig.configs')

-- Option 1: Using JBang
configs.qdup = {
  default_config = {
    cmd = { 'jbang', '/path/to/qdup-lsp.java' },
    filetypes = { 'yaml' },
    root_dir = lspconfig.util.find_git_ancestor,
    settings = {},
  },
}

-- Option 2: Using the fat JAR
-- configs.qdup = {
--   default_config = {
--     cmd = { 'java', '-jar', '/path/to/qDup-lsp-0.11.1-SNAPSHOT.jar' },
--     filetypes = { 'yaml' },
--     root_dir = lspconfig.util.find_git_ancestor,
--     settings = {},
--   },
-- }

lspconfig.qdup.setup({})
```

To limit activation to qDup files only, you can use an `on_attach` or `autocommand` that checks for qDup-specific top-level keys (`scripts:`, `roles:`, `hosts:`).

### VS Code

Install a generic LSP client extension such as [vscode-languageclient](https://github.com/AKosyak/vscode-glspc) or create a minimal extension with a `package.json`:

```json
{
  "name": "qdup-lsp",
  "displayName": "qDup Language Support",
  "version": "0.1.0",
  "engines": { "vscode": "^1.75.0" },
  "activationEvents": ["onLanguage:yaml"],
  "main": "./extension.js",
  "contributes": {
    "configuration": {
      "properties": {
        "qdup.lsp.path": {
          "type": "string",
          "default": "",
          "description": "Path to the qDup LSP fat JAR"
        }
      }
    }
  }
}
```

With an `extension.js`:

```javascript
const { LanguageClient, TransportKind } = require('vscode-languageclient/node');

let client;

function activate(context) {
  const jarPath = vscode.workspace.getConfiguration('qdup').get('lsp.path');
  const serverOptions = {
    command: 'java',
    args: ['-jar', jarPath],
    transport: TransportKind.stdio,
  };
  const clientOptions = {
    documentSelector: [{ scheme: 'file', language: 'yaml' }],
  };
  client = new LanguageClient('qdup', 'qDup Language Server', serverOptions, clientOptions);
  client.start();
}

function deactivate() {
  return client?.stop();
}

module.exports = { activate, deactivate };
```

### Emacs (eglot)

```elisp
;; Using JBang
(with-eval-after-load 'eglot
  (add-to-list 'eglot-server-programs
               '(yaml-mode . ("jbang" "/path/to/qdup-lsp.java"))))

;; Or using the fat JAR
;; (with-eval-after-load 'eglot
;;   (add-to-list 'eglot-server-programs
;;                '(yaml-mode . ("java" "-jar" "/path/to/qDup-lsp-0.11.1-SNAPSHOT.jar"))))
```

### Helix

Add to `~/.config/helix/languages.toml`:

```toml
[[language]]
name = "yaml"
language-servers = ["qdup-lsp"]

# Using JBang
[language-server.qdup-lsp]
command = "jbang"
args = ["/path/to/qdup-lsp.java"]

# Or using the fat JAR
# [language-server.qdup-lsp]
# command = "java"
# args = ["-jar", "/path/to/qDup-lsp-0.11.1-SNAPSHOT.jar"]
```

## Features

### Completion

The server provides context-aware completions for:

| Context | Completions |
|---|---|
| Top-level keys | `name`, `scripts`, `hosts`, `roles`, `states`, `globals` |
| Script commands | All 32+ qDup commands (`sh`, `regex`, `set-state`, etc.) |
| Command modifiers | `then`, `else`, `watch`, `with`, `timer`, `on-signal`, `silent`, etc. |
| Command parameters | Command-specific keys (e.g., `command`, `prompt` for `sh`) |
| Host configuration | 21 host config keys (`hostname`, `username`, `port`, `identity`, etc.) |
| Role properties | `hosts`, `setup-scripts`, `run-scripts`, `cleanup-scripts` |
| Script references | Script names defined in the `scripts:` section |

### Diagnostics

The server validates documents and reports:

- **Errors:** Unknown top-level keys, unknown command names, unknown host config keys, unknown role keys
- **Warnings:** Undefined script references in roles, undefined host references in roles
- **Info:** Unused scripts not referenced by any role, unused hosts not referenced by any role

### Hover

Hovering over qDup elements shows documentation:

- **Commands** — description and usage from the qDup reference docs
- **Command parameters** — per-parameter documentation (e.g., `sh.command`, `regex.pattern`)
- **Modifiers** — description of `then`, `watch`, `timer`, `on-signal`, etc.
- **Host config keys** — description of `hostname`, `port`, `identity`, etc.
- **Top-level keys** — description of `scripts`, `roles`, `hosts`, etc.
- **Role keys** — description of `setup-scripts`, `run-scripts`, etc.

## Architecture

```
io.hyperfoil.tools.qdup.lsp
├── QDupLspLauncher          # Entry point (stdin/stdout JSON-RPC)
├── QDupLanguageServer        # LanguageServer impl, declares capabilities
├── QDupTextDocumentService   # Completion, hover, diagnostics wiring
├── QDupWorkspaceService      # Stub
├── QDupDocument              # Parsed document model (text + SnakeYAML Node tree)
├── YamlContext               # Enum of cursor context types
├── CursorContextResolver     # Determines YamlContext from position + Node tree
├── CompletionProvider        # Produces CompletionItems based on context
├── DiagnosticsProvider       # Validates document, produces Diagnostics
├── HoverProvider             # Produces Hover content based on context
└── CommandRegistry           # Extracts command metadata from qDup-core Parser
```

The `CommandRegistry` loads command metadata from the qDup-core `Parser` at startup using reflection, giving the LSP access to the same command definitions used by the qDup runtime. When the `Parser` is not available (e.g., classpath issues), it falls back to a hardcoded command list.

Document parsing uses SnakeYAML's `compose()` method to produce a `Node` tree with line/column positions. When `compose()` fails on broken YAML, a line-based fallback determines context from indentation and parent key patterns.
