package io.hyperfoil.tools.qdup.lsp;

/**
 * Represents where the cursor is positioned within the qDup YAML document structure.
 * Used to determine what completions, diagnostics, and hover info to provide.
 */
public enum YamlContext {
    /** Cursor is at a top-level key position (e.g., name, scripts, hosts, roles, states, globals) */
    TOP_LEVEL_KEY,

    /** Cursor is at a top-level value position */
    TOP_LEVEL_VALUE,

    /** Cursor is at a command key position within a script (e.g., sh, regex, set-state) */
    SCRIPT_COMMAND_KEY,

    /** Cursor is at a command value position */
    SCRIPT_COMMAND_VALUE,

    /** Cursor is at a command modifier key position (e.g., then, else, watch, with, timer) */
    COMMAND_MODIFIER_KEY,

    /** Cursor is at a command-specific parameter key (e.g., command, prompt for sh) */
    COMMAND_PARAM_KEY,

    /** Cursor is at a host configuration key position */
    HOST_CONFIG_KEY,

    /** Cursor is at a role key position (e.g., hosts, setup-scripts, run-scripts, cleanup-scripts) */
    ROLE_KEY,

    /** Cursor is at a role script reference value */
    ROLE_SCRIPT_REF,

    /** Cursor is at a role host reference value */
    ROLE_HOST_REF,

    /** Cursor is at a state variable reference */
    STATE_VARIABLE_REF,

    /** Cursor is at a script name definition (under scripts:) */
    SCRIPT_NAME,

    /** Cursor is at a host name definition (under hosts:) */
    HOST_NAME,

    /** Cursor is at a role name definition (under roles:) */
    ROLE_NAME,

    /** Context could not be determined */
    UNKNOWN
}
