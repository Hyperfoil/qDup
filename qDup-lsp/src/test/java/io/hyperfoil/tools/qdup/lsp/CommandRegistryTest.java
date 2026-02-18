package io.hyperfoil.tools.qdup.lsp;

import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class CommandRegistryTest {

    @Test
    public void testCommandNamesLoaded() {
        CommandRegistry registry = new CommandRegistry();
        Set<String> commands = registry.getCommandNames();

        assertFalse("Should have loaded commands", commands.isEmpty());
        assertTrue("Should contain 'sh'", commands.contains("sh"));
        assertTrue("Should contain 'regex'", commands.contains("regex"));
        assertTrue("Should contain 'set-state'", commands.contains("set-state"));
        assertTrue("Should contain 'download'", commands.contains("download"));
        assertTrue("Should contain 'upload'", commands.contains("upload"));
        assertTrue("Should contain 'for-each'", commands.contains("for-each"));
        assertTrue("Should contain 'signal'", commands.contains("signal"));
        assertTrue("Should contain 'wait-for'", commands.contains("wait-for"));
        assertTrue("Should contain 'sleep'", commands.contains("sleep"));
        assertTrue("Should contain 'log'", commands.contains("log"));
        assertTrue("Should contain 'abort'", commands.contains("abort"));
        assertTrue("Should contain 'js'", commands.contains("js"));
        assertTrue("Should contain 'xml'", commands.contains("xml"));
    }

    @Test
    public void testNoArgCommands() {
        CommandRegistry registry = new CommandRegistry();
        Set<String> noArgs = registry.getNoArgCommands();

        assertTrue("ctrlC should be no-arg", noArgs.contains("ctrlC"));
        assertTrue("done should be no-arg", noArgs.contains("done"));
        assertTrue("echo should be no-arg", noArgs.contains("echo"));
        assertFalse("sh should NOT be no-arg", noArgs.contains("sh"));
        assertFalse("regex should NOT be no-arg", noArgs.contains("regex"));
    }

    @Test
    public void testModifierKeys() {
        CommandRegistry registry = new CommandRegistry();
        Set<String> modifiers = registry.getModifierKeys();

        assertTrue("Should contain 'then'", modifiers.contains("then"));
        assertTrue("Should contain 'with'", modifiers.contains("with"));
        assertTrue("Should contain 'watch'", modifiers.contains("watch"));
        assertTrue("Should contain 'timer'", modifiers.contains("timer"));
        assertTrue("Should contain 'on-signal'", modifiers.contains("on-signal"));
        assertTrue("Should contain 'silent'", modifiers.contains("silent"));
        assertTrue("Should contain 'prefix'", modifiers.contains("prefix"));
        assertTrue("Should contain 'suffix'", modifiers.contains("suffix"));
        assertEquals("Should have 12 modifiers", 12, modifiers.size());
    }

    @Test
    public void testHostConfigKeys() {
        CommandRegistry registry = new CommandRegistry();
        List<String> hostKeys = registry.getHostConfigKeys();

        assertTrue("Should contain 'hostname'", hostKeys.contains("hostname"));
        assertTrue("Should contain 'username'", hostKeys.contains("username"));
        assertTrue("Should contain 'password'", hostKeys.contains("password"));
        assertTrue("Should contain 'port'", hostKeys.contains("port"));
        assertTrue("Should contain 'identity'", hostKeys.contains("identity"));
        assertTrue("Should contain 'local'", hostKeys.contains("local"));
        assertTrue("Should contain 'platform'", hostKeys.contains("platform"));
        assertEquals("Should have 21 host keys", 21, hostKeys.size());
    }

    @Test
    public void testExpectedKeys() {
        CommandRegistry registry = new CommandRegistry();

        List<String> shKeys = registry.getExpectedKeys("sh");
        assertTrue("sh should have 'command' key", shKeys.contains("command"));
        assertTrue("sh should have 'prompt' key", shKeys.contains("prompt"));

        List<String> setStateKeys = registry.getExpectedKeys("set-state");
        assertTrue("set-state should have 'key'", setStateKeys.contains("key"));
        assertTrue("set-state should have 'value'", setStateKeys.contains("value"));

        List<String> regexKeys = registry.getExpectedKeys("regex");
        assertTrue("regex should have 'pattern'", regexKeys.contains("pattern"));
    }

    @Test
    public void testIsCommand() {
        CommandRegistry registry = new CommandRegistry();

        assertTrue(registry.isCommand("sh"));
        assertTrue(registry.isCommand("regex"));
        assertTrue(registry.isCommand("ctrlC"));
        assertFalse(registry.isCommand("nonexistent"));
        assertFalse(registry.isCommand("then")); // modifier, not command
    }

    @Test
    public void testTopLevelKeys() {
        CommandRegistry registry = new CommandRegistry();
        Set<String> topLevel = registry.getTopLevelKeys();

        assertTrue(topLevel.contains("name"));
        assertTrue(topLevel.contains("scripts"));
        assertTrue(topLevel.contains("hosts"));
        assertTrue(topLevel.contains("roles"));
        assertTrue(topLevel.contains("states"));
        assertTrue(topLevel.contains("globals"));
    }

    @Test
    public void testRoleKeys() {
        CommandRegistry registry = new CommandRegistry();
        Set<String> roleKeys = registry.getRoleKeys();

        assertTrue(roleKeys.contains("hosts"));
        assertTrue(roleKeys.contains("setup-scripts"));
        assertTrue(roleKeys.contains("run-scripts"));
        assertTrue(roleKeys.contains("cleanup-scripts"));
    }
}
