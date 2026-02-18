# qDup VS Code Extension

Language support for [qDup](https://github.com/Hyperfoil/qDup) YAML scripts, powered by the qDup Language Server.

## Features

- **Completion** — context-aware suggestions for top-level keys, commands, modifiers, command parameters, host config, role properties, and script references
- **Diagnostics** — errors for unknown keys/commands, warnings for undefined references, info for unused scripts/hosts
- **Hover documentation** — inline docs for commands, parameters, modifiers, host config keys, and more

## Requirements

- **Java 17+** — required to run the LSP server
- One of the following to provide the server:
  - The qDup LSP fat JAR (`qDup-lsp-<version>.jar`), or
  - [JBang](https://www.jbang.dev/) installed on your PATH

## Installation

### From source

```bash
cd vscode
npm install
npm run compile
```

Then press `F5` in VS Code with the `vscode/` folder open to launch an Extension Development Host.

### Package as .vsix

```bash
cd vscode
npm install
npm run compile
npx @vscode/vsce package
```

Install the resulting `.vsix` file via **Extensions > ... > Install from VSIX**.

## File Association

The extension activates automatically for files with these extensions:

- `*.qdup.yaml`
- `*.qdup.yml`

## Server Discovery

The extension locates the qDup LSP server using the following order:

1. **`qdup.lsp.jarPath` setting** — if set to a valid file path, uses `java -jar <path>`
2. **Bundled JAR** — looks for `server/qDup-lsp.jar` inside the extension directory
3. **JBang** — if JBang is on your PATH, runs the bundled `server/qdup-lsp.java` script
4. **Error** — displays a message with a link to open settings

To bundle the JAR with the extension, build it and copy it in:

```bash
mvn -pl qDup-lsp package -DskipTests
cp qDup-lsp/target/qDup-lsp-*.jar vscode/server/qDup-lsp.jar
```

## Settings

| Setting | Default | Description |
|---|---|---|
| `qdup.lsp.jarPath` | *(empty)* | Absolute path to the qDup LSP server JAR file. Leave empty for auto-detection. |
| `qdup.lsp.jbangPath` | `jbang` | Path to the JBang executable. |
| `qdup.lsp.javaHome` | *(empty)* | Path to a Java 17+ installation. Leave empty to use `JAVA_HOME` or `java` on PATH. |

## Commands

| Command | Description |
|---|---|
| `qDup: Restart Language Server` | Restarts the LSP server without reloading the window. |

Access commands via the Command Palette (`Ctrl+Shift+P` / `Cmd+Shift+P`).

## Development

```bash
cd vscode
npm install
npm run watch   # recompile on changes
```

Open the `vscode/` folder in VS Code and press `F5` to launch a development instance with the extension loaded.
