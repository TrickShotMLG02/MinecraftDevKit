package com.trickshotmlg.devkit.commands.error;

/** Stable error categories that applications can localize or replace. */
public enum CommandErrorCategory {
    UNKNOWN_COMMAND,
    INVALID_SYNTAX,
    MISSING_ARGUMENT,
    INVALID_ARGUMENT,
    NO_PERMISSION,
    PLAYER_ONLY,
    CONSOLE_ONLY,
    EXECUTION_ERROR
}
