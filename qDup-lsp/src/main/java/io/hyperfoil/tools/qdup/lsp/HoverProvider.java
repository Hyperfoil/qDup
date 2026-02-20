package io.hyperfoil.tools.qdup.lsp;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;

import java.util.Collection;
import java.util.Properties;

/**
 * Produces Hover content for qDup YAML elements.
 */
public class HoverProvider {

    private final CommandRegistry registry;
    private final CursorContextResolver contextResolver;
    private final Properties commandDocs;

    public HoverProvider(CommandRegistry registry, CursorContextResolver contextResolver, Properties commandDocs) {
        this.registry = registry;
        this.contextResolver = contextResolver;
        this.commandDocs = commandDocs;
    }

    /**
     * Provides hover information for the given position, searching across all workspace documents.
     */
    public Hover hover(QDupDocument document, int line, int character, Collection<QDupDocument> allDocs) {
        String currentLine = document.getLine(line);

        // Check for state variable pattern ${{...}}
        String varName = DefinitionProvider.extractVariableAt(currentLine, character);
        if (varName != null) {
            String content = getStateVariableHover(varName, document, allDocs);
            if (content != null) {
                MarkupContent markup = new MarkupContent();
                markup.setKind(MarkupKind.MARKDOWN);
                markup.setValue(content);
                return new Hover(markup);
            }
        }

        return hover(document, line, character);
    }

    private String getStateVariableHover(String varName, QDupDocument currentDoc, Collection<QDupDocument> allDocs) {
        // Search current document first
        String value = currentDoc.getStateValue(varName);
        if (value != null) {
            return "**${{" + varName + "}}**\n\nValue: `" + value + "`\n\nSource: " + shortUri(currentDoc.getUri());
        }
        if (currentDoc.findStateNode(varName) != null) {
            return "**${{" + varName + "}}**\n\nState variable\n\nSource: " + shortUri(currentDoc.getUri());
        }

        // Search other workspace documents
        if (allDocs != null) {
            for (QDupDocument other : allDocs) {
                if (other.getUri().equals(currentDoc.getUri()) || !other.isParseSuccessful()) {
                    continue;
                }
                value = other.getStateValue(varName);
                if (value != null) {
                    return "**${{" + varName + "}}**\n\nValue: `" + value + "`\n\nSource: " + shortUri(other.getUri());
                }
                if (other.findStateNode(varName) != null) {
                    return "**${{" + varName + "}}**\n\nState variable\n\nSource: " + shortUri(other.getUri());
                }
            }
        }

        return "**${{" + varName + "}}**\n\nState variable (undefined)";
    }

    private String shortUri(String uri) {
        int lastSlash = uri.lastIndexOf('/');
        return lastSlash >= 0 ? uri.substring(lastSlash + 1) : uri;
    }

    /**
     * Provides hover information for the given position in the document.
     */
    public Hover hover(QDupDocument document, int line, int character) {
        String currentLine = document.getLine(line);
        String word = extractWordAt(currentLine, character);

        if (word == null || word.isEmpty()) {
            return null;
        }

        YamlContext context = contextResolver.resolve(document, line, character);

        String content = null;

        switch (context) {
            case SCRIPT_COMMAND_KEY:
            case SCRIPT_COMMAND_VALUE:
                if (registry.isCommand(word)) {
                    content = getCommandHover(word);
                }
                break;

            case COMMAND_MODIFIER_KEY:
                content = getModifierHover(word);
                break;

            case COMMAND_PARAM_KEY:
                String cmdName = contextResolver.findCommandAtLine(document, line);
                if (cmdName != null) {
                    content = getParamHover(cmdName, word);
                }
                if (content == null && registry.isCommand(word)) {
                    content = getCommandHover(word);
                }
                if (content == null) {
                    content = getModifierHover(word);
                }
                break;

            case HOST_CONFIG_KEY:
                content = getHostKeyHover(word);
                break;

            case TOP_LEVEL_KEY:
                content = getTopLevelKeyHover(word);
                break;

            case ROLE_KEY:
                content = getRoleKeyHover(word);
                break;

            default:
                // Try command name hover as fallback
                if (registry.isCommand(word)) {
                    content = getCommandHover(word);
                }
                break;
        }

        if (content != null) {
            MarkupContent markup = new MarkupContent();
            markup.setKind(MarkupKind.MARKDOWN);
            markup.setValue(content);
            return new Hover(markup);
        }

        return null;
    }

    private String getCommandHover(String command) {
        String doc = commandDocs.getProperty(command);
        if (doc != null) {
            return "**" + command + "**\n\n" + doc;
        }
        if (registry.isNoArgCommand(command)) {
            return "**" + command + "** (no arguments)\n\nqDup command";
        }
        return "**" + command + "**\n\nqDup command";
    }

