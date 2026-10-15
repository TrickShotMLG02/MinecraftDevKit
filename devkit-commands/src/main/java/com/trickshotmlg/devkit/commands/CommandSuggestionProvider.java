package com.trickshotmlg.devkit.commands;

import java.util.List;
import java.util.concurrent.CompletionStage;

/** Produces argument suggestions without assuming a platform or execution thread. */
@FunctionalInterface
public interface CommandSuggestionProvider {
    CompletionStage<List<String>> suggest(CommandContext context, String currentInput);
}
