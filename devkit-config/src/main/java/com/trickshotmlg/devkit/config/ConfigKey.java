package com.trickshotmlg.devkit.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Immutable, strongly typed declaration for one configuration value. */
public final class ConfigKey<T> {
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]*");
    private final String path;
    private final ConfigCodec<T> codec;
    private final Optional<T> defaultValue;
    private final List<ConfigValidator<T>> validators;
    private final Optional<String> description;
    private final boolean sensitive;

    private ConfigKey(Builder<T> builder) {
        path = validatePath(builder.path);
        codec = Objects.requireNonNull(builder.codec, "codec");
        validators = List.copyOf(builder.validators);
        description = Optional.ofNullable(builder.description);
        sensitive = builder.sensitive;
        defaultValue = Optional.ofNullable(builder.defaultValue).map(value -> ConfigCodecs.freeze(codec.decode(codec.encode(value))));
        defaultValue.ifPresent(value -> {
            List<String> failures = validate(value);
            if (!failures.isEmpty()) throw new IllegalArgumentException("Invalid default for '" + path + "': " + String.join("; ", failures));
        });
    }

    public static Builder<String> string(String path, String defaultValue) { return builder(path, ConfigCodecs.STRING).defaultValue(defaultValue); }
    public static Builder<String> string(String path) { return builder(path, ConfigCodecs.STRING); }
    public static Builder<Boolean> bool(String path, boolean defaultValue) { return builder(path, ConfigCodecs.BOOLEAN).defaultValue(defaultValue); }
    public static Builder<Boolean> bool(String path) { return builder(path, ConfigCodecs.BOOLEAN); }
    public static Builder<Integer> integer(String path, int defaultValue) { return builder(path, ConfigCodecs.INTEGER).defaultValue(defaultValue); }
    public static Builder<Integer> integer(String path) { return builder(path, ConfigCodecs.INTEGER); }
    public static Builder<Long> longInteger(String path, long defaultValue) { return builder(path, ConfigCodecs.LONG).defaultValue(defaultValue); }
    public static Builder<Long> longInteger(String path) { return builder(path, ConfigCodecs.LONG); }
    public static Builder<Double> decimal(String path, double defaultValue) { return builder(path, ConfigCodecs.DOUBLE).defaultValue(defaultValue); }
    public static Builder<Double> decimal(String path) { return builder(path, ConfigCodecs.DOUBLE); }
    public static Builder<Float> floatNumber(String path, float defaultValue) { return builder(path, ConfigCodecs.FLOAT).defaultValue(defaultValue); }
    public static Builder<Float> floatNumber(String path) { return builder(path, ConfigCodecs.FLOAT); }
    public static Builder<java.util.UUID> uuid(String path, java.util.UUID defaultValue) { return builder(path, ConfigCodecs.UUID_CODEC).defaultValue(defaultValue); }
    public static Builder<java.util.UUID> uuid(String path) { return builder(path, ConfigCodecs.UUID_CODEC); }
    public static Builder<java.time.Duration> duration(String path, java.time.Duration defaultValue) { return builder(path, ConfigCodecs.DURATION).defaultValue(defaultValue); }
    public static Builder<java.time.Duration> duration(String path) { return builder(path, ConfigCodecs.DURATION); }
    public static <E extends Enum<E>> Builder<E> enumeration(String path, Class<E> enumType, E defaultValue) { return builder(path, ConfigCodecs.enumCodec(enumType)).defaultValue(defaultValue); }
    public static <E extends Enum<E>> Builder<E> enumeration(String path, Class<E> enumType) { return builder(path, ConfigCodecs.enumCodec(enumType)); }
    public static <T> Builder<T> builder(String path, ConfigCodec<T> codec) { return new Builder<>(path, codec); }

    public String path() { return path; }
    public ConfigCodec<T> codec() { return codec; }
    public Optional<T> defaultValue() { return defaultValue; }
    public Optional<String> description() { return description; }
    public boolean sensitive() { return sensitive; }

    /** Returns all validation messages for an already decoded value. */
    public List<String> validate(T value) {
        ArrayList<String> failures = new ArrayList<>();
        for (ConfigValidator<T> validator : validators) {
            try { validator.validate(value).ifPresent(failures::add); }
            catch (RuntimeException exception) { failures.add("validator failed: " + exception.getMessage()); }
        }
        return List.copyOf(failures);
    }

    @Override public String toString() { return "ConfigKey[" + path + ": " + codec.description() + "]"; }

    static String validatePath(String path) {
        Objects.requireNonNull(path, "path");
        if (path.isBlank() || path.startsWith(".") || path.endsWith(".") || path.equals("config-version")) {
            throw new IllegalArgumentException("Invalid or reserved configuration path: '" + path + "'");
        }
        for (String segment : path.split("\\.", -1)) if (!SEGMENT.matcher(segment).matches()) {
            throw new IllegalArgumentException("Invalid configuration path '" + path + "': segment '" + segment + "' must match " + SEGMENT.pattern());
        }
        return path;
    }

    /** Fluent key builder. Every call returns the eventual immutable key. */
    public static final class Builder<T> {
        private final String path;
        private final ConfigCodec<T> codec;
        private T defaultValue;
        private boolean hasDefault;
        private final List<ConfigValidator<T>> validators = new ArrayList<>();
        private String description;
        private boolean sensitive;
        private Builder(String path, ConfigCodec<T> codec) { this.path = path; this.codec = Objects.requireNonNull(codec, "codec"); }
        public Builder<T> defaultValue(T value) { defaultValue = Objects.requireNonNull(value, "defaultValue"); hasDefault = true; return this; }
        public Builder<T> validate(ConfigValidator<T> validator) { validators.add(Objects.requireNonNull(validator, "validator")); return this; }
        public Builder<T> description(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("description must not be blank"); description = value; return this; }
        public Builder<T> sensitive() { sensitive = true; return this; }
        public Builder<T> range(double minimum, double maximum) {
            if (minimum > maximum) throw new IllegalArgumentException("minimum must not exceed maximum");
            return validate(value -> {
                if (!(value instanceof Number number)) return Optional.of("range validation requires a numeric codec");
                double n = number.doubleValue();
                return !Double.isFinite(n) || n < minimum || n > maximum
                        ? Optional.of("must be between " + minimum + " and " + maximum) : Optional.empty();
            });
        }
        public Builder<T> minimum(double minimum) { return validate(value -> value instanceof Number n && n.doubleValue() < minimum ? Optional.of("must be at least " + minimum) : Optional.empty()); }
        public Builder<T> maximum(double maximum) { return validate(value -> value instanceof Number n && n.doubleValue() > maximum ? Optional.of("must be at most " + maximum) : Optional.empty()); }
        public Builder<T> notBlank() { return validate(value -> value instanceof String text && text.isBlank() ? Optional.of("must not be blank") : Optional.empty()); }
        public Builder<T> length(int minimum, int maximum) {
            if (minimum < 0 || maximum < minimum) throw new IllegalArgumentException("invalid length bounds");
            return validate(value -> value instanceof String text && (text.length() < minimum || text.length() > maximum)
                    ? Optional.of("length must be between " + minimum + " and " + maximum) : Optional.empty());
        }
        public Builder<T> matches(String regex) {
            Pattern pattern = Pattern.compile(Objects.requireNonNull(regex, "regex"));
            return validate(value -> value instanceof String text && !pattern.matcher(text).matches()
                    ? Optional.of("must match pattern " + regex) : Optional.empty());
        }
        public Builder<T> oneOf(java.util.Collection<? extends T> values) {
            List<? extends T> copy = List.copyOf(values);
            if (copy.isEmpty()) throw new IllegalArgumentException("allowed values must not be empty");
            return validate(value -> copy.contains(value) ? Optional.empty() : Optional.of("must be one of " + copy));
        }
        public ConfigKey<T> build() { return new ConfigKey<>(this); }
    }
}
