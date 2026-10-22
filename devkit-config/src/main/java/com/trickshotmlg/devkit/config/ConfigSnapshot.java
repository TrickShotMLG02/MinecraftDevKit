package com.trickshotmlg.devkit.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable coherent view of one successfully validated configuration generation. */
public final class ConfigSnapshot implements ConfigValues {
    private final ConfigSchema schema;
    private final Map<String, Object> values;
    private final long generation;

    ConfigSnapshot(ConfigSchema schema, Map<String, Object> values, long generation) {
        this.schema = schema;
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        values.forEach((path, value) -> copy.put(path, ConfigCodecs.freeze(value)));
        this.values = Collections.unmodifiableMap(copy);
        this.generation = generation;
    }
    @Override public <T> T get(ConfigKey<T> key) {
        Objects.requireNonNull(key, "key");
        ConfigKey<?> registered = schema.key(key.path()).orElseThrow(() -> new IllegalArgumentException("Key is not in this schema: " + key.path()));
        if (registered != key) throw new IllegalArgumentException("A different key declaration uses path '" + key.path() + "'");
        @SuppressWarnings("unchecked") T value = (T) values.get(key.path());
        if (value == null) throw new IllegalStateException("Required configuration value is unavailable: " + key.path());
        return value;
    }
    /** Returns the immutable values map using canonical dotted paths. */
    public Map<String, Object> values() { return values; }
    public long generation() { return generation; }
    public ConfigSchema schema() { return schema; }
}
