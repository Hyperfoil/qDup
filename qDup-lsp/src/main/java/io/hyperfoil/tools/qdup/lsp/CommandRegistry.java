package io.hyperfoil.tools.qdup.lsp;

import io.hyperfoil.tools.qdup.config.yaml.CmdMapping;
import io.hyperfoil.tools.qdup.config.yaml.HostDefinition;
import io.hyperfoil.tools.qdup.config.yaml.Parser;

import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Extracts command metadata from the qDup-core Parser for use by the LSP.
 * Uses reflection to access the Parser's internal command registration data.
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

    @SuppressWarnings("unchecked")
    private void loadFromParser() {
        try {
            Parser parser = Parser.getInstance();

            // Reflect on cmdMappings field: Map<Class, CmdMapping>
            Field cmdMappingsField = Parser.class.getDeclaredField("cmdMappings");
            cmdMappingsField.setAccessible(true);
            Map<Class, CmdMapping> cmdMappings = (Map<Class, CmdMapping>) cmdMappingsField.get(parser);

            for (Map.Entry<Class, CmdMapping> entry : cmdMappings.entrySet()) {
                String key = entry.getValue().getKey();
                if (key != null && !key.startsWith("#")) { // skip internal markers like #NO_OP
                    commandNames.add(key);
                }
            }

            // Reflect on noArgs field: Map<String, FromString>
            Field noArgsField = Parser.class.getDeclaredField("noArgs");
            noArgsField.setAccessible(true);
            Map<String, ?> noArgs = (Map<String, ?>) noArgsField.get(parser);
            noArgCommands.addAll(noArgs.keySet());

            // Extract expected keys from constructor's constructs
            // We do this by inspecting the OverloadConstructor
            Field constructorField = Parser.class.getDeclaredField("constructor");
            constructorField.setAccessible(true);
            Object constructor = constructorField.get(parser);

            // Try to get constructs from OverloadConstructor
            try {
                Field constructsField = findField(constructor.getClass(), "yamlConstructors");
                if (constructsField != null) {
                    constructsField.setAccessible(true);
                    Map<?, ?> constructs = (Map<?, ?>) constructsField.get(constructor);
                    for (Map.Entry<?, ?> cEntry : constructs.entrySet()) {
                        Object construct = cEntry.getValue();
                        if (construct != null) {
                            // Check if it's a CmdConstruct or CmdWithElseConstruct
                            Field expectedKeysField = findField(construct.getClass(), "expectedKeys");
                            Field tagField = findField(construct.getClass(), "tag");
                            if (expectedKeysField != null && tagField != null) {
                                expectedKeysField.setAccessible(true);
                                tagField.setAccessible(true);
                                String tag = (String) tagField.get(construct);
                                List<String> keys = (List<String>) expectedKeysField.get(construct);
                                if (tag != null && keys != null && !keys.isEmpty()) {
                                    commandExpectedKeys.put(tag, new ArrayList<>(keys));
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOG.log(Level.FINE, "Could not extract expected keys from constructs", e);
            }

            // Manually add well-known expected keys for commands we know about
            addKnownExpectedKeys();

            LOG.info("CommandRegistry loaded " + commandNames.size() + " commands, " + noArgCommands.size() + " no-arg commands");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load commands from Parser, using fallback", e);
            loadFallbackCommands();
        }
    }

    private void addKnownExpectedKeys() {
        // Only add if not already discovered via reflection
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

    private Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
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
