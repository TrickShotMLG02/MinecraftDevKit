package com.trickshotmlg.devkit.commands;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import com.trickshotmlg.devkit.text.TextRef;

/** A strongly typed, immutable handle used to declare and retrieve a command argument. */
public final class Argument<T> {
    private static final Pattern NAME = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*");
    private final String name;
    private final ArgumentType type;
    private final Class<T> valueType;
    private final Class<? extends Enum<?>> enumType;
    private final Optional<Number> minimum;
    private final Optional<Number> maximum;
    private final Optional<TextRef> description;

    Argument(String name, ArgumentType type, Class<T> valueType,
             Class<? extends Enum<?>> enumType, Optional<Number> minimum, Optional<Number> maximum,
             Optional<TextRef> description) {
        this.name = Objects.requireNonNull(name, "name");
        if (!NAME.matcher(name).matches()) throw new IllegalArgumentException("Invalid argument name: " + name);
        this.type = Objects.requireNonNull(type, "type");
        this.valueType = Objects.requireNonNull(valueType, "valueType");
        this.enumType = enumType;
        this.minimum = Objects.requireNonNull(minimum, "minimum");
        this.maximum = Objects.requireNonNull(maximum, "maximum");
        this.description = Objects.requireNonNull(description, "description");
        if (minimum.isPresent() && maximum.isPresent()
                && minimum.get().doubleValue() > maximum.get().doubleValue()) {
            throw new IllegalArgumentException("Minimum exceeds maximum for argument '" + name + "'");
        }
    }

    public String name() { return name; }
    public ArgumentType type() { return type; }
    public Class<T> valueType() { return valueType; }
    public Optional<Class<? extends Enum<?>>> enumType() { return Optional.ofNullable(enumType); }
    public Optional<Number> minimum() { return minimum; }
    public Optional<Number> maximum() { return maximum; }
    public Optional<TextRef> description() { return description; }

    /** Returns a copy of this descriptor with literal explanatory text. */
    public Argument<T> withDescription(String value) { return withDescription(TextRef.literal(value)); }

    /** Returns a copy of this descriptor with localized explanatory text. */
    public Argument<T> withDescription(TextRef value) {
        return new Argument<>(name, type, valueType, enumType, minimum, maximum,
                Optional.of(Objects.requireNonNull(value, "description")));
    }

    @Override public String toString() { return "<" + name + ">"; }
}
