package com.trickshotmlg.devkit.commands.error;

/** Receives structured failures for application logging without exposing details to senders. */
@FunctionalInterface
public interface CommandErrorReporter {
    void report(CommandErrorCategory category, Throwable failure);

    CommandErrorReporter NO_OP = (category, failure) -> { };
    CommandErrorReporter DEFAULT = (category, failure) -> System.getLogger("com.trickshotmlg.devkit.commands")
            .log(System.Logger.Level.ERROR, "Command execution failed", failure);
}
