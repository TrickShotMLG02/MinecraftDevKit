package com.trickshotmlg.devkit.commands.error;

import com.trickshotmlg.devkit.text.TextRef;
import com.trickshotmlg.devkit.text.TextCatalog;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Immutable framework error message set with replaceable localized references. */
public final class CommandErrorMessages {
    private final Map<CommandErrorCategory, TextRef> messages;

    private CommandErrorMessages(Map<CommandErrorCategory, TextRef> messages) {
        this.messages = Collections.unmodifiableMap(new EnumMap<>(messages));
    }

    public static CommandErrorMessages defaults() {
        EnumMap<CommandErrorCategory, TextRef> defaults = new EnumMap<>(CommandErrorCategory.class);
        defaults.put(CommandErrorCategory.UNKNOWN_COMMAND, TextRef.translated("commands.error.unknown_command", "Unknown command."));
        defaults.put(CommandErrorCategory.INVALID_SYNTAX, TextRef.translated("commands.error.invalid_syntax", "Invalid command syntax."));
        defaults.put(CommandErrorCategory.MISSING_ARGUMENT, TextRef.translated("commands.error.missing_argument", "A required argument is missing."));
        defaults.put(CommandErrorCategory.INVALID_ARGUMENT, TextRef.translated("commands.error.invalid_argument", "One of the arguments is invalid."));
        defaults.put(CommandErrorCategory.NO_PERMISSION, TextRef.translated("commands.error.no_permission", "You do not have permission to do that."));
        defaults.put(CommandErrorCategory.PLAYER_ONLY, TextRef.translated("commands.error.player_only", "This command can only be used by a player."));
        defaults.put(CommandErrorCategory.CONSOLE_ONLY, TextRef.translated("commands.error.console_only", "This command can only be used from the console."));
        defaults.put(CommandErrorCategory.EXECUTION_ERROR, TextRef.translated("commands.error.execution", "The command could not be completed."));
        return new CommandErrorMessages(defaults);
    }

    /** Returns a copy with one category replaced. */
    public CommandErrorMessages with(CommandErrorCategory category, TextRef message) {
        EnumMap<CommandErrorCategory, TextRef> copy = new EnumMap<>(messages);
        copy.put(Objects.requireNonNull(category, "category"), Objects.requireNonNull(message, "message"));
        return new CommandErrorMessages(copy);
    }

    public TextRef message(CommandErrorCategory category) {
        return Objects.requireNonNull(messages.get(category), "No message configured for " + category);
    }

    public Map<CommandErrorCategory, TextRef> messages() { return messages; }

    /** Collects framework error text references for localization validation. */
    public TextCatalog textReferences() {
        TextCatalog.Builder catalog = TextCatalog.builder();
        messages.forEach((category, reference) -> catalog.add(reference, "framework-error", category.name()));
        return catalog.build();
    }
}
