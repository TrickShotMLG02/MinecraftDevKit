package com.trickshotmlg.devkit.commands;

import java.util.Objects;
import java.util.Optional;

/** Immutable declaration that an argument is required or optional. */
public record ArgumentBinding<T>(Argument<T> argument, boolean required, Optional<T> defaultValue) {
    public ArgumentBinding {
        Objects.requireNonNull(argument, "argument");
        Objects.requireNonNull(defaultValue, "defaultValue");
        if (required && defaultValue.isPresent()) throw new IllegalArgumentException("Required arguments cannot have defaults");
        defaultValue.ifPresent(value -> {
            if (!argument.valueType().isInstance(value)) throw new IllegalArgumentException("Default value has the wrong type for " + argument.name());
        });
    }
}
