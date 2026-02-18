package io.hyperfoil.tools.qdup.lsp;

import io.hyperfoil.tools.qdup.config.yaml.CmdMapping;
import io.hyperfoil.tools.qdup.config.yaml.HostDefinition;
import io.hyperfoil.tools.qdup.config.yaml.Parser;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Extracts command metadata from the qDup-core Parser for use by the LSP.
 * Uses Parser's public API to access command registration data.
 */
public class CommandRegistry {

    private static final Logger LOG = Logger.getLogger(CommandRegistry.class.getName());

    private final Set<String> commandNames = new LinkedHashSet<>();
    private final Set<String> noArgCommands = new LinkedHashSet<>();
    private final Map<String, List<String>> commandExpectedKeys = new LinkedHashMap<>();
    private final Set<String> modifierKeys;
    private final List<String> hostConfigKeys;

    public CommandRegistry() {
        this.modifierKeys = CmdMapping.COMMAND_KEYS;
        this.hostConfigKeys = HostDefinition.KEYS;
        loadFromParser();
    }

    private void loadFromParser() {
        try {
            Parser parser = Parser.getInstance();

            commandNames.addAll(parser.getCommandNames());
            noArgCommands.addAll(parser.getNoArgCommandNames());

            // Load expected keys for each command from Parser's public API
            for (String cmd : commandNames) {
                List<String> params = parser.getCommandParameters(cmd);
                if (!params.isEmpty()) {
                    commandExpectedKeys.put(cmd, new ArrayList<>(params));
                }
            }

            // Add well-known expected keys for commands not covered by the public API
            // (e.g., commands registered via CmdWithElseConstruct that don't track parameters)
            addKnownExpectedKeys();

            LOG.info("CommandRegistry loaded " + commandNames.size() + " commands, " + noArgCommands.size() + " no-arg commands");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load commands from Parser, using fallback", e);
            loadFallbackCommands();
        }
    }

    private void addKnownExpectedKeys() {
        // Only add if not already discovered via Parser API
        commandExpectedKeys.putIfAbsent("abort", List.of("message", "skip-cleanup"));
        commandExpectedKeys.putIfAbsent("add-prompt", List.of("prompt", "is-shell"));
        commandExpectedKeys.putIfAbsent("countdown", List.of("name", "initial"));
        commandExpectedKeys.putIfAbsent("download", List.of("path", "destination", "max-size"));
        commandExpectedKeys.putIfAbsent("exec", List.of("command", "async", "silent"));
        commandExpectedKeys.putIfAbsent("for-each", List.of("name", "input"));
        commandExpectedKeys.putIfAbsent("queue-download", List.of("path", "destination", "max-size"));
        commandExpectedKeys.putIfAbsent("regex", List.of("pattern", "miss", "autoConvert"));
        commandExpectedKeys.putIfAbsent("script", List.of("name", "async"));
        commandExpectedKeys.putIfAbsent("set-signal", List.of("name", "count", "reset"));
        commandExpectedKeys.putIfAbsent("set-state", List.of("key", "value", "separator", "silent", "autoConvert"));
        commandExpectedKeys.putIfAbsent("sh", List.of("command", "prompt", "ignore-exit-code", "silent"));
        commandExpectedKeys.putIfAbsent("upload", List.of("path", "destination"));
        commandExpectedKeys.putIfAbsent("wait-for", List.of("name", "initial"));
        commandExpectedKeys.putIfAbsent("xml", List.of("operations", "path"));
    }

    private void loadFallbackCommands() {
        commandNames.addAll(List.of(
            "abort", "add-prompt", "countdown", "ctrlC", "ctrl/", "ctrl\\",
            "ctrlU", "ctrlZ", "done", "download", "echo", "exec",
            "for-each", "js", "json", "log", "parse", "queue-download",
            "read-signal", "read-state", "regex", "repeat-until", "script",
            "send-text", "set-signal", "set-state", "sh", "signal",
            "sleep", "upload", "wait-for", "xml"
        ));
        noArgCommands.addAll(List.of("ctrlC", "ctrl/", "ctrl\\", "ctrlU", "ctrlZ", "done", "echo"));
        addKnownExpectedKeys();
    }

    public Set<String> getCommandNames() {
        return Collections.unmodifiableSet(commandNames);
    }

    public Set<String> getNoArgCommands() {
        return Collections.unmodifiableSet(noArgCommands);
    }

    public boolean isCommand(String name) {
        return commandNames.contains(name);
    }

    public boolean isNoArgCommand(String name) {
        return noArgCommands.contains(name);
    }

    public List<String> getExpectedKeys(String commandName) {
        return commandExpectedKeys.getOrDefault(commandName, Collections.emptyList());
    }

    public Set<String> getModifierKeys() {
        return modifierKeys;
    }

    public List<String> getHostConfigKeys() {
        return hostConfigKeys;
    }

    public Set<String> getTopLevelKeys() {
        return Set.of("name", "scripts", "hosts", "roles", "states", "globals");
    }

    public Set<String> getRoleKeys() {
        return Set.of("hosts", "setup-scripts", "run-scripts", "cleanup-scripts");
    }
}
