package com.trickshotmlg.devkit.commands;

/** User command handler. Exceptions are handled by the command runtime. */
@FunctionalInterface
public interface CommandExecutor {
    void execute(CommandContext context) throws Exception;
}
