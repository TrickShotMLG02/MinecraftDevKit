package com.trickshotmlg.devkit.commands;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable execution context. Adapters populate it from Cloud's typed command context. */
public final class CommandContext {
    private final CommandSender sender;
    private final Map<Argument<?>, Object> values;

    public CommandContext(CommandSender sender, Map<Argument<?>, ?> values) {
        this.sender = Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(values, "values");
        var copy = new java.util.LinkedHashMap<Argument<?>, Object>();
        values.forEach((argument, value) -> {
            Objects.requireNonNull(argument, "argument");
            if (value != null && !argument.valueType().isInstance(value)) {
                throw new IllegalArgumentException("Value for '" + argument.name() + "' has the wrong type");
            }
            copy.put(argument, value);
        });
        this.values = java.util.Collections.unmodifiableMap(copy);
    }

    public CommandSender sender() { return sender; }

    public <T> T get(Argument<T> argument) {
        Objects.requireNonNull(argument, "argument");
        if (!values.containsKey(argument)) throw new IllegalStateException("Argument '" + argument.name() + "' is not available");
        Object value = values.get(argument);
        if (value == null) throw new IllegalStateException("Optional argument '" + argument.name() + "' has no value or default");
        return argument.valueType().cast(value);
    }

    public <T> Optional<T> getOptional(Argument<T> argument) {
        Objects.requireNonNull(argument, "argument");
        if (!values.containsKey(argument)) throw new IllegalStateException("Argument '" + argument.name() + "' is not available");
        return Optional.ofNullable(argument.valueType().cast(values.get(argument)));
    }
}