    private String getModifierHover(String modifier) {
        return switch (modifier) {
            case "then" -> "**then**\n\nCommands to run when the parent command succeeds. " +
                           "The commands are executed sequentially after the parent completes.";
            case "else" -> "**else**\n\nCommands to run when the parent command does not match or fails. " +
                           "Used with commands like `regex` and `read-state`.";
            case "with" -> "**with**\n\nSet state variables for this command and its children. " +
                           "Accepts a map of key-value pairs.";
            case "watch" -> "**watch**\n\nCommands to run concurrently while this command executes. " +
                            "Watchers observe the command's output in real time.";
            case "timer" -> "**timer**\n\nCommands to run after a specified timeout. " +
                            "Accepts a map of duration to command list.";
            case "on-signal" -> "**on-signal**\n\nCommands to run when a specific signal is received. " +
                                "Accepts a map of signal name to command list.";
            case "silent" -> "**silent**\n\nSuppress command output in the qDup log. " +
                             "Set to `true` to silence output.";
            case "prefix" -> "**prefix**\n\nOverride the state variable prefix pattern (default `${{`).";
            case "suffix" -> "**suffix**\n\nOverride the state variable suffix pattern (default `}}`).";
            case "separator" -> "**separator**\n\nOverride the state variable default value separator (default `:`).";
            case "js-prefix" -> "**js-prefix**\n\nOverride the JavaScript evaluation prefix (default `=`).";
            case "idle-timer" -> "**idle-timer**\n\nSet or disable the idle timer for this command. " +
                                 "Set to `false` to disable, or a duration string to customize.";
            case "state-scan" -> "**state-scan**\n\nEnable or disable state variable scanning in command output. " +
                                 "Set to `false` to disable automatic state variable detection.";
            default -> null;
        };
    }

    private String getParamHover(String command, String param) {
        String key = command + "." + param;
        String doc = commandDocs.getProperty(key);
        if (doc != null) {
            return "**" + param + "** (parameter of `" + command + "`)\n\n" + doc;
        }
        return null;
    }

    private String getHostKeyHover(String key) {
        return switch (key) {
            case "username" -> "**username**\n\nSSH username for connecting to the host.";
            case "hostname" -> "**hostname**\n\nHostname or IP address of the remote host.";
            case "password" -> "**password**\n\nSSH password for authentication.";
            case "port" -> "**port**\n\nSSH port number (default: 22).";
            case "prompt" -> "**prompt**\n\nExpected shell prompt pattern for the host.";
            case "local" -> "**local**\n\nSet to `true` to use a local shell instead of SSH.";
            case "platform" -> "**platform**\n\nContainer platform to use (e.g., `podman`, `docker`).";
            case "container" -> "**container**\n\nContainer name or image to use.";
            case "identity" -> "**identity**\n\nPath to SSH identity (private key) file.";
            case "upload" -> "**upload**\n\nCustom upload command template.";
            case "download" -> "**download**\n\nCustom download command template.";
            case "exec" -> "**exec**\n\nCustom exec command template.";
            case "is-shell" -> "**is-shell**\n\nWhether the connection provides a shell (default: true).";
            case "connect-shell" -> "**connect-shell**\n\nCommand to establish a shell connection to the host.";
            case "platform-login" -> "**platform-login**\n\nCommand to log in to the container platform.";
            case "create-container" -> "**create-container**\n\nCommand to create a new container.";
            case "create-connected-container" -> "**create-connected-container**\n\nCommand to create and connect to a container.";
            case "restart-connected-container" -> "**restart-connected-container**\n\nCommand to restart and reconnect to a container.";
            case "stop-container" -> "**stop-container**\n\nCommand to stop a running container.";
            case "check-container-id" -> "**check-container-id**\n\nCommand to check the container ID.";
            case "check-container-name" -> "**check-container-name**\n\nCommand to check the container name.";
            default -> "**" + key + "**\n\nHost configuration key.";
        };
    }

    private String getTopLevelKeyHover(String key) {
        return switch (key) {
            case "name" -> "**name**\n\nThe name of this qDup configuration.";
            case "scripts" -> "**scripts**\n\nDefines named scripts containing sequences of commands.";
            case "hosts" -> "**hosts**\n\nDefines named host configurations for SSH connections.";
            case "roles" -> "**roles**\n\nAssigns scripts to hosts for execution. " +
                            "Each role specifies hosts and which scripts to run.";
            case "states" -> "**states**\n\nDefines initial state variables available to scripts.";
            case "globals" -> "**globals**\n\nDefines global state variables shared across all scripts.";
            default -> null;
        };
    }

    private String getRoleKeyHover(String key) {
        return switch (key) {
            case "hosts" -> "**hosts**\n\nList of host names (defined in `hosts:`) to run this role's scripts on.";
            case "setup-scripts" -> "**setup-scripts**\n\nScripts to run during the setup phase (before run-scripts).";
            case "run-scripts" -> "**run-scripts**\n\nScripts to run during the main execution phase.";
            case "cleanup-scripts" -> "**cleanup-scripts**\n\nScripts to run during the cleanup phase (after run-scripts).";
            default -> null;
        };
    }

    /**
     * Extracts the word at the given character position in a line.
     */
    private String extractWordAt(String line, int character) {
        if (line == null || character < 0 || character > line.length()) {
            return null;
        }

        // Find word boundaries
        int start = character;
        int end = character;

        while (start > 0 && isWordChar(line.charAt(start - 1))) {
            start--;
        }
        while (end < line.length() && isWordChar(line.charAt(end))) {
            end++;
        }

        if (start == end) {
            return null;
        }

        String word = line.substring(start, end);
        // Strip leading "- " prefix from YAML list items
        if (word.startsWith("- ")) {
            word = word.substring(2);
        }
        return word;
    }

    private boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '/' || c == '\\';
    }
}
